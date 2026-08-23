package be.autoservplus.vente.domain;

import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Machines a etats du paiement et de la commande : les gardes vivent dans les
 * entites, en particulier celles qui tranchent la course job d expiration /
 * webhook tardif — une PAYEE ne redevient pas ANNULEE, une ANNULEE ne devient
 * pas PAYEE, un REUSSI est irreversible.
 */
@DisplayName("Machines a etats paiement et commande")
class PaiementTest {

    private static final Instant MAINTENANT = Instant.parse("2026-09-14T09:00:00Z");

    private Commande commande;
    private Paiement paiement;

    @BeforeEach
    void setUp() {
        Utilisateur marie = new Utilisateur(
                "marie@exemple.be", "$2a$12$h", "Dupont", "Marie", TypeUtilisateur.MEMBRE);
        commande = new Commande("CMD-2026-0001", marie,
                new BigDecimal("70.01"), new BigDecimal("10.20"), new BigDecimal("80.21"),
                MAINTENANT);
        paiement = new Paiement(commande, commande.getMontantTvac(), MAINTENANT);
    }

    @Nested
    @DisplayName("paiement")
    class MachinePaiement {

        @Test
        @DisplayName("nait INITIE avec une cle d'idempotence, et peut aboutir directement en REUSSI")
        void naissanceEtRaccourci() {
            assertThat(paiement.getStatut()).isEqualTo(StatutPaiement.INITIE);
            assertThat(paiement.getCleIdempotence()).isNotBlank();

            // Un webhook « paid » peut arriver sans que « pending » ait ete vu.
            paiement.confirmer(MAINTENANT.plusSeconds(60));

            assertThat(paiement.getStatut()).isEqualTo(StatutPaiement.REUSSI);
            assertThat(paiement.getDateFinalisation()).isEqualTo(MAINTENANT.plusSeconds(60));
        }

        @Test
        @DisplayName("REUSSI est irreversible : ni echec ni expiration ensuite")
        void reussiIrreversible() {
            paiement.confirmer(MAINTENANT);

            assertThatThrownBy(() -> paiement.echouer(MAINTENANT))
                    .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> paiement.expirer(MAINTENANT))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("ECHOUE est terminal : le re-essai est un NOUVEAU paiement")
        void echoueTerminal() {
            paiement.mettreEnCours();
            paiement.echouer(MAINTENANT);

            assertThatThrownBy(() -> paiement.confirmer(MAINTENANT))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(paiement.estTermine()).isTrue();
        }

