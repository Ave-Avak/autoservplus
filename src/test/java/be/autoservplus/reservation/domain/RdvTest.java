package be.autoservplus.reservation.domain;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests de l entite Rdv : invariants de construction, calcul de la fin, machine a etats
 * et delai d annulation. Aucune dependance Spring : l entite se teste seule.
 */
@DisplayName("Rdv")
class RdvTest {

    private static final Instant DEBUT = Instant.parse("2026-09-15T08:00:00Z");
    private static final Duration PAS = Duration.ofMinutes(30);
    private static final Duration DELAI_24H = Duration.ofHours(24);

    private final Utilisateur marie = new Utilisateur("marie@exemple.be", "$2a$12$h", "Dupont", "Marie", TypeUtilisateur.MEMBRE);
    private final Utilisateur paul = new Utilisateur("paul@exemple.be", "$2a$12$h", "Martin", "Paul", TypeUtilisateur.MEMBRE);
    private final Vehicule golf = new Vehicule(marie, "1-ABC-123", "Volkswagen", "Golf", Motorisation.DIESEL);
    private final PosteAtelier pont = new PosteAtelier("Pont 1");
    private final Categorie entretien = new Categorie("ENTRETIEN", "Entretien", TypeCategorie.SERVICE);
    private final Prestation vidange = new Prestation(entretien, "VID", "Vidange", new BigDecimal("49.00"), 30);
    private final Prestation freins = new Prestation(entretien, "FRE", "Plaquettes", new BigDecimal("89.00"), 50);

    private Rdv rdvDe(Prestation... prestations) {
        return new Rdv("RDV-2026-0001", marie, golf, pont, DEBUT, PAS, List.of(prestations), null);
    }

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("demarre en attente, avec reference et lignes")
        void demarreEnAttente() {
            Rdv rdv = rdvDe(vidange);

            assertThat(rdv.getStatut()).isEqualTo(StatutRdv.EN_ATTENTE);
            assertThat(rdv.getReference()).isNotNull();
            assertThat(rdv.getLignes()).hasSize(1);
            assertThat(rdv.getDebut()).isEqualTo(DEBUT);
        }

