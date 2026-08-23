package be.autoservplus.retractation.domain;

import be.autoservplus.facturation.domain.Avoir;
import be.autoservplus.facturation.domain.Facture;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.vente.domain.Commande;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Machine a etats de la demande de retractation (F30, RM-23) : les gardes vivent
 * dans l entite, pas dans le service — un second appelant ne peut pas les
 * contourner en oubliant de les recopier.
 */
@DisplayName("DemandeAnnulation")
class DemandeAnnulationTest {

    private static final Instant MAINTENANT = Instant.parse("2026-08-25T10:00:00Z");

    private Utilisateur marie;
    private Utilisateur admin;
    private Commande commande;
    private DemandeAnnulation demande;

    @BeforeEach
    void setUp() {
        marie = new Utilisateur("marie@exemple.be", "$2a$12$h", "Dupont", "Marie",
                TypeUtilisateur.MEMBRE);
        admin = new Utilisateur("admin@autoservplus.be", "$2a$12$h", "Garage", "Admin",
                TypeUtilisateur.ADMINISTRATEUR);
        commande = new Commande("CMD-2026-0001", marie,
                new BigDecimal("39.98"), new BigDecimal("8.40"), new BigDecimal("48.38"),
                MAINTENANT.minusSeconds(86_400));
        demande = new DemandeAnnulation(commande, "Piece non compatible", MAINTENANT);
    }

    private Avoir avoir() {
        Facture facture = Facture.pourCommande("2026-0001", (short) 2026, 1, commande,
                new BigDecimal("21.00"), MAINTENANT);
        return Avoir.contrePassant("AV-2026-0001", facture, Avoir.MOTIF_RETRACTATION, MAINTENANT);
    }

    @Test
    @DisplayName("nait EN_ATTENTE, sans trace de decision")
    void naissance() {
        assertThat(demande.getStatut()).isEqualTo(StatutDemandeAnnulation.EN_ATTENTE);
        assertThat(demande.estEnAttente()).isTrue();
        assertThat(demande.getDecidePar()).isNull();
        assertThat(demande.getDecideLe()).isNull();
        assertThat(demande.getAvoir()).isNull();
        assertThat(demande.getReference()).isNotNull();
    }

    @Test
    @DisplayName("le motif du membre est facultatif : vide ou blanc vaut absence")
    void motifMembreFacultatif() {
        // Le droit de retractation est inconditionnel (CDE art. VI.47) : exiger un
        // motif poserait une condition la ou la loi n en pose aucune. La colonne
        // reste NULL plutot que de contenir une chaine vide.
        assertThat(new DemandeAnnulation(commande, null, MAINTENANT).getMotifMembre()).isNull();
        assertThat(new DemandeAnnulation(commande, "   ", MAINTENANT).getMotifMembre()).isNull();
        assertThat(new DemandeAnnulation(commande, "  trop cher  ", MAINTENANT).getMotifMembre())
                .isEqualTo("trop cher");
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("pose l'avoir, le decideur et la date d'un seul geste")
        void validationComplete() {
            Avoir note = avoir();

            demande.valider(note, admin, MAINTENANT.plusSeconds(3600));

            // Un seul geste, parce que ck_demande_annulation_avoir refuse l etat
            // intermediaire « validee sans avoir » : transitionner puis rattacher
            // produirait, entre les deux ecritures, une ligne que la base rejette.
            assertThat(demande.getStatut()).isEqualTo(StatutDemandeAnnulation.VALIDEE);
            assertThat(demande.getAvoir()).isSameAs(note);
            assertThat(demande.getDecidePar()).isSameAs(admin);
            assertThat(demande.getDecideLe()).isEqualTo(MAINTENANT.plusSeconds(3600));
            assertThat(demande.estEnAttente()).isFalse();
        }

        @Test
        @DisplayName("exige l'avoir : une validation sans note de credit est refusee")
        void validationSansAvoir() {
            assertThatThrownBy(() -> demande.valider(null, admin, MAINTENANT))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("avoir");
        }

        @Test
        @DisplayName("une demande deja validee ne se revalide pas")
        void validationNonRejouable() {
            demande.valider(avoir(), admin, MAINTENANT);

            assertThatThrownBy(() -> demande.valider(avoir(), admin, MAINTENANT))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("VALIDEE");
        }

        @Test
        @DisplayName("une demande validee ne peut plus etre refusee")
        void pasDeRetourApresValidation() {
            demande.valider(avoir(), admin, MAINTENANT);

            assertThatThrownBy(() -> demande.refuser("changement d'avis", admin, MAINTENANT))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("refus")
    class Refus {

        @Test
        @DisplayName("conserve le motif, le decideur et la date, sans emettre d'avoir")
        void refusMotive() {
            demande.refuser("  Piece deja montee sur le vehicule  ", admin, MAINTENANT);

            assertThat(demande.getStatut()).isEqualTo(StatutDemandeAnnulation.REFUSEE);
            assertThat(demande.getMotifDecision()).isEqualTo("Piece deja montee sur le vehicule");
            assertThat(demande.getDecidePar()).isSameAs(admin);
            assertThat(demande.getDecideLe()).isEqualTo(MAINTENANT);
            // Un refus ne produit aucun mouvement comptable.
            assertThat(demande.getAvoir()).isNull();
        }

        @Test
        @DisplayName("exige un motif : c'est le garage qui se justifie, pas le consommateur")
        void refusSansMotif() {
            for (String vide : new String[]{null, "", "   "}) {
                assertThatThrownBy(() -> demande.refuser(vide, admin, MAINTENANT))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("motive");
            }
            assertThat(demande.getStatut()).isEqualTo(StatutDemandeAnnulation.EN_ATTENTE);
        }

        @Test
        @DisplayName("une demande refusee ne se revalide pas")
        void pasDeRetourApresRefus() {
            demande.refuser("Piece deballee", admin, MAINTENANT);

            assertThatThrownBy(() -> demande.valider(avoir(), admin, MAINTENANT))
                    .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> demande.refuser("autre motif", admin, MAINTENANT))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("transitions declarees")
    class Transitions {

        @Test
        @DisplayName("EN_ATTENTE mene aux deux issues, et elles sont terminales")
        void tableDesTransitions() {
            assertThat(StatutDemandeAnnulation.EN_ATTENTE
                    .peutPasserA(StatutDemandeAnnulation.VALIDEE)).isTrue();
            assertThat(StatutDemandeAnnulation.EN_ATTENTE
                    .peutPasserA(StatutDemandeAnnulation.REFUSEE)).isTrue();
            assertThat(StatutDemandeAnnulation.EN_ATTENTE
                    .peutPasserA(StatutDemandeAnnulation.EN_ATTENTE)).isFalse();

            for (StatutDemandeAnnulation tranchee : new StatutDemandeAnnulation[]{
                    StatutDemandeAnnulation.VALIDEE, StatutDemandeAnnulation.REFUSEE}) {
                assertThat(tranchee.estTranchee()).isTrue();
                for (StatutDemandeAnnulation cible : StatutDemandeAnnulation.values()) {
                    assertThat(tranchee.peutPasserA(cible)).isFalse();
                }
            }
        }
    }
}
