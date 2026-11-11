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
    /** Instant de confirmation des tests qui ne portent pas sur la garde du début. */
    private static final Instant AVANT_LE_DEBUT = DEBUT.minus(Duration.ofDays(3));
    /** Instant de clôture des tests qui ne portent pas sur les gardes de temps. */
    private static final Instant APRES_LA_FIN = DEBUT.plus(Duration.ofDays(1));
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
            rdv.confirmer(AVANT_LE_DEBUT);
            assertThat(rdv.getStatut()).isEqualTo(StatutRdv.CONFIRME);
        }

        @Test
        @DisplayName("CONFIRME -> HONORE")
        void honore() {
            Rdv rdv = rdvDe(vidange);
            rdv.confirmer(AVANT_LE_DEBUT);
            rdv.marquerHonore(APRES_LA_FIN);
            assertThat(rdv.getStatut()).isEqualTo(StatutRdv.HONORE);
        }

        @Test
        @DisplayName("CONFIRME -> ABSENT")
        void absent() {
            Rdv rdv = rdvDe(vidange);
            rdv.confirmer(AVANT_LE_DEBUT);
            rdv.marquerAbsent(APRES_LA_FIN);
            assertThat(rdv.getStatut()).isEqualTo(StatutRdv.ABSENT);
        }

        @Test
        @DisplayName("refuse d honorer un rendez-vous non confirme")
        void refuseHonorerSansConfirmation() {
            Rdv rdv = rdvDe(vidange);
            assertThatThrownBy(() -> rdv.marquerHonore(APRES_LA_FIN))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("RM-10");
        }

        @Test
        @DisplayName("refuse de confirmer deux fois")
        void refuseDoubleConfirmation() {
            Rdv rdv = rdvDe(vidange);
            rdv.confirmer(AVANT_LE_DEBUT);
            assertThatThrownBy(() -> rdv.confirmer(AVANT_LE_DEBUT)).isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("un etat final est definitif")
        void etatFinalDefinitif() {
            Rdv rdv = rdvDe(vidange);
            rdv.refuser("Piece indisponible", DEBUT.minus(Duration.ofDays(2)));

            assertThatThrownBy(() -> rdv.confirmer(AVANT_LE_DEBUT)).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> rdv.annulerParLeMembre(DEBUT.minus(Duration.ofDays(3)), DELAI_24H))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("garde du début : confirmation et annulation par le garage")
    class GardeDuDebut {

        @Test
        @DisplayName("confirmation acceptée une seconde avant le début")
        void confirmationAvantLeDebut() {
            Rdv rdv = rdvDe(vidange);
            assertThat(rdv.peutEtreConfirme(DEBUT.minusSeconds(1))).isTrue();
            rdv.confirmer(DEBUT.minusSeconds(1));
            assertThat(rdv.getStatut()).isEqualTo(StatutRdv.CONFIRME);
        }

        @Test
        @DisplayName("confirmation refusée à l'heure du début et après, le refus reste possible")
        void confirmationRefuseeDesLeDebut() {
            Rdv rdv = rdvDe(vidange);
            assertThat(rdv.peutEtreConfirme(DEBUT)).isFalse();
            assertThatThrownBy(() -> rdv.confirmer(DEBUT))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("seulement refusé");
            assertThatThrownBy(() -> rdv.confirmer(DEBUT.plus(Duration.ofHours(2))))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(rdv.getStatut()).isEqualTo(StatutRdv.EN_ATTENTE);

            rdv.refuser("Demande arrivée trop tard", DEBUT.plus(Duration.ofHours(2)));
            assertThat(rdv.getStatut()).isEqualTo(StatutRdv.REFUSE);
        }

        @Test
        @DisplayName("annulation d'un rendez-vous confirmé refusée dès le début, honoré et absent restent possibles")
        void annulationConfirmeRefuseeDesLeDebut() {
            Rdv rdv = rdvDe(vidange);
            rdv.confirmer(AVANT_LE_DEBUT);
            assertThat(rdv.peutEtreAnnuleParLeGarage(DEBUT.minusSeconds(1))).isTrue();
            assertThat(rdv.peutEtreAnnuleParLeGarage(DEBUT)).isFalse();

            assertThatThrownBy(() -> rdv.annulerParLeGarage("Panne du pont", DEBUT))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("honoré ou absent");
            assertThat(rdv.getStatut()).isEqualTo(StatutRdv.CONFIRME);
            assertThat(rdv.getMotifRefus()).isNull();

            rdv.marquerHonore(APRES_LA_FIN);
            assertThat(rdv.getStatut()).isEqualTo(StatutRdv.HONORE);
        }

        @Test
        @DisplayName("annulation d'une demande en attente refusée après le début")
        void annulationEnAttenteRefuseeApresLeDebut() {
            Rdv rdv = rdvDe(vidange);
            assertThat(rdv.peutEtreAnnuleParLeGarage(DEBUT.plusSeconds(1))).isFalse();
            assertThatThrownBy(() -> rdv.annulerParLeGarage("Atelier fermé", DEBUT.plusSeconds(1)))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(rdv.getStatut()).isEqualTo(StatutRdv.EN_ATTENTE);
        }

        @Test
        @DisplayName("le motif reste contrôlé en premier")
        void motifAvantLaGarde() {
            Rdv rdv = rdvDe(vidange);
            assertThatThrownBy(() -> rdv.annulerParLeGarage(" ", DEBUT.plusSeconds(1)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("un état final n'est ni confirmable ni annulable, même avant le début")
        void etatFinal() {
            Rdv rdv = rdvDe(vidange);
            rdv.refuser("motif", AVANT_LE_DEBUT);
            assertThat(rdv.peutEtreConfirme(AVANT_LE_DEBUT)).isFalse();
            assertThat(rdv.peutEtreAnnuleParLeGarage(AVANT_LE_DEBUT)).isFalse();
        }
    }

    @Nested
    @DisplayName("gardes de temps : honoré et absent")
    class GardesDeCloture {

        private Rdv confirme() {
            Rdv rdv = rdvDe(vidange);
            rdv.confirmer(AVANT_LE_DEBUT);
            return rdv;
        }

        @Test
        @DisplayName("honoré refusé une seconde avant le début, accepté à l'heure du début")
        void honoreDesLeDebut() {
            Rdv rdv = confirme();
            assertThat(rdv.peutEtreMarqueHonore(DEBUT.minusSeconds(1))).isFalse();
            assertThatThrownBy(() -> rdv.marquerHonore(DEBUT.minusSeconds(1)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("avant l'heure de début");
            assertThat(rdv.getStatut()).isEqualTo(StatutRdv.CONFIRME);

            assertThat(rdv.peutEtreMarqueHonore(DEBUT)).isTrue();
            rdv.marquerHonore(DEBUT);
            assertThat(rdv.getStatut()).isEqualTo(StatutRdv.HONORE);
        }

        @Test
        @DisplayName("absent refusé jusqu'à la fin du créneau, accepté juste après")
        void absentApresLaFin() {
            Rdv rdv = confirme();
            assertThat(rdv.peutEtreMarqueAbsent(rdv.getFin())).isFalse();
            assertThatThrownBy(() -> rdv.marquerAbsent(rdv.getFin()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("avant la fin");
            assertThat(rdv.getStatut()).isEqualTo(StatutRdv.CONFIRME);

            assertThat(rdv.peutEtreMarqueAbsent(rdv.getFin().plusSeconds(1))).isTrue();
            rdv.marquerAbsent(rdv.getFin().plusSeconds(1));
            assertThat(rdv.getStatut()).isEqualTo(StatutRdv.ABSENT);
        }

        @Test
        @DisplayName("une demande en attente n'est ni honorée ni absente, même après la fin")
        void enAttente() {
            Rdv rdv = rdvDe(vidange);
            assertThat(rdv.peutEtreMarqueHonore(APRES_LA_FIN)).isFalse();
            assertThat(rdv.peutEtreMarqueAbsent(APRES_LA_FIN)).isFalse();
        }
    }

    @Nested
    @DisplayName("confirmation dans la fenêtre du rappel")
    class ConfirmationEtRappel {

        private final Duration rappel = Duration.ofHours(24);

        @Test
        @DisplayName("confirmé moins de 24 h avant le début : la confirmation tient lieu de rappel")
        void dansLaFenetre() {
            Rdv rdv = rdvDe(vidange);
            Instant maintenant = DEBUT.minus(Duration.ofHours(10));
            rdv.confirmer(maintenant);

            assertThat(rdv.confirmationTientLieuDeRappel(maintenant, rappel)).isTrue();
            assertThat(rdv.confirmationTientLieuDeRappel(DEBUT.minus(rappel), rappel)).as("à la limite").isTrue();
        }

        @Test
        @DisplayName("confirmé plus tôt, ou délai de rappel nul, ou demande en attente : rien")
        void horsFenetre() {
            Rdv rdv = rdvDe(vidange);
            assertThat(rdv.confirmationTientLieuDeRappel(DEBUT.minus(Duration.ofHours(10)), rappel))
                    .as("en attente").isFalse();

            rdv.confirmer(AVANT_LE_DEBUT);
            assertThat(rdv.confirmationTientLieuDeRappel(DEBUT.minus(rappel).minusSeconds(1), rappel)).isFalse();
            assertThat(rdv.confirmationTientLieuDeRappel(DEBUT.minus(Duration.ofHours(10)), Duration.ZERO)).isFalse();
        }
    }

    @Nested
    @DisplayName("échéance de réponse et expiration")
    class Expiration {

        private final Instant echeance = DEBUT.minus(Duration.ofHours(20));

        @Test
        @DisplayName("une demande expire à son échéance, pas avant, et libère le créneau")
        void expireALEcheance() {
            Rdv rdv = rdvDe(vidange);
            rdv.fixerEcheanceReponse(echeance);

            assertThat(rdv.peutExpirer(echeance.minusSeconds(1))).isFalse();
            assertThatThrownBy(() -> rdv.expirer(echeance.minusSeconds(1))).isInstanceOf(IllegalStateException.class);

            rdv.expirer(echeance);
            assertThat(rdv.getStatut()).isEqualTo(StatutRdv.EXPIRE);
            assertThat(rdv.getStatut().libereLeCreneau()).isTrue();
            assertThat(rdv.getStatut().estEnCours()).isFalse();
            assertThat(rdv.getDateAnnulation()).as("personne n'a annulé").isNull();
            assertThat(rdv.getEcheanceReponse()).isEqualTo(echeance);
        }

        @Test
        @DisplayName("sans échéance, une demande n'expire jamais")
        void sansEcheance() {
            Rdv rdv = rdvDe(vidange);
            assertThat(rdv.peutExpirer(APRES_LA_FIN)).isFalse();
            assertThatThrownBy(() -> rdv.expirer(APRES_LA_FIN)).isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("un rendez-vous confirmé n'expire jamais, même passé")
        void confirmeNExpireJamais() {
            Rdv rdv = rdvDe(vidange);
            rdv.fixerEcheanceReponse(echeance);
            rdv.confirmer(AVANT_LE_DEBUT);

            assertThat(rdv.peutExpirer(APRES_LA_FIN)).isFalse();
            assertThatThrownBy(() -> rdv.expirer(APRES_LA_FIN)).isInstanceOf(IllegalStateException.class);
            assertThat(rdv.getStatut()).isEqualTo(StatutRdv.CONFIRME);
        }

        @Test
        @DisplayName("l'échéance se fixe une seule fois, et seulement sur une demande en attente")
        void echeanceFigee() {
            Rdv rdv = rdvDe(vidange);
            rdv.fixerEcheanceReponse(echeance);
            assertThatThrownBy(() -> rdv.fixerEcheanceReponse(echeance.plusSeconds(60)))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(rdv.getEcheanceReponse()).isEqualTo(echeance);

            Rdv confirme = rdvDe(vidange);
            confirme.confirmer(AVANT_LE_DEBUT);
            assertThatThrownBy(() -> confirme.fixerEcheanceReponse(echeance))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("une demande expirée ne se confirme plus, ne se refuse plus, ne s'annule plus")
        void etatFinal() {
            Rdv rdv = rdvDe(vidange);
            rdv.fixerEcheanceReponse(echeance);
            rdv.expirer(echeance);

            assertThat(rdv.peutEtreConfirme(echeance)).isFalse();
            assertThatThrownBy(() -> rdv.refuser("trop tard", echeance)).isInstanceOf(IllegalStateException.class);
            assertThat(rdv.peutEtreAnnuleParLeGarage(echeance)).isFalse();
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
            rdv.confirmer(AVANT_LE_DEBUT);
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
            rdv.confirmer(AVANT_LE_DEBUT);
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
            depuisHonore.confirmer(AVANT_LE_DEBUT);
            depuisHonore.marquerHonore(APRES_LA_FIN);
            assertThatThrownBy(() -> depuisHonore.annulerParLeGarage("motif", maintenant))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("RM-10");

            Rdv depuisAbsent = rdvDe(vidange);
            depuisAbsent.confirmer(AVANT_LE_DEBUT);
            depuisAbsent.marquerAbsent(APRES_LA_FIN);
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
            rdv.confirmer(AVANT_LE_DEBUT);
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