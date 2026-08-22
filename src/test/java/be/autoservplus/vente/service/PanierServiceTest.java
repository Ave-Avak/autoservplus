package be.autoservplus.vente.service;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.PieceRepository;
import be.autoservplus.common.exception.ConflitConcurrenceException;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.reservation.service.support.FormatageRdv;
import be.autoservplus.vente.domain.LignePanier;
import be.autoservplus.vente.domain.Panier;
import be.autoservplus.vente.repository.PanierRepository;
import be.autoservplus.vente.web.dto.PanierVue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
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
@DisplayName("PanierService")
class PanierServiceTest {

    private static final String EMAIL = "marie@exemple.be";

    @Mock private PanierRepository paniers;
    @Mock private PieceRepository pieces;
    @Mock private UtilisateurRepository utilisateurs;

    private PanierService service;

    private Utilisateur marie;
    private Piece plaquettes;
    private Piece ampoule;

    @BeforeEach
    void setUp() {
        service = new PanierService(paniers, pieces, utilisateurs);

        marie = new Utilisateur(EMAIL, "$2a$12$h", "Dupont", "Marie", TypeUtilisateur.MEMBRE);
        Categorie freinage = new Categorie("FRE", "Freinage", TypeCategorie.PIECE);
        plaquettes = new Piece(freinage, "FRE-001", "Plaquettes avant", new BigDecimal("19.99"));
        plaquettes.setQuantiteStock(10); // taux par defaut : 21.00
        ampoule = new Piece(freinage, "ECL-001", "Ampoule H7", new BigDecimal("10.01"));
        ampoule.setTauxTva(new BigDecimal("6.00"));
        ampoule.setQuantiteStock(5);
    }

    /** Panier existant de marie, renvoye par le repository. */
    private Panier panierExistant() {
        Panier panier = new Panier(marie);
        when(paniers.findByMembreEmail(EMAIL)).thenReturn(Optional.of(panier));
        return panier;
    }

