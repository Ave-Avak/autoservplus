package be.autoservplus.intervention.domain;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.reservation.domain.Motorisation;
import be.autoservplus.reservation.domain.PosteAtelier;
import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.domain.Vehicule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests de l entite Intervention : construction depuis un RDV (pre-remplissage
 * des lignes de main d oeuvre), machine a etats, gestion des lignes et calculs.
 * Aucune dependance Spring, le domaine est testable seul.
 */
@DisplayName("Intervention")
class InterventionTest {

    private static final Instant DEBUT = Instant.parse("2026-09-14T08:00:00Z");
    private static final Duration PAS = Duration.ofMinutes(30);

    private final Utilisateur marie = new Utilisateur("marie@exemple.be", "$2a$12$h", "Dupont", "Marie", TypeUtilisateur.MEMBRE);
    private final Vehicule golf = new Vehicule(marie, "1-ABC-123", "VW", "Golf", Motorisation.DIESEL);
    private final PosteAtelier pont = new PosteAtelier("Pont 1");
    private final Categorie entretien = new Categorie("ENT", "Entretien", TypeCategorie.SERVICE);
    private final Prestation vidange = new Prestation(entretien, "VID", "Vidange", new BigDecimal("49.00"), 30);
    private final Prestation freins = new Prestation(entretien, "FRE", "Plaquettes", new BigDecimal("89.00"), 60);

    private Rdv rdvAvec(Prestation... prestations) {
        return new Rdv("RDV-2026-0001", marie, golf, pont, DEBUT, PAS, List.of(prestations), null);
    }

    private Intervention interventionDepuis(Prestation... prestations) {
        return new Intervention("INT-2026-0001", rdvAvec(prestations));
    }

    @Nested
    @DisplayName("construction depuis un RDV")
    class Construction {

        @Test
        @DisplayName("statut PLANIFIEE, reference et vehicule recopies")
        void demarreEnPlanifiee() {
            Intervention it = interventionDepuis(vidange);

            assertThat(it.getStatut()).isEqualTo(StatutIntervention.PLANIFIEE);
            assertThat(it.getReference()).isNotNull();
            assertThat(it.getNumero()).isEqualTo("INT-2026-0001");
            assertThat(it.getVehicule()).isEqualTo(golf);
            assertThat(it.getRdv().getReference()).isNotNull();
        }

        @Test
        @DisplayName("une ligne de main d oeuvre par prestation, libelle et prix figes")
        void preRemplitLesLignes() {
            Intervention it = interventionDepuis(vidange, freins);

            assertThat(it.getLignes()).hasSize(2);
            assertThat(it.getLignes()).allMatch(l -> l.getType() == TypeLigneIntervention.MAIN_OEUVRE);
            assertThat(it.getLignes().get(0).getLibelleFige()).isEqualTo("Vidange");
            assertThat(it.getLignes().get(0).getPrixUnitaireHtva()).isEqualByComparingTo("49.00");
        }

        @Test
        @DisplayName("figer le prix : une modification ulterieure du catalogue n affecte pas la ligne")
        void figeLePrix() {
            Intervention it = interventionDepuis(vidange);
            vidange.modifierPrix(new BigDecimal("59.00"));

            assertThat(it.getLignes().get(0).getPrixUnitaireHtva()).isEqualByComparingTo("49.00");
        }
    }

    @Nested
    @DisplayName("transitions autorisees")
    class Transitions {

        @Test
        @DisplayName("PLANIFIEE -> EN_COURS enregistre le debut reel")
        void demarrer() {
            Intervention it = interventionDepuis(vidange);
            Instant maintenant = DEBUT.plus(Duration.ofHours(1));

            it.demarrer(maintenant);

            assertThat(it.getStatut()).isEqualTo(StatutIntervention.EN_COURS);
            assertThat(it.getDebutReel()).isEqualTo(maintenant);
        }

        @Test
        @DisplayName("EN_COURS -> EN_PAUSE ne modifie pas le debut")
        void mettreEnPause() {
            Intervention it = interventionDepuis(vidange);
            it.demarrer(DEBUT);
            Instant debutFige = it.getDebutReel();

            it.mettreEnPause();

            assertThat(it.getStatut()).isEqualTo(StatutIntervention.EN_PAUSE);
            assertThat(it.getDebutReel()).isEqualTo(debutFige);
        }

        @Test
        @DisplayName("EN_PAUSE -> EN_COURS ne remet pas le debut a jour")
        void reprendre() {
            Intervention it = interventionDepuis(vidange);
            it.demarrer(DEBUT);
            Instant debutFige = it.getDebutReel();
            it.mettreEnPause();

            it.reprendre();

            assertThat(it.getStatut()).isEqualTo(StatutIntervention.EN_COURS);
            assertThat(it.getDebutReel()).isEqualTo(debutFige);
        }

        @Test
        @DisplayName("EN_COURS -> TERMINEE enregistre la fin reelle")
        void terminerDepuisEnCours() {
            Intervention it = interventionDepuis(vidange);
            it.demarrer(DEBUT);
            Instant fin = DEBUT.plus(Duration.ofHours(1));

            it.terminer(fin);

            assertThat(it.getStatut()).isEqualTo(StatutIntervention.TERMINEE);
            assertThat(it.getFinReelle()).isEqualTo(fin);
        }