        @Test
        @DisplayName("refuse un vehicule qui n appartient pas au membre (RM-06)")
        void refuseLeVehiculeDAutrui() {
            assertThatThrownBy(() -> new Rdv("RDV-2026-0001", paul, golf, pont, DEBUT, PAS, List.of(vidange), null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("RM-06");
        }

        @Test
        @DisplayName("refuse un rendez-vous sans prestation")
        void refuseSansPrestation() {
            assertThatThrownBy(() -> new Rdv("RDV-2026-0001", marie, golf, pont, DEBUT, PAS, List.of(), null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("ne cree qu une ligne par prestation distincte")
        void dedoublonneLesPrestations() {
            Rdv rdv = new Rdv("RDV-2026-0001", marie, golf, pont, DEBUT, PAS,
                    List.of(vidange, vidange), null);

            assertThat(rdv.getLignes()).hasSize(1);
        }

        @Test
        @DisplayName("fige le prix du catalogue dans la ligne")
        void figeLePrix() {
            Rdv rdv = rdvDe(vidange);
            vidange.modifierPrix(new BigDecimal("59.00"));

            assertThat(rdv.montantHtva()).isEqualByComparingTo("49.00");
        }

        @Test
        @DisplayName("normalise un commentaire vide en null")
        void normaliseLeCommentaire() {
            Rdv rdv = new Rdv("RDV-2026-0001", marie, golf, pont, DEBUT, PAS, List.of(vidange), "   ");
            assertThat(rdv.getCommentaire()).isNull();
        }
    }

    @Nested
    @DisplayName("duree et fin")
    class Duree {

        @Test
        @DisplayName("30 minutes sur un pas de 30 donnent une fin a +30")
        void dureeExacte() {
            Rdv rdv = rdvDe(vidange);
            assertThat(rdv.getFin()).isEqualTo(DEBUT.plus(Duration.ofMinutes(30)));
        }

        @Test
        @DisplayName("80 minutes sur un pas de 30 sont arrondies a 90")
        void arrondiAuPasSuperieur() {
            Rdv rdv = rdvDe(vidange, freins);
            assertThat(rdv.dureeEstimeeMinutes()).isEqualTo(80);
            assertThat(rdv.duree()).isEqualTo(Duration.ofMinutes(90));
        }

        @ParameterizedTest(name = "{0} min, pas {1} -> {2} min")
        @CsvSource({
                "30, 30, 30",
                "31, 30, 60",
                "50, 30, 60",
                "60, 30, 60",
                "1, 15, 15",
                "0, 30, 30",
                "125, 60, 180"
        })
        @DisplayName("arrondit toujours au multiple superieur, jamais a zero")
        void arrondi(int minutes, int pas, int attendu) {
            assertThat(Rdv.dureeArrondie(minutes, Duration.ofMinutes(pas)))
                    .isEqualTo(Duration.ofMinutes(attendu));
        }
    }

    @Nested
    @DisplayName("transitions (RM-10)")
    class Transitions {

        @Test
        @DisplayName("EN_ATTENTE -> CONFIRME")
        void confirme() {
            Rdv rdv = rdvDe(vidange);
            rdv.confirmer();
            assertThat(rdv.getStatut()).isEqualTo(StatutRdv.CONFIRME);
        }

        @Test
        @DisplayName("CONFIRME -> HONORE")
        void honore() {
            Rdv rdv = rdvDe(vidange);
            rdv.confirmer();
            rdv.marquerHonore();
            assertThat(rdv.getStatut()).isEqualTo(StatutRdv.HONORE);
        }

        @Test
        @DisplayName("CONFIRME -> ABSENT")
        void absent() {
            Rdv rdv = rdvDe(vidange);
            rdv.confirmer();
            rdv.marquerAbsent();
            assertThat(rdv.getStatut()).isEqualTo(StatutRdv.ABSENT);
        }

        @Test
        @DisplayName("refuse d honorer un rendez-vous non confirme")
        void refuseHonorerSansConfirmation() {
            Rdv rdv = rdvDe(vidange);
            assertThatThrownBy(rdv::marquerHonore)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("RM-10");
        }

        @Test
        @DisplayName("refuse de confirmer deux fois")
        void refuseDoubleConfirmation() {
            Rdv rdv = rdvDe(vidange);
            rdv.confirmer();
            assertThatThrownBy(rdv::confirmer).isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("un etat final est definitif")
        void etatFinalDefinitif() {
            Rdv rdv = rdvDe(vidange);
            rdv.refuser("Piece indisponible", DEBUT.minus(Duration.ofDays(2)));

            assertThatThrownBy(rdv::confirmer).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> rdv.annulerParLeMembre(DEBUT.minus(Duration.ofDays(3)), DELAI_24H))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("refus par le garage")
    class Refus {

        @Test
        @DisplayName("enregistre le motif et la date")
        void enregistreLeMotif() {
            Rdv rdv = rdvDe(vidange);
            Instant maintenant = DEBUT.minus(Duration.ofDays(2));

            rdv.refuser("  Piece indisponible ", maintenant);

            assertThat(rdv.getStatut()).isEqualTo(StatutRdv.REFUSE);
            assertThat(rdv.getMotifRefus()).isEqualTo("Piece indisponible");
            assertThat(rdv.getDateAnnulation()).isEqualTo(maintenant);
        }

        @Test
        @DisplayName("exige un motif")
        void exigeUnMotif() {
            Rdv rdv = rdvDe(vidange);
            assertThatThrownBy(() -> rdv.refuser(" ", DEBUT))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(rdv.getStatut()).isEqualTo(StatutRdv.EN_ATTENTE);
        }

        @Test
        @DisplayName("reste possible apres confirmation")
        void possibleApresConfirmation() {
            Rdv rdv = rdvDe(vidange);
            rdv.confirmer();
            assertThatThrownBy(() -> rdv.refuser("x", DEBUT)).isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("annulation par le garage")
    class AnnulationGarage {

        @Test
        @DisplayName("EN_ATTENTE devient ANNULE avec motif trim et date")
        void depuisEnAttente() {
            Rdv rdv = rdvDe(vidange);
            Instant maintenant = DEBUT.minus(Duration.ofDays(2));

            rdv.annulerParLeGarage("  Poste indisponible ", maintenant);

            assertThat(rdv.getStatut()).isEqualTo(StatutRdv.ANNULE);
            assertThat(rdv.getMotifRefus()).isEqualTo("Poste indisponible");
            assertThat(rdv.getDateAnnulation()).isEqualTo(maintenant);
        }

        @Test
        @DisplayName("CONFIRME devient ANNULE avec motif trim et date")
        void depuisConfirme() {
            Rdv rdv = rdvDe(vidange);
            rdv.confirmer();
            Instant maintenant = DEBUT.minus(Duration.ofHours(6));

            rdv.annulerParLeGarage(" Panne du pont ", maintenant);

            assertThat(rdv.getStatut()).isEqualTo(StatutRdv.ANNULE);
            assertThat(rdv.getMotifRefus()).isEqualTo("Panne du pont");
            assertThat(rdv.getDateAnnulation()).isEqualTo(maintenant);
        }

        @Test
        @DisplayName("refuse un motif blank")
        void refuseMotifBlank() {
            Rdv rdv = rdvDe(vidange);
            assertThatThrownBy(() -> rdv.annulerParLeGarage("   ", DEBUT))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(rdv.getStatut()).isEqualTo(StatutRdv.EN_ATTENTE);
        }

        @Test
        @DisplayName("refuse un motif null")
        void refuseMotifNull() {
            Rdv rdv = rdvDe(vidange);
            assertThatThrownBy(() -> rdv.annulerParLeGarage(null, DEBUT))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(rdv.getStatut()).isEqualTo(StatutRdv.EN_ATTENTE);
        }

        @Test
        @DisplayName("refuse depuis un etat final (RM-10)")
        void refuseDepuisEtatFinal() {
            Instant maintenant = DEBUT.minus(Duration.ofDays(2));

            Rdv depuisRefuse = rdvDe(vidange);
            depuisRefuse.refuser("motif", maintenant);
            assertThatThrownBy(() -> depuisRefuse.annulerParLeGarage("motif", maintenant))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("RM-10");

            Rdv depuisAnnule = rdvDe(vidange);
            depuisAnnule.annulerParLeGarage("motif", maintenant);
            assertThatThrownBy(() -> depuisAnnule.annulerParLeGarage("autre motif", maintenant))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("RM-10");

            Rdv depuisHonore = rdvDe(vidange);
            depuisHonore.confirmer();
            depuisHonore.marquerHonore();
            assertThatThrownBy(() -> depuisHonore.annulerParLeGarage("motif", maintenant))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("RM-10");

            Rdv depuisAbsent = rdvDe(vidange);
            depuisAbsent.confirmer();
            depuisAbsent.marquerAbsent();
            assertThatThrownBy(() -> depuisAbsent.annulerParLeGarage("motif", maintenant))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("RM-10");
        }
    }

    @Nested
    @DisplayName("annulation par le membre (RM-11)")
    class Annulation {

        @Test
        @DisplayName("acceptee 25 heures avant")
        void accepteeAvantLeDelai() {
            Rdv rdv = rdvDe(vidange);
            Instant maintenant = DEBUT.minus(Duration.ofHours(25));

            rdv.annulerParLeMembre(maintenant, DELAI_24H);

            assertThat(rdv.getStatut()).isEqualTo(StatutRdv.ANNULE);
            assertThat(rdv.getDateAnnulation()).isEqualTo(maintenant);
        }

        @Test
        @DisplayName("acceptee exactement 24 heures avant")
        void accepteeALaLimite() {
            Rdv rdv = rdvDe(vidange);
            rdv.annulerParLeMembre(DEBUT.minus(DELAI_24H), DELAI_24H);
            assertThat(rdv.getStatut()).isEqualTo(StatutRdv.ANNULE);
        }

        @Test
        @DisplayName("refusee 23 heures avant")
        void refuseeDansLeDelai() {
            Rdv rdv = rdvDe(vidange);
            assertThatThrownBy(() -> rdv.annulerParLeMembre(DEBUT.minus(Duration.ofHours(23)), DELAI_24H))
                    .isInstanceOf(IllegalStateException.class)
                    // Le code RM-11 n'est plus dans la phrase : elle est affichee telle
                    // quelle au membre. Il est porte par le RegleMetierException que
                    // RdvService pose autour (voir RdvServiceIT).
                    .hasMessageContaining("n est plus possible");
            assertThat(rdv.getStatut()).isEqualTo(StatutRdv.EN_ATTENTE);
        }

        @Test
        @DisplayName("le delai est celui de l atelier, pas une constante")
        void delaiParametrable() {
            Rdv rdv = rdvDe(vidange);
            Instant deuxHeuresAvant = DEBUT.minus(Duration.ofHours(2));

            assertThat(rdv.peutEtreAnnuleParLeMembre(deuxHeuresAvant, DELAI_24H)).isFalse();
            assertThat(rdv.peutEtreAnnuleParLeMembre(deuxHeuresAvant, Duration.ofHours(1))).isTrue();
        }

        @Test
        @DisplayName("possible aussi apres confirmation")
        void possibleApresConfirmation() {
            Rdv rdv = rdvDe(vidange);
            rdv.confirmer();
            rdv.annulerParLeMembre(DEBUT.minus(Duration.ofDays(1)), DELAI_24H);
            assertThat(rdv.getStatut()).isEqualTo(StatutRdv.ANNULE);
        }
    }

    @Nested
    @DisplayName("montants")
    class Montants {

        @Test
        @DisplayName("additionne les lignes HTVA et TVAC")
        void additionneLesLignes() {
            Rdv rdv = rdvDe(vidange, freins);

            assertThat(rdv.montantHtva()).isEqualByComparingTo("138.00");
            assertThat(rdv.montantTvac()).isEqualByComparingTo("166.98");
        }
    }

    @Test
    @DisplayName("appartientA compare sur l adresse")
    void appartientA() {
        Rdv rdv = rdvDe(vidange);
        assertThat(rdv.appartientA(marie)).isTrue();
        assertThat(rdv.appartientA(paul)).isFalse();
        assertThat(rdv.appartientA(null)).isFalse();
    }
}