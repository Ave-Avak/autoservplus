package be.autoservplus.vente.service;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.identite.domain.Consentement;
import be.autoservplus.identite.domain.TypeDocumentConsentement;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.ConsentementRepository;
import be.autoservplus.reservation.service.support.FormatageRdv;
import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.domain.LignePanier;
import be.autoservplus.vente.domain.Panier;
import be.autoservplus.vente.domain.StatutCommande;
import be.autoservplus.vente.repository.CommandeRepository;
import be.autoservplus.vente.repository.PaiementRepository;
import be.autoservplus.vente.repository.PanierRepository;
import be.autoservplus.vente.web.dto.ConfirmationCommandeVue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommandeService")
class CommandeServiceTest {

    private static final String EMAIL = "marie@exemple.be";
    private static final String IP = "203.0.113.7";
    private static final Instant MAINTENANT = Instant.parse("2026-09-14T09:00:00Z");

    @Mock private CommandeRepository commandes;
    @Mock private PaiementRepository paiements;
    @Mock private PanierRepository paniers;
    @Mock private ConsentementRepository consentements;
    @Mock private GenerateurNumeroCommande numeros;

    private CommandeService service;

    private Utilisateur marie;
    private Piece plaquettes;
    private Piece ampoule;

    @BeforeEach
    void setUp() {
        service = new CommandeService(commandes, paiements, paniers, consentements, numeros,
                Clock.fixed(MAINTENANT, ZoneId.of("Europe/Brussels")));

        marie = new Utilisateur(EMAIL, "$2a$12$h", "Dupont", "Marie", TypeUtilisateur.MEMBRE);
        Categorie freinage = new Categorie("FRE", "Freinage", TypeCategorie.PIECE);
        plaquettes = new Piece(freinage, "FRE-001", "Plaquettes avant", new BigDecimal("19.99"));
        plaquettes.setQuantiteStock(10); // taux par defaut : 21.00
        ampoule = new Piece(freinage, "ECL-001", "Ampoule H7", new BigDecimal("10.01"));
        ampoule.setTauxTva(new BigDecimal("6.00"));
        ampoule.setQuantiteStock(5);
    }

    /** Panier de marie : 3 ampoules a 6 % et 2 plaquettes a 21 % (cas RM-30 du module). */
    private Panier panierRempli() {
        Panier panier = new Panier(marie);
        panier.ajouterPiece(ampoule, 3);
        panier.ajouterPiece(plaquettes, 2);
        when(paniers.findByMembreEmail(EMAIL)).thenReturn(Optional.of(panier));
        return panier;
    }

    private void conversionPossible() {
        when(numeros.prochain()).thenReturn("CMD-2026-0001");
        when(commandes.saveAndFlush(any(Commande.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Nested
    @DisplayName("conversion nominale (F14)")
    class ConversionNominale {

        /**
         * Montants verifies a la main (cas a taux mixtes du module vente) :
         * ampoules 30,03 HTVA / 1,80 TVA / 31,83 TVAC ; plaquettes 39,98 / 8,40 /
         * 48,38 ; totaux 70,01 / 10,20 / 80,21 — HTVA + TVA = TVAC par construction.
         */
        @Test
        @DisplayName("cree la commande EN_ATTENTE_PAIEMENT aux montants figes, taux mixtes 6 % et 21 %")
        void creeLaCommandeAuxMontantsFiges() {
            panierRempli();
            conversionPossible();

            ConfirmationCommandeVue vue = service.passerCommande(EMAIL, true, false, IP);

            assertThat(vue.numero()).isEqualTo("CMD-2026-0001");
            assertThat(vue.totalTvac()).isEqualTo(FormatageRdv.euros(new BigDecimal("80.21")));

            ArgumentCaptor<Commande> captor = ArgumentCaptor.forClass(Commande.class);
            verify(commandes).saveAndFlush(captor.capture());
            Commande commande = captor.getValue();
            assertThat(commande.getStatut()).isEqualTo(StatutCommande.EN_ATTENTE_PAIEMENT);
            assertThat(commande.getMembre()).isSameAs(marie);
            assertThat(commande.getMontantHtva()).isEqualByComparingTo("70.01");
            assertThat(commande.getMontantTva()).isEqualByComparingTo("10.20");
            assertThat(commande.getMontantTvac()).isEqualByComparingTo("80.21");
            assertThat(commande.getDateCommande()).isEqualTo(MAINTENANT);
        }

        @Test
        @DisplayName("les lignes sont DEPLACEES vers la commande, le panier les perd")
        void lignesReaffectees() {
            Panier panier = panierRempli();
            conversionPossible();

            service.passerCommande(EMAIL, true, false, IP);

            assertThat(panier.getLignes())
                    .as("Memes lignes, nouveau rattachement : pas de recopie")
                    .hasSize(2)
                    .allSatisfy(l -> {
                        assertThat(l.getCommande()).isNotNull();
                        assertThat(l.getPanier())
                                .as("panier_id relache : au rechargement, le panier est vide")
                                .isNull();
                    });
        }

        @Test
        @DisplayName("les montants viennent des valeurs figees du panier, pas du catalogue courant")
        void montantsFigesPasLeCatalogue() {
            panierRempli();
            conversionPossible();
            // Le garage change les prix APRES l ajout au panier, AVANT la conversion.
            plaquettes.modifierPrix(new BigDecimal("999.99"));
            ampoule.modifierPrix(new BigDecimal("999.99"));

            service.passerCommande(EMAIL, true, false, IP);

            ArgumentCaptor<Commande> captor = ArgumentCaptor.forClass(Commande.class);
            verify(commandes).saveAndFlush(captor.capture());
            assertThat(captor.getValue().getMontantTvac()).isEqualByComparingTo("80.21");
        }

        @Test
        @DisplayName("une preuve d'acceptation CGV est ecrite : type, version, IP, horloge injectee")
        void preuveCgvEcrite() {
            panierRempli();
            conversionPossible();

            service.passerCommande(EMAIL, true, false, IP);

            ArgumentCaptor<Consentement> captor = ArgumentCaptor.forClass(Consentement.class);
            verify(consentements).save(captor.capture());
            Consentement preuve = captor.getValue();
            assertThat(preuve.getUtilisateur()).isSameAs(marie);
            assertThat(preuve.getTypeDocument()).isEqualTo(TypeDocumentConsentement.CGV);
            assertThat(preuve.getVersionAcceptee()).isEqualTo(Consentement.CGV_VERSION_COURANTE);
            assertThat(preuve.isAccorde()).isTrue();
            assertThat(preuve.getAdresseIp()).isEqualTo(IP);
            assertThat(preuve.getDateConsentement()).isEqualTo(MAINTENANT);
        }
    }

    @Nested
    @DisplayName("refus de conversion")
    class Refus {

        @Test
        @DisplayName("CGV non acceptees : rien n'est lu ni ecrit, pas meme le panier")
        void cgvNonAcceptees() {
            assertThatThrownBy(() -> service.passerCommande(EMAIL, false, false, IP))
                    .isInstanceOf(CgvNonAccepteesException.class);

            verifyNoInteractions(paniers, commandes, consentements, numeros);
        }

        @Test
        @DisplayName("panier jamais cree : refus « panier vide »")
        void panierInexistant() {
            when(paniers.findByMembreEmail(EMAIL)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.passerCommande(EMAIL, true, false, IP))
                    .isInstanceOf(PanierVideException.class);

            verifyNoInteractions(commandes, consentements);
        }

        @Test
        @DisplayName("panier vide : refus, aucune commande")
        void panierVide() {
            when(paniers.findByMembreEmail(EMAIL)).thenReturn(Optional.of(new Panier(marie)));

            assertThatThrownBy(() -> service.passerCommande(EMAIL, true, false, IP))
                    .isInstanceOf(PanierVideException.class);

            verifyNoInteractions(commandes, consentements);
        }

        /**
         * La premiere ligne (ampoules) est en stock, la seconde (plaquettes) ne
         * l est plus : le refus doit etre TOTAL — pas de commande, pas de preuve,
         * stock intact, et AUCUNE ligne reaffectee, pas meme celle qui passait.
         */
        @Test
        @DisplayName("stock insuffisant sur une ligne : refus total, rien n'est modifie (atomicite)")
        void stockInsuffisantRefusTotal() {
            Panier panier = panierRempli();
            plaquettes.setQuantiteStock(1); // 2 demandees au panier

            assertThatThrownBy(() -> service.passerCommande(EMAIL, true, false, IP))
                    .isInstanceOf(StockInsuffisantException.class)
                    .satisfies(e -> {
                        StockInsuffisantException stock = (StockInsuffisantException) e;
                        assertThat(stock.getLibelle()).isEqualTo("Plaquettes avant");
                        assertThat(stock.getQuantiteDisponible()).isEqualTo(1);
                    });

            verify(commandes, never()).saveAndFlush(any());
            verify(consentements, never()).save(any());
            assertThat(plaquettes.getQuantiteStock())
                    .as("Le stock n est jamais touche a la conversion (decrement au paiement)")
                    .isEqualTo(1);
            assertThat(panier.getLignes()).allSatisfy((LignePanier l) -> {
                assertThat(l.getPanier()).isNotNull();
                assertThat(l.getCommande()).isNull();
            });
        }

        @Test
        @DisplayName("piece devenue inactive : refus (contrainte F13), aucune ecriture")
        void pieceInactive() {
            panierRempli();
            ampoule.desactiver();

            assertThatThrownBy(() -> service.passerCommande(EMAIL, true, false, IP))
                    .isInstanceOf(PieceInactiveException.class)
                    .satisfies(e -> assertThat(((PieceInactiveException) e).getLibelle())
                            .isEqualTo("Ampoule H7"));

            verify(commandes, never()).saveAndFlush(any());
            verify(consentements, never()).save(any());
        }
    }

    @Nested
    @DisplayName("confirmation")
    class Confirmation {

        @Test
        @DisplayName("le proprietaire lit sa confirmation : numero et total TVAC")
        void confirmationProprietaire() {
            Commande commande = new Commande("CMD-2026-0001", marie,
                    new BigDecimal("70.01"), new BigDecimal("10.20"), new BigDecimal("80.21"),
                    MAINTENANT);
            when(commandes.findByReference(commande.getReference()))
                    .thenReturn(Optional.of(commande));

            ConfirmationCommandeVue vue = service.confirmation(commande.getReference(), EMAIL);

            assertThat(vue.numero()).isEqualTo("CMD-2026-0001");
            assertThat(vue.totalTvac()).isEqualTo(FormatageRdv.euros(new BigDecimal("80.21")));
        }

        @Test
        @DisplayName("commande d'autrui : 404, l'existence n'est pas confirmee")
        void ownership() {
            Commande commande = new Commande("CMD-2026-0001", marie,
                    new BigDecimal("70.01"), new BigDecimal("10.20"), new BigDecimal("80.21"),
                    MAINTENANT);
            when(commandes.findByReference(commande.getReference()))
                    .thenReturn(Optional.of(commande));

            assertThatThrownBy(() -> service.confirmation(commande.getReference(), "intrus@exemple.be"))
                    .isInstanceOf(RessourceIntrouvableException.class);
        }
    }
}
