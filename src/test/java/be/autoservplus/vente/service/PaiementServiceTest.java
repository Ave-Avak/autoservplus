package be.autoservplus.vente.service;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.PieceRepository;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.domain.MotifAnnulationCommande;
import be.autoservplus.vente.domain.Paiement;
import be.autoservplus.vente.domain.Panier;
import be.autoservplus.vente.domain.StatutCommande;
import be.autoservplus.vente.domain.StatutPaiement;
import be.autoservplus.vente.repository.CommandeRepository;
import be.autoservplus.vente.repository.PaiementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaiementService")
class PaiementServiceTest {

    private static final String EMAIL = "marie@exemple.be";
    private static final Instant MAINTENANT = Instant.parse("2026-09-14T09:00:00Z");

    @Mock private PaiementRepository paiements;
    @Mock private CommandeRepository commandes;
    @Mock private PieceRepository pieces;
    @Mock private PrestatairePaiement prestataire;
    @Mock private ApplicationEventPublisher evenements;

    private PaiementService service;

    private Utilisateur marie;
    private Piece plaquettes;
    private Piece ampoule;
    private Commande commande;
    private Panier panier;

    @BeforeEach
    void setUp() {
        service = new PaiementService(paiements, commandes, pieces, prestataire, evenements,
                Clock.fixed(MAINTENANT, ZoneId.of("Europe/Brussels")),
                // Barre finale volontaire : elle verifie que le service la retire au
                // lieu de produire une URL a double separateur.
                "https://garage.example/");

        marie = new Utilisateur(EMAIL, "$2a$12$h", "Dupont", "Marie", TypeUtilisateur.MEMBRE);
        Categorie freinage = new Categorie("FRE", "Freinage", TypeCategorie.PIECE);
        plaquettes = new Piece(freinage, "FRE-001", "Plaquettes avant", new BigDecimal("19.99"));
        plaquettes.setQuantiteStock(10);
        ReflectionTestUtils.setField(plaquettes, "id", 1L);
        ampoule = new Piece(freinage, "ECL-001", "Ampoule H7", new BigDecimal("10.01"));
        ampoule.setTauxTva(new BigDecimal("6.00"));
        ampoule.setQuantiteStock(5);
        ReflectionTestUtils.setField(ampoule, "id", 2L);

        // Commande issue du panier RM-30 du module : 2 plaquettes + 3 ampoules, 80,21 TVAC.
        panier = new Panier(marie);
        panier.ajouterPiece(plaquettes, 2);
        panier.ajouterPiece(ampoule, 3);
        commande = new Commande("CMD-2026-0001", marie,
                new BigDecimal("70.01"), new BigDecimal("10.20"), new BigDecimal("80.21"),
                MAINTENANT);
        ReflectionTestUtils.setField(commande, "id", 10L);
        commande.reprendreLignes(panier.getLignes());
    }

    /** Paiement initie et connu du repository sous sa reference prestataire. */
    private Paiement paiementInitie() {
        Paiement paiement = new Paiement(commande, commande.getMontantTvac(), MAINTENANT);
        paiement.enregistrerReferencePrestataire("tr_fictif_0001");
        when(paiements.findByReferenceMollie("tr_fictif_0001")).thenReturn(Optional.of(paiement));
        return paiement;
    }

    private void statutAuthentique(StatutPaiement statut) {
        when(prestataire.lireEtat("tr_fictif_0001")).thenReturn(EtatPaiement.de(statut));
    }

    private void etatAuthentique(StatutPaiement statut, String methode) {
        when(prestataire.lireEtat("tr_fictif_0001"))
                .thenReturn(new EtatPaiement(statut, methode));
    }