    /** A poser dans les seuls tests ou une ecriture aboutit (Mockito strict). */
    private void avecEcriture() {
        when(paniers.saveAndFlush(any(Panier.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void pieceConnue(Piece piece) {
        when(pieces.findByReference(piece.getReference())).thenReturn(Optional.of(piece));
    }

    @Nested
    @DisplayName("trouve-ou-cree (RM-19)")
    class TrouveOuCree {

        @Test
        @DisplayName("premier ajout : cree LE panier du membre, une seule fois")
        void creeAuPremierAjout() {
            pieceConnue(plaquettes);
            when(paniers.findByMembreEmail(EMAIL)).thenReturn(Optional.empty());
            when(utilisateurs.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(marie));
            when(paniers.saveAndFlush(any(Panier.class))).thenAnswer(inv -> inv.getArgument(0));

            PanierVue vue = service.ajouterPiece(EMAIL, plaquettes.getReference(), 2);

            ArgumentCaptor<Panier> captor = ArgumentCaptor.forClass(Panier.class);
            // Deux ecritures du MEME panier (creation puis contenu), pas deux paniers.
            verify(paniers, times(2)).saveAndFlush(captor.capture());
            assertThat(captor.getAllValues().get(0)).isSameAs(captor.getAllValues().get(1));
            assertThat(captor.getValue().getMembre()).isSameAs(marie);
            assertThat(vue.nombreArticles()).isEqualTo(2);
        }

        @Test
        @DisplayName("panier deja ouvert : reutilise, ne recree jamais")
        void reutiliseLePanierOuvert() {
            pieceConnue(plaquettes);
            Panier existant = panierExistant();
            avecEcriture();

            service.ajouterPiece(EMAIL, plaquettes.getReference(), 1);

            verifyNoInteractions(utilisateurs);
            verify(paniers).saveAndFlush(existant);
        }

        @Test
        @DisplayName("la lecture ne cree rien : sans panier, vue vide et zero ecriture")
        void lectureSansCreation() {
            when(paniers.findByMembreEmail(EMAIL)).thenReturn(Optional.empty());

            PanierVue vue = service.panierDuMembre(EMAIL);

            assertThat(vue.estVide()).isTrue();
            assertThat(vue.nombreArticles()).isZero();
            verify(paniers, never()).saveAndFlush(any());
            verifyNoInteractions(utilisateurs);
        }

        @Test
        @DisplayName("course a la creation : l'index unique tranche, conflit explicite")
        void courseALaCreation() {
            pieceConnue(plaquettes);
            when(paniers.findByMembreEmail(EMAIL)).thenReturn(Optional.empty());
            when(utilisateurs.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(marie));
            when(paniers.saveAndFlush(any(Panier.class)))
                    .thenThrow(new DataIntegrityViolationException("uq_panier_membre_actif"));

            assertThatThrownBy(() -> service.ajouterPiece(EMAIL, plaquettes.getReference(), 1))
                    .isInstanceOf(ConflitConcurrenceException.class)
                    .hasMessageContaining("réessayez");
        }
    }

    @Nested
    @DisplayName("ajout d'une piece (F13)")
    class Ajout {

        @Test
        @DisplayName("prix, libelle et taux sont figes : changer la piece apres coup ne change pas la ligne")
        void valeursFigeesALAjout() {
            pieceConnue(plaquettes);
            panierExistant();
            avecEcriture();
            service.ajouterPiece(EMAIL, plaquettes.getReference(), 2);

            // Le garage change ensuite le prix et le taux au catalogue.
            plaquettes.modifierPrix(new BigDecimal("99.99"));
            plaquettes.setTauxTva(new BigDecimal("6.00"));

            PanierVue vue = service.panierDuMembre(EMAIL);
            PanierVue.LignePanierVue ligne = vue.lignes().get(0);
            assertThat(ligne.prixUnitaireHtva()).isEqualTo(FormatageRdv.euros(new BigDecimal("19.99")));
            assertThat(ligne.tauxTva()).isEqualTo("21");
            // 19,99 x 2 = 39,98 HTVA — sur le prix d'entree au panier, pas les 99,99 du jour.
            assertThat(ligne.totalHtva()).isEqualTo(FormatageRdv.euros(new BigDecimal("39.98")));
        }

        @Test
        @DisplayName("ajouter une piece deja presente fusionne la ligne, sans doublon")
        void fusionneLesDoublons() {
            pieceConnue(plaquettes);
            panierExistant();
            avecEcriture();

            service.ajouterPiece(EMAIL, plaquettes.getReference(), 2);
            PanierVue vue = service.ajouterPiece(EMAIL, plaquettes.getReference(), 3);

            assertThat(vue.lignes()).hasSize(1);
            assertThat(vue.lignes().get(0).quantite()).isEqualTo((short) 5);
        }

        @Test
        @DisplayName("le controle de stock porte sur la quantite cumulee, pas sur le seul ajout")
        void stockControleSurLeCumul() {
            plaquettes.setQuantiteStock(4);
            pieceConnue(plaquettes);
            panierExistant();
            avecEcriture();
            service.ajouterPiece(EMAIL, plaquettes.getReference(), 2);

            // 2 deja au panier + 3 demandees = 5 > 4 en stock : refus, et la quantite
            // remontee est ce qui peut encore etre AJOUTE (4 - 2 = 2).
            assertThatThrownBy(() -> service.ajouterPiece(EMAIL, plaquettes.getReference(), 3))
                    .isInstanceOf(StockInsuffisantException.class)
                    .satisfies(e -> assertThat(((StockInsuffisantException) e).getQuantiteDisponible())
                            .isEqualTo(2));
        }

        @Test
        @DisplayName("stock insuffisant des le premier ajout : la quantite disponible est remontee")
        void stockInsuffisantRemonteLeDisponible() {
            plaquettes.setQuantiteStock(1);
            pieceConnue(plaquettes);
            when(paniers.findByMembreEmail(EMAIL)).thenReturn(Optional.of(new Panier(marie)));

            assertThatThrownBy(() -> service.ajouterPiece(EMAIL, plaquettes.getReference(), 5))
                    .isInstanceOf(StockInsuffisantException.class)
                    .satisfies(e -> assertThat(((StockInsuffisantException) e).getQuantiteDisponible())
                            .isEqualTo(1));
            verify(paniers, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("une piece inactive est refusee (contrainte F13), rien n'est ecrit")
        void pieceInactiveRefusee() {
            plaquettes.desactiver();
            pieceConnue(plaquettes);

            assertThatThrownBy(() -> service.ajouterPiece(EMAIL, plaquettes.getReference(), 1))
                    .isInstanceOf(PieceInactiveException.class)
                    .hasMessageContaining("n est plus proposee");
            verify(paniers, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("quantite nulle ou negative refusee avant toute lecture")
        void quantiteInvalideRefusee() {
            assertThatThrownBy(() -> service.ajouterPiece(EMAIL, plaquettes.getReference(), 0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> service.ajouterPiece(EMAIL, plaquettes.getReference(), -3))
                    .isInstanceOf(IllegalArgumentException.class);
            verifyNoInteractions(pieces, paniers);
        }

        @Test
        @DisplayName("reference de piece inconnue : 404")
        void pieceInconnue() {
            when(pieces.findByReference(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.ajouterPiece(EMAIL, plaquettes.getReference(), 1))
                    .isInstanceOf(RessourceIntrouvableException.class);
        }
    }

    @Nested
    @DisplayName("modification, retrait, vidage")
    class ModificationEtRetrait {

        /** Panier de marie avec une ligne persistee (id 1) de 2 plaquettes. */
        private Panier panierAvecLigne() {
            Panier panier = panierExistant();
            LignePanier ligne = panier.ajouterPiece(plaquettes, 2);
            // L'id est pose par la base a la persistence ; en test unitaire, on le
            // fixe par reflexion pour pouvoir adresser la ligne comme le fera le web.
            ReflectionTestUtils.setField(ligne, "id", 1L);
            return panier;
        }

        @Test
        @DisplayName("modifie la quantite sous controle du stock sur la nouvelle valeur totale")
        void modifieLaQuantite() {
            plaquettes.setQuantiteStock(4);
            panierAvecLigne();
            avecEcriture();

            PanierVue vue = service.modifierQuantite(EMAIL, 1L, 4);

            assertThat(vue.lignes().get(0).quantite()).isEqualTo((short) 4);
        }

        @Test
        @DisplayName("refuse une quantite au-dela du stock, avec le disponible remonte")
        void refuseQuantiteAuDelaDuStock() {
            plaquettes.setQuantiteStock(4);
            panierAvecLigne();

            assertThatThrownBy(() -> service.modifierQuantite(EMAIL, 1L, 5))
                    .isInstanceOf(StockInsuffisantException.class)
                    .satisfies(e -> assertThat(((StockInsuffisantException) e).getQuantiteDisponible())
                            .isEqualTo(4));
        }

        @Test
        @DisplayName("piece devenue inactive : reduire reste permis, augmenter est refuse (contrainte F13)")
        void ligneInactiveNAugmentePas() {
            Panier panier = panierAvecLigne();
            avecEcriture();
            plaquettes.desactiver();

            assertThatThrownBy(() -> service.modifierQuantite(EMAIL, 1L, 3))
                    .isInstanceOf(PieceInactiveException.class);

            PanierVue vue = service.modifierQuantite(EMAIL, 1L, 1);
            assertThat(vue.lignes().get(0).quantite()).isEqualTo((short) 1);
            assertThat(panier.nombreArticles()).isEqualTo(1);
        }

        @Test
        @DisplayName("ligne inconnue ou d'autrui : 404, sans reveler l'existence")
        void ligneDAutrui() {
            panierAvecLigne();

            assertThatThrownBy(() -> service.modifierQuantite(EMAIL, 99L, 1))
                    .isInstanceOf(RessourceIntrouvableException.class);
            assertThatThrownBy(() -> service.retirerLigne(EMAIL, 99L))
                    .isInstanceOf(RessourceIntrouvableException.class);
        }

        @Test
        @DisplayName("membre sans panier : meme 404 « ligne introuvable », rien n'est divulgue")
        void sansPanierMeme404() {
            when(paniers.findByMembreEmail(EMAIL)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.modifierQuantite(EMAIL, 1L, 1))
                    .isInstanceOf(RessourceIntrouvableException.class)
                    .hasMessageContaining("LignePanier");
        }

        @Test
        @DisplayName("retire une ligne ; le panier peut redevenir vide")
        void retireUneLigne() {
            panierAvecLigne();
            avecEcriture();

            PanierVue vue = service.retirerLigne(EMAIL, 1L);

            assertThat(vue.estVide()).isTrue();
            assertThat(vue.nombreArticles()).isZero();
        }

        @Test
        @DisplayName("vider retire toutes les lignes")
        void vide() {
            Panier panier = panierAvecLigne();
            avecEcriture();

            service.vider(EMAIL);

            assertThat(panier.estVide()).isTrue();
            verify(paniers).saveAndFlush(panier);
        }

        @Test
        @DisplayName("vider sans panier existant : non-evenement, aucune ecriture")
        void viderSansPanier() {
            when(paniers.findByMembreEmail(EMAIL)).thenReturn(Optional.empty());

            service.vider(EMAIL);

            verify(paniers, never()).saveAndFlush(any());
        }
    }

    @Nested
    @DisplayName("totaux HTVA / TVA / TVAC (RM-30)")
    class TotauxRm30 {

        /**
         * Cas a taux mixtes, verifie a la main :
         * <pre>
         * Ampoule H7   : 10,01 x 3 = 30,03 HTVA ; TVAC = 30,03 x 1,06 = 31,8318 -> 31,83 ;
         *                TVA = 31,83 - 30,03 = 1,80  (taux 6 %)
         * Plaquettes   : 19,99 x 2 = 39,98 HTVA ; TVAC = 39,98 x 1,21 = 48,3758 -> 48,38 ;
         *                TVA = 48,38 - 39,98 = 8,40  (taux 21 %)
         * Totaux       : HTVA 70,01 ; TVA 10,20 ; TVAC 80,21
         * </pre>
         * Un calcul « TVA sur le total » serait faux ici : aucun taux unique ne
         * transforme 70,01 en 80,21 (cela ferait ~14,57 %). La somme ligne a ligne
         * est la seule lecture correcte de RM-30.
         */
        @Test
        @DisplayName("taux mixtes 6 % et 21 % : totaux sommes ligne a ligne, arrondis HALF_UP")
        void tauxMixtes() {
            Panier panier = new Panier(marie);
            panier.ajouterPiece(ampoule, 3);
            panier.ajouterPiece(plaquettes, 2);
            when(paniers.findByMembreEmail(EMAIL)).thenReturn(Optional.of(panier));

            PanierVue vue = service.panierDuMembre(EMAIL);

            assertThat(vue.nombreArticles()).isEqualTo(5);
            PanierVue.LignePanierVue ligneAmpoule = vue.lignes().get(0);
            assertThat(ligneAmpoule.totalHtva()).isEqualTo(FormatageRdv.euros(new BigDecimal("30.03")));
            assertThat(ligneAmpoule.totalTva()).isEqualTo(FormatageRdv.euros(new BigDecimal("1.80")));
            assertThat(ligneAmpoule.totalTvac()).isEqualTo(FormatageRdv.euros(new BigDecimal("31.83")));
            assertThat(ligneAmpoule.tauxTva()).isEqualTo("6");

            PanierVue.LignePanierVue lignePlaquettes = vue.lignes().get(1);
            assertThat(lignePlaquettes.totalHtva()).isEqualTo(FormatageRdv.euros(new BigDecimal("39.98")));
            assertThat(lignePlaquettes.totalTva()).isEqualTo(FormatageRdv.euros(new BigDecimal("8.40")));
            assertThat(lignePlaquettes.totalTvac()).isEqualTo(FormatageRdv.euros(new BigDecimal("48.38")));

            assertThat(vue.totalHtva()).isEqualTo(FormatageRdv.euros(new BigDecimal("70.01")));
            assertThat(vue.totalTva()).isEqualTo(FormatageRdv.euros(new BigDecimal("10.20")));
            assertThat(vue.totalTvac()).isEqualTo(FormatageRdv.euros(new BigDecimal("80.21")));
        }

        @Test
        @DisplayName("l'identite HTVA + TVA = TVAC tient par construction, centimes compris")
        void identiteComptable() {
            Panier panier = new Panier(marie);
            panier.ajouterPiece(ampoule, 3);
            panier.ajouterPiece(plaquettes, 2);

            assertThat(panier.totalHtva().add(panier.totalTva()))
                    .as("La table commande verifiera cette identite par CHECK a la conversion")
                    .isEqualByComparingTo(panier.totalTvac());
        }

        @Test
        @DisplayName("une piece inactive au panier est signalee dans la vue (contrainte F13)")
        void pieceInactiveSignalee() {
            Panier panier = new Panier(marie);
            panier.ajouterPiece(plaquettes, 1);
            plaquettes.desactiver();
            when(paniers.findByMembreEmail(EMAIL)).thenReturn(Optional.of(panier));

            PanierVue vue = service.panierDuMembre(EMAIL);

            assertThat(vue.contientPieceInactive()).isTrue();
            assertThat(vue.lignes().get(0).pieceActive()).isFalse();
        }
    }
}