        @Test
        @DisplayName("REUSSI mene au remboursement, qui est a son tour terminal (F30)")
        void remboursement() {
            paiement.confirmer(MAINTENANT);

            paiement.rembourser("re_fictif_0001");

            assertThat(paiement.getStatut()).isEqualTo(StatutPaiement.REMBOURSE);
            assertThat(paiement.getReferenceRemboursement()).isEqualTo("re_fictif_0001");
            assertThat(paiement.estTermine()).isTrue();
            // La date de finalisation date l encaissement, pas le remboursement :
            // c est elle que porte la facture immuable, elle ne se reecrit pas.
            assertThat(paiement.getDateFinalisation()).isEqualTo(MAINTENANT);
            assertThatThrownBy(() -> paiement.rembourser("re_fictif_0002"))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("seul un paiement encaisse se rembourse")
        void remboursementSeulementApresEncaissement() {
            assertThatThrownBy(() -> paiement.rembourser("re_fictif_0001"))
                    .isInstanceOf(IllegalStateException.class);

            paiement.mettreEnCours();
            paiement.echouer(MAINTENANT);
            assertThatThrownBy(() -> paiement.rembourser("re_fictif_0001"))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("la cle d'idempotence du remboursement est stable et distincte de celle du paiement")
        void cleIdempotenceRemboursement() {
            // Stable : un rejeu envoie la meme cle, le prestataire ne rembourse
            // qu une fois. Une cle tiree au hasard donnerait la garantie inverse.
            assertThat(paiement.cleIdempotenceRemboursement())
                    .isEqualTo(paiement.cleIdempotenceRemboursement())
                    .isNotEqualTo(paiement.getCleIdempotence())
                    .contains(paiement.getReference().toString());
        }

        @Test
        @DisplayName("la reference prestataire se pose une seule fois")
        void referencePrestataireUnique() {
            paiement.enregistrerReferencePrestataire("tr_fictif_0001");

            assertThatThrownBy(() -> paiement.enregistrerReferencePrestataire("tr_autre"))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(paiement.getReferenceMollie()).isEqualTo("tr_fictif_0001");
        }
    }

    @Nested
    @DisplayName("commande")
    class MachineCommande {

        @Test
        @DisplayName("confirmerPaiement : EN_ATTENTE_PAIEMENT -> PAYEE, date posee")
        void paiementConfirme() {
            commande.confirmerPaiement(MAINTENANT.plusSeconds(120));

            assertThat(commande.getStatut()).isEqualTo(StatutCommande.PAYEE);
            assertThat(commande.getDatePaiement()).isEqualTo(MAINTENANT.plusSeconds(120));
        }

        @Test
        @DisplayName("annuler : motif et date obligatoires, poses ensemble (RM-21)")
        void annulation() {
            commande.annuler(MotifAnnulationCommande.TIMEOUT_PAIEMENT, MAINTENANT.plusSeconds(1800));

            assertThat(commande.getStatut()).isEqualTo(StatutCommande.ANNULEE);
            assertThat(commande.getMotifAnnulation()).isEqualTo(MotifAnnulationCommande.TIMEOUT_PAIEMENT);
            assertThat(commande.getDateAnnulation()).isEqualTo(MAINTENANT.plusSeconds(1800));
        }

        @Test
        @DisplayName("une PAYEE ne redevient pas ANNULEE : le job d'expiration ne peut pas defaire un encaissement")
        void payeeJamaisAnnulee() {
            commande.confirmerPaiement(MAINTENANT);

            assertThatThrownBy(() -> commande.annuler(
                    MotifAnnulationCommande.TIMEOUT_PAIEMENT, MAINTENANT))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(commande.getStatut()).isEqualTo(StatutCommande.PAYEE);
        }

        @Test
        @DisplayName("rembourser : PAYEE -> REMBOURSEE, motif RETRACTATION_F30 pose (F30)")
        void remboursementApresRetractation() {
            commande.confirmerPaiement(MAINTENANT);

            commande.rembourser(MAINTENANT.plusSeconds(86_400));

            // REMBOURSEE et non ANNULEE : une commande encaissee puis contre-passee
            // n est pas une commande jamais payee.
            assertThat(commande.getStatut()).isEqualTo(StatutCommande.REMBOURSEE);
            assertThat(commande.getMotifAnnulation())
                    .isEqualTo(MotifAnnulationCommande.RETRACTATION_F30);
            assertThat(commande.getDateAnnulation()).isEqualTo(MAINTENANT.plusSeconds(86_400));
        }

        @Test
        @DisplayName("une commande non payee ne se rembourse pas, une REMBOURSEE ne se rembourse pas deux fois")
        void remboursementImpossible() {
            assertThatThrownBy(() -> commande.rembourser(MAINTENANT))
                    .isInstanceOf(IllegalStateException.class);

            commande.confirmerPaiement(MAINTENANT);
            commande.rembourser(MAINTENANT);
            assertThatThrownBy(() -> commande.rembourser(MAINTENANT))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("une ANNULEE ne devient pas PAYEE : un webhook tardif ne ressuscite pas la commande")
        void annuleeJamaisPayee() {
            commande.annuler(MotifAnnulationCommande.TIMEOUT_PAIEMENT, MAINTENANT);

            assertThatThrownBy(() -> commande.confirmerPaiement(MAINTENANT))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(commande.getStatut()).isEqualTo(StatutCommande.ANNULEE);
        }
    }
}