        @Test
        @DisplayName("EN_PAUSE -> TERMINEE possible directement")
        void terminerDepuisPause() {
            Intervention it = interventionDepuis(vidange);
            it.demarrer(DEBUT);
            it.mettreEnPause();

            it.terminer(DEBUT.plus(Duration.ofHours(2)));

            assertThat(it.getStatut()).isEqualTo(StatutIntervention.TERMINEE);
        }

        @Test
        @DisplayName("TERMINEE -> FACTUREE, hook du module facturation")
        void facturer() {
            Intervention it = interventionDepuis(vidange);
            it.demarrer(DEBUT);
            it.terminer(DEBUT.plus(Duration.ofHours(1)));

            it.marquerFacturee();

            assertThat(it.getStatut()).isEqualTo(StatutIntervention.FACTUREE);
        }

        @Test
        @DisplayName("PLANIFIEE -> TERMINEE directement (prestation express)")
        void terminerDepuisPlanifiee() {
            Intervention it = interventionDepuis(vidange);
            Instant fin = DEBUT.plus(Duration.ofMinutes(15));

            it.terminer(fin);

            assertThat(it.getStatut()).isEqualTo(StatutIntervention.TERMINEE);
            assertThat(it.getFinReelle()).isEqualTo(fin);
            // debutReel aligne sur finReelle pour eviter une trace incomplete.
            assertThat(it.getDebutReel()).isEqualTo(fin);
        }
    }

    @Nested
    @DisplayName("transitions interdites")
    class TransitionsInterdites {

        @Test
        @DisplayName("PLANIFIEE ne peut pas etre mise en pause directement")
        void planifieeVersPause() {
            Intervention it = interventionDepuis(vidange);
            assertThatThrownBy(it::mettreEnPause)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("PLANIFIEE");
        }

        @Test
        @DisplayName("self-loops refuses (PLANIFIEE -> PLANIFIEE, EN_COURS -> EN_COURS, ...)")
        void selfLoopsRefuses() {
            for (StatutIntervention s : StatutIntervention.values()) {
                assertThat(s.peutPasserA(s))
                        .as("Aucun statut ne doit pouvoir transiter vers lui-meme : " + s)
                        .isFalse();
            }
        }

        @Test
        @DisplayName("double demarrage refuse")
        void doubleDemarrage() {
            Intervention it = interventionDepuis(vidange);
            it.demarrer(DEBUT);
            assertThatThrownBy(() -> it.demarrer(DEBUT.plusSeconds(1)))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("mise en pause depuis PLANIFIEE refusee")
        void pauseDepuisPlanifiee() {
            Intervention it = interventionDepuis(vidange);
            assertThatThrownBy(it::mettreEnPause).isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("FACTUREE est definitive")
        void factureeDefinitive() {
            Intervention it = interventionDepuis(vidange);
            it.demarrer(DEBUT);
            it.terminer(DEBUT.plus(Duration.ofHours(1)));
            it.marquerFacturee();

            assertThatThrownBy(it::mettreEnPause).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(it::marquerFacturee).isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("lignes et commentaire admin")
    class Editions {

        @Test
        @DisplayName("ajouterLigneMainOeuvre possible en PLANIFIEE, EN_COURS, EN_PAUSE")
        void ajouterLigneQuandEditable() {
            Intervention it = interventionDepuis(vidange);
            it.ajouterLigneMainOeuvre(freins, (short) 1, new BigDecimal("89.00"), new BigDecimal("21.00"));
            assertThat(it.getLignes()).hasSize(2);

            it.demarrer(DEBUT);
            it.ajouterLigneMainOeuvre(freins, (short) 1, new BigDecimal("89.00"), new BigDecimal("21.00"));
            assertThat(it.getLignes()).hasSize(3);
        }

        @Test
        @DisplayName("ajouterLigne refuse quand TERMINEE")
        void ajouterLigneRefuseQuandTerminee() {
            Intervention it = interventionDepuis(vidange);
            it.demarrer(DEBUT);
            it.terminer(DEBUT.plus(Duration.ofHours(1)));

            assertThatThrownBy(() -> it.ajouterLigneMainOeuvre(freins, (short) 1,
                    new BigDecimal("89.00"), new BigDecimal("21.00")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("TERMINEE");
        }

        @Test
        @DisplayName("modifierCommentaireAdmin nettoie et refuse quand terminee")
        void commentaireAdmin() {
            Intervention it = interventionDepuis(vidange);
            it.modifierCommentaireAdmin("  Piece commandee ");
            assertThat(it.getCommentaireAdmin()).isEqualTo("Piece commandee");

            it.modifierCommentaireAdmin("   ");
            assertThat(it.getCommentaireAdmin()).isNull();

            it.demarrer(DEBUT);
            it.terminer(DEBUT.plus(Duration.ofHours(1)));
            assertThatThrownBy(() -> it.modifierCommentaireAdmin("trop tard"))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("totaux")
    class Totaux {

        @Test
        @DisplayName("additionne les lignes HTVA et TVAC")
        void additionneLesLignes() {
            Intervention it = interventionDepuis(vidange, freins);

            assertThat(it.totalHtva()).isEqualByComparingTo("138.00");
            assertThat(it.totalTvac()).isEqualByComparingTo("166.98");
        }

        @Test
        @DisplayName("retirerLigne refuse quand TERMINEE")
        void retirerLigneRefuseQuandTerminee() {
            Intervention it = interventionDepuis(vidange);
            it.demarrer(DEBUT);
            it.terminer(DEBUT.plus(Duration.ofHours(1)));

            assertThatThrownBy(() -> it.retirerLigne(1L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("TERMINEE");
        }
    }
}