    /** Stubs du chemin « confirme » : verrou commande, lignes triees, verrous pieces. */
    private void cheminConfirmation() {
        when(commandes.verrouillerParId(10L)).thenReturn(Optional.of(commande));
        when(commandes.lignesDe(commande)).thenReturn(panier.getLignes());
        when(pieces.verrouillerParId(1L)).thenReturn(Optional.of(plaquettes));
        when(pieces.verrouillerParId(2L)).thenReturn(Optional.of(ampoule));
        when(paiements.saveAndFlush(any(Paiement.class))).thenAnswer(inv -> inv.getArgument(0));
        when(commandes.saveAndFlush(any(Commande.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Nested
    @DisplayName("initiation")
    class Initiation {

        @Test
        @DisplayName("commande EN_ATTENTE_PAIEMENT : Paiement INITIE, reference stockee, URL rendue")
        void initiationNominale() {
            when(commandes.findByReference(commande.getReference()))
                    .thenReturn(Optional.of(commande));
            when(prestataire.creerPaiement(any(DemandePaiement.class)))
                    .thenReturn(new PaiementCree("tr_fictif_0001", "/paiement-fictif/tr_fictif_0001"));
            when(paiements.saveAndFlush(any(Paiement.class))).thenAnswer(inv -> inv.getArgument(0));

            String url = service.initierPaiement(commande.getReference(), EMAIL);

            assertThat(url).isEqualTo("/paiement-fictif/tr_fictif_0001");
            ArgumentCaptor<Paiement> captor = ArgumentCaptor.forClass(Paiement.class);
            verify(paiements).saveAndFlush(captor.capture());
            Paiement paiement = captor.getValue();
            assertThat(paiement.getStatut()).isEqualTo(StatutPaiement.INITIE);
            assertThat(paiement.getReferenceMollie()).isEqualTo("tr_fictif_0001");
            assertThat(paiement.getMontant()).isEqualByComparingTo("80.21");
            assertThat(paiement.getDateInitiation()).isEqualTo(MAINTENANT);

            // La cle d idempotence du paiement accompagne bien la demande au prestataire.
            ArgumentCaptor<DemandePaiement> demande = ArgumentCaptor.forClass(DemandePaiement.class);
            verify(prestataire).creerPaiement(demande.capture());
            assertThat(demande.getValue().cleIdempotence()).isEqualTo(paiement.getCleIdempotence());

            // Les deux adresses remises au prestataire sont ABSOLUES et derivees de
            // l URL publique : le prestataire renvoie un navigateur depuis l exterieur,
            // et notifie un serveur sans session — ni l un ni l autre ne saurait quoi
            // faire d un chemin relatif. La barre finale de la configuration est retiree.
            assertThat(demande.getValue().urlRetour())
                    .isEqualTo("https://garage.example/commande/"
                            + commande.getReference() + "/retour");
            assertThat(demande.getValue().urlNotification())
                    .isEqualTo("https://garage.example/webhooks/paiement");
        }

        @Test
        @DisplayName("commande deja payee ou annulee : initiation refusee")
        void initiationRefuseeHorsAttente() {
            commande.confirmerPaiement(MAINTENANT);
            when(commandes.findByReference(commande.getReference()))
                    .thenReturn(Optional.of(commande));

            assertThatThrownBy(() -> service.initierPaiement(commande.getReference(), EMAIL))
                    .isInstanceOf(PaiementImpossibleException.class);
            verifyNoInteractions(prestataire);
        }

        @Test
        @DisplayName("commande d'autrui : 404, le prestataire n'est jamais appele")
        void initiationOwnership() {
            when(commandes.findByReference(commande.getReference()))
                    .thenReturn(Optional.of(commande));

            assertThatThrownBy(() -> service.initierPaiement(commande.getReference(), "intrus@exemple.be"))
                    .isInstanceOf(RessourceIntrouvableException.class);
            verifyNoInteractions(prestataire);
        }
    }

    @Nested
    @DisplayName("webhook — statut relu, jamais le payload")
    class Webhook {

        @Test
        @DisplayName("le moyen rapporte par le prestataire est enregistre sur le paiement")
        void moyenDePaiementEnregistre() {
            // Le CdC P384 demande le mode de paiement au detail d une commande. Le
            // prestataire ne le connait qu une fois le client passe par sa page : il
            // arrive donc a la relecture, pas a la creation.
            Paiement paiement = paiementInitie();
            etatAuthentique(StatutPaiement.REUSSI, "bancontact");
            cheminConfirmation();

            service.traiterNotification("tr_fictif_0001");

            assertThat(paiement.getMethode()).isEqualTo("bancontact");
        }

        @Test
        @DisplayName("le moyen deja enregistre n est jamais reecrit par un rejeu")
        void moyenJamaisReecrit() {
            // La relecture est rejouee a chaque notification et a chaque retour du
            // membre. Un prestataire qui cesserait de rapporter le moyen effacerait
            // sinon une donnee que la facture emise a peut-etre deja opposee.
            Paiement paiement = paiementInitie();
            etatAuthentique(StatutPaiement.REUSSI, "bancontact");
            cheminConfirmation();
            service.traiterNotification("tr_fictif_0001");

            etatAuthentique(StatutPaiement.REUSSI, null);
            service.traiterNotification("tr_fictif_0001");
            assertThat(paiement.getMethode()).isEqualTo("bancontact");

            etatAuthentique(StatutPaiement.REUSSI, "carte");
            service.traiterNotification("tr_fictif_0001");
            assertThat(paiement.getMethode()).isEqualTo("bancontact");
        }

        @Test
        @DisplayName("un prestataire qui ne rapporte aucun moyen laisse le champ vide")
        void aucunMoyenRapporte() {
            // Le bouchon est dans ce cas : l ecran dit « moyen non communique » plutot
            // que d inventer un moyen que personne n a employe.
            Paiement paiement = paiementInitie();
            statutAuthentique(StatutPaiement.REUSSI);
            cheminConfirmation();

            service.traiterNotification("tr_fictif_0001");

            assertThat(paiement.getMethode()).isNull();
        }

        @Test
        @DisplayName("paye : commande PAYEE, stock decremente, evenement publie une fois")
        void webhookPaye() {
            Paiement paiement = paiementInitie();
            statutAuthentique(StatutPaiement.REUSSI);
            cheminConfirmation();

            service.traiterNotification("tr_fictif_0001");

            assertThat(commande.getStatut()).isEqualTo(StatutCommande.PAYEE);
            assertThat(commande.getDatePaiement()).isEqualTo(MAINTENANT);
            assertThat(paiement.getStatut()).isEqualTo(StatutPaiement.REUSSI);
            assertThat(plaquettes.getQuantiteStock()).isEqualTo(8);  // 10 - 2
            assertThat(ampoule.getQuantiteStock()).isEqualTo(2);     // 5 - 3
            assertThat(commande.isRuptureAHonorer()).isFalse();
            verify(evenements).publishEvent(new CommandePayeeEvent(commande.getReference()));
        }

        @Test
        @DisplayName("double webhook paye : aucun double decrement, aucun double evenement")
        void doubleWebhookPaye() {
            Paiement paiement = paiementInitie();
            statutAuthentique(StatutPaiement.REUSSI);
            cheminConfirmation();

            service.traiterNotification("tr_fictif_0001");
            service.traiterNotification("tr_fictif_0001");

            assertThat(plaquettes.getQuantiteStock())
                    .as("Le second webhook retombe sur une commande PAYEE : idempotent")
                    .isEqualTo(8);
            assertThat(ampoule.getQuantiteStock()).isEqualTo(2);
            verify(evenements, times(1)).publishEvent(any(CommandePayeeEvent.class));
            verify(commandes, times(1)).lignesDe(commande);
        }

        @Test
        @DisplayName("echoue : paiement ECHOUE, la commande reste EN_ATTENTE_PAIEMENT (re-essai possible)")
        void webhookEchoue() {
            Paiement paiement = paiementInitie();
            statutAuthentique(StatutPaiement.ECHOUE);
            when(paiements.saveAndFlush(any(Paiement.class))).thenAnswer(inv -> inv.getArgument(0));

            service.traiterNotification("tr_fictif_0001");

            assertThat(paiement.getStatut()).isEqualTo(StatutPaiement.ECHOUE);
            assertThat(paiement.getDateFinalisation()).isEqualTo(MAINTENANT);
            assertThat(commande.getStatut()).isEqualTo(StatutCommande.EN_ATTENTE_PAIEMENT);
            assertThat(plaquettes.getQuantiteStock()).isEqualTo(10);
            verifyNoInteractions(evenements);
        }

        /**
         * Regle (a) : la derniere ampoule est partie entre la conversion et le
         * paiement. La commande passe QUAND MEME PAYEE (on n annule pas un
         * encaissement), le stock plancher a 0, le drapeau leve l alerte.
         */
        @Test
        @DisplayName("rupture au paiement : PAYEE quand meme, stock plancher a 0, alerte levee")
        void ruptureAuPaiement() {
            ampoule.setQuantiteStock(1); // 3 demandees
            paiementInitie();
            statutAuthentique(StatutPaiement.REUSSI);
            cheminConfirmation();

            service.traiterNotification("tr_fictif_0001");

            assertThat(commande.getStatut()).isEqualTo(StatutCommande.PAYEE);
            assertThat(commande.isRuptureAHonorer()).isTrue();
            assertThat(ampoule.getQuantiteStock())
                    .as("Plancher a 0, jamais negatif (le CHECK en base le redouble)")
                    .isZero();
            assertThat(plaquettes.getQuantiteStock())
                    .as("La ligne en stock est servie normalement")
                    .isEqualTo(8);
            verify(evenements).publishEvent(any(CommandePayeeEvent.class));
        }

        @Test
        @DisplayName("paye sur une commande ANNULEE (course perdue) : elle ne ressuscite pas")
        void payeSurCommandeAnnulee() {
            commande.annuler(MotifAnnulationCommande.TIMEOUT_PAIEMENT, MAINTENANT);
            Paiement paiement = paiementInitie();
            statutAuthentique(StatutPaiement.REUSSI);
            when(commandes.verrouillerParId(10L)).thenReturn(Optional.of(commande));
            when(paiements.saveAndFlush(any(Paiement.class))).thenAnswer(inv -> inv.getArgument(0));

            service.traiterNotification("tr_fictif_0001");

            assertThat(commande.getStatut())
                    .as("Une ANNULEE ne devient jamais PAYEE")
                    .isEqualTo(StatutCommande.ANNULEE);
            assertThat(paiement.getStatut())
                    .as("La realite comptable est actee : encaisse, a rembourser hors ligne")
                    .isEqualTo(StatutPaiement.REUSSI);
            assertThat(plaquettes.getQuantiteStock()).isEqualTo(10);
            verifyNoInteractions(evenements);
        }

        @Test
        @DisplayName("en cours : le paiement suit, rien d'autre ne bouge")
        void webhookEnCours() {
            Paiement paiement = paiementInitie();
            statutAuthentique(StatutPaiement.EN_COURS);
            when(paiements.saveAndFlush(any(Paiement.class))).thenAnswer(inv -> inv.getArgument(0));

            service.traiterNotification("tr_fictif_0001");

            assertThat(paiement.getStatut()).isEqualTo(StatutPaiement.EN_COURS);
            assertThat(commande.getStatut()).isEqualTo(StatutCommande.EN_ATTENTE_PAIEMENT);
            verifyNoInteractions(evenements);
        }

        @Test
        @DisplayName("reference inconnue : 404, le prestataire n'est meme pas consulte")
        void referenceInconnue() {
            when(paiements.findByReferenceMollie("tr_forge")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.traiterNotification("tr_forge"))
                    .isInstanceOf(RessourceIntrouvableException.class);
            verify(prestataire, never()).lireEtat(any());
        }
    }
}
