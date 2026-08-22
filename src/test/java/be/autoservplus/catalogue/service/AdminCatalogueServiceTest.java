package be.autoservplus.catalogue.service;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.HistoriqueModificationCatalogue;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.domain.TypeEntiteCatalogue;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.HistoriqueModificationCatalogueRepository;
import be.autoservplus.catalogue.repository.PieceRepository;
import be.autoservplus.catalogue.repository.PrestationRepository;
import be.autoservplus.catalogue.service.dto.ArticleVueAdmin;
import be.autoservplus.catalogue.service.dto.DonneesPiece;
import be.autoservplus.catalogue.service.dto.DonneesPrestation;
import be.autoservplus.catalogue.service.dto.ModificationCatalogueVue;
import be.autoservplus.catalogue.service.dto.PropositionSuppression;
import be.autoservplus.common.exception.RegleMetierException;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.service.AuteurCourant;
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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires du back-office catalogue : creation (A1, A4) et modification
 * (A2, A5), unicites, coherence de type des categories, stock.
 *
 * <p>La securite {@code @PreAuthorize} n est pas evaluee ici (pas de contexte
 * Spring) : elle est prouvee en integration par {@code AdminCatalogueServiceIT},
 * comme pour {@code AdminRdvService}.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminCatalogueService")
class AdminCatalogueServiceTest {

    private static final Instant MAINTENANT = Instant.parse("2026-08-22T10:15:00Z");

    @Mock private CategorieRepository categories;
    @Mock private PrestationRepository prestations;
    @Mock private PieceRepository pieces;
    @Mock private HistoriqueModificationCatalogueRepository historiques;
    @Mock private AuteurCourant auteurCourant;

    private AdminCatalogueService service;

    private final Categorie entretien = new Categorie("ENTRETIEN", "Entretien", TypeCategorie.SERVICE);
    private final Categorie filtres = new Categorie("P_FILTRES", "Filtres", TypeCategorie.PIECE);

    @BeforeEach
    void setUp() {
        // Horloge figee : l horodatage du journal des modifications (A2, A5) est une
        // valeur attendue du test, pas un instant subi.
        service = new AdminCatalogueService(categories, prestations, pieces, historiques,
                auteurCourant, Clock.fixed(MAINTENANT, ZoneId.of("Europe/Brussels")));
    }

    /** Les lignes d historique ecrites par l appel qui vient de se produire, dans l ordre. */
    private List<HistoriqueModificationCatalogue> lignesHistorisees() {
        ArgumentCaptor<HistoriqueModificationCatalogue> capture =
                ArgumentCaptor.forClass(HistoriqueModificationCatalogue.class);
        verify(historiques, atLeast(0)).save(capture.capture());
        return capture.getAllValues();
    }

    private DonneesPrestation donneesVidange() {
        return new DonneesPrestation("ENTRETIEN", "VID", "Vidange", "Huile et filtre",
                new BigDecimal("75.00"), new BigDecimal("21.00"), 60, true);
    }

    private DonneesPiece donneesFiltre() {
        return new DonneesPiece("P_FILTRES", "F-001", "Filtre a huile", "Bosch",
                "Visserie comprise", new BigDecimal("12.50"), new BigDecimal("21.00"), 8, 2, true);
    }

    @Nested
    @DisplayName("creation de categorie")
    class CreationCategorie {

        @Test
        @DisplayName("enregistre la categorie lorsque le code est libre")
        void creeQuandCodeLibre() {
            when(categories.existsByCode("FREINAGE")).thenReturn(false);
            when(categories.save(any(Categorie.class))).thenAnswer(i -> i.getArgument(0));

            Categorie resultat = service.creerCategorie("FREINAGE", "Freinage", TypeCategorie.SERVICE);

            assertThat(resultat.getCode()).isEqualTo("FREINAGE");
            assertThat(resultat.getType()).isEqualTo(TypeCategorie.SERVICE);
            assertThat(resultat.isActif()).isTrue();
        }

        @Test
        @DisplayName("refuse un code deja utilise")
        void refuseUnCodeDuplique() {
            when(categories.existsByCode("FREINAGE")).thenReturn(true);

            assertThatThrownBy(() -> service.creerCategorie("FREINAGE", "Freinage", TypeCategorie.SERVICE))
                    .isInstanceOf(DoublonCatalogueException.class)
                    .hasMessageContaining("deja utilise");

            verify(categories, never()).save(any());
        }
    }

    @Nested
    @DisplayName("creation de prestation (A1)")
    class CreationPrestation {

        @Test
        @DisplayName("rattache la prestation a une categorie de service, avec tous les champs")
        void creeUnePrestation() {
            when(prestations.existsByCode("VID")).thenReturn(false);
            when(prestations.existsByLibelleIgnoreCase("Vidange")).thenReturn(false);
            when(categories.findByCode("ENTRETIEN")).thenReturn(Optional.of(entretien));
            when(prestations.save(any(Prestation.class))).thenAnswer(i -> i.getArgument(0));

            Prestation resultat = service.creerPrestation(donneesVidange());

            assertThat(resultat.getCode()).isEqualTo("VID");
            assertThat(resultat.getCategorie()).isEqualTo(entretien);
            assertThat(resultat.getDescription()).isEqualTo("Huile et filtre");
            assertThat(resultat.getDureeMinutes()).isEqualTo(60);
            assertThat(resultat.getTauxTva()).isEqualByComparingTo(new BigDecimal("21.00"));
            assertThat(resultat.getReference()).isNotNull();
            assertThat(resultat.isActif()).isTrue();
        }

        @Test
        @DisplayName("respecte le statut initial inactif demande")
        void respecteLeStatutInitialInactif() {
            when(prestations.existsByCode("VID")).thenReturn(false);
            when(prestations.existsByLibelleIgnoreCase("Vidange")).thenReturn(false);
            when(categories.findByCode("ENTRETIEN")).thenReturn(Optional.of(entretien));
            when(prestations.save(any(Prestation.class))).thenAnswer(i -> i.getArgument(0));

            Prestation resultat = service.creerPrestation(new DonneesPrestation(
                    "ENTRETIEN", "VID", "Vidange", null,
                    new BigDecimal("75.00"), new BigDecimal("21.00"), 60, false));

            assertThat(resultat.isActif()).isFalse();
        }

        @Test
        @DisplayName("A1 : refuse un nom de service deja utilise")
        void refuseUnNomDuplique() {
            when(prestations.existsByCode("VID")).thenReturn(false);
            when(prestations.existsByLibelleIgnoreCase("Vidange")).thenReturn(true);

            assertThatThrownBy(() -> service.creerPrestation(donneesVidange()))
                    .isInstanceOf(DoublonCatalogueException.class)
                    .hasMessageContaining("existe deja")
                    .extracting("champ").isEqualTo("libelle");

            verify(prestations, never()).save(any());
        }

        @Test
        @DisplayName("refuse un code de prestation deja utilise")
        void refuseUnCodeDuplique() {
            when(prestations.existsByCode("VID")).thenReturn(true);

            assertThatThrownBy(() -> service.creerPrestation(donneesVidange()))
                    .isInstanceOf(DoublonCatalogueException.class)
                    .hasMessageContaining("deja utilise")
                    .extracting("champ").isEqualTo("code");
        }

        @Test
        @DisplayName("refuse une categorie destinee aux pieces")
        void refuseUneCategorieDePieces() {
            when(prestations.existsByCode("VID")).thenReturn(false);
            when(prestations.existsByLibelleIgnoreCase("Vidange")).thenReturn(false);
            when(categories.findByCode("P_FILTRES")).thenReturn(Optional.of(filtres));

            assertThatThrownBy(() -> service.creerPrestation(new DonneesPrestation(
                    "P_FILTRES", "VID", "Vidange", null,
                    new BigDecimal("75.00"), new BigDecimal("21.00"), 60, true)))
                    .isInstanceOf(RegleMetierException.class)
                    .hasMessageContaining("destinee aux pieces");

            verify(prestations, never()).save(any());
        }

        @Test
        @DisplayName("refuse une categorie inexistante")
        void refuseUneCategorieInconnue() {
            when(prestations.existsByCode("VID")).thenReturn(false);
            when(prestations.existsByLibelleIgnoreCase("Vidange")).thenReturn(false);
            when(categories.findByCode("INCONNUE")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.creerPrestation(new DonneesPrestation(
                    "INCONNUE", "VID", "Vidange", null,
                    new BigDecimal("75.00"), new BigDecimal("21.00"), 60, true)))
                    .isInstanceOf(RessourceIntrouvableException.class);
        }

        @Test
        @DisplayName("refuse un taux de TVA hors de la liste belge")
        void refuseUnTauxHorsListe() {
            when(prestations.existsByCode("VID")).thenReturn(false);
            when(prestations.existsByLibelleIgnoreCase("Vidange")).thenReturn(false);
            when(categories.findByCode("ENTRETIEN")).thenReturn(Optional.of(entretien));

            assertThatThrownBy(() -> service.creerPrestation(new DonneesPrestation(
                    "ENTRETIEN", "VID", "Vidange", null,
                    new BigDecimal("75.00"), new BigDecimal("19.00"), 60, true)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Taux de TVA");

            verify(prestations, never()).save(any());
        }
    }

    @Nested
    @DisplayName("modification de prestation (A2)")
    class ModificationPrestation {

        private Prestation vidangeExistante() {
            Prestation prestation = new Prestation(entretien, "VID", "Vidange",
                    new BigDecimal("75.00"), 60);
            // Le journal des modifications designe sa cible par son id : une entite
            // chargee depuis la base en a toujours un, une entite de test doit se le
            // voir poser.
            ReflectionTestUtils.setField(prestation, "id", 7L);
            when(prestations.findByReference(prestation.getReference()))
                    .thenReturn(Optional.of(prestation));
            return prestation;
        }

        @Test
        @DisplayName("applique tous les champs modifiables, code exclu")
        void modifieLesChamps() {
            Prestation prestation = vidangeExistante();
            Categorie freinage = new Categorie("FREINAGE", "Freinage", TypeCategorie.SERVICE);
            when(prestations.existsByLibelleIgnoreCaseAndReferenceNot(
                    "Vidange longue duree", prestation.getReference())).thenReturn(false);
            when(categories.findByCode("FREINAGE")).thenReturn(Optional.of(freinage));

            service.modifierPrestation(prestation.getReference(), new DonneesPrestation(
                    "FREINAGE", "AUTRE-CODE", "Vidange longue duree", "Nouvelle description",
                    new BigDecimal("89.00"), new BigDecimal("6.00"), 45, false));

            assertThat(prestation.getLibelle()).isEqualTo("Vidange longue duree");
            assertThat(prestation.getDescription()).isEqualTo("Nouvelle description");
            assertThat(prestation.getCategorie()).isEqualTo(freinage);
            assertThat(prestation.getPrixHtva()).isEqualByComparingTo(new BigDecimal("89.00"));
            assertThat(prestation.getTauxTva()).isEqualByComparingTo(new BigDecimal("6.00"));
            assertThat(prestation.getDureeMinutes()).isEqualTo(45);
            assertThat(prestation.isActif()).isFalse();
            // Le code est l identite technique de la prestation : il reste inchange.
            assertThat(prestation.getCode()).isEqualTo("VID");
        }

        @Test
        @DisplayName("A1 : refuse le nom d une autre prestation")
        void refuseLeNomDUneAutre() {
            Prestation prestation = vidangeExistante();
            when(prestations.existsByLibelleIgnoreCaseAndReferenceNot(
                    "Diagnostic", prestation.getReference())).thenReturn(true);

            assertThatThrownBy(() -> service.modifierPrestation(prestation.getReference(),
                    new DonneesPrestation("ENTRETIEN", "VID", "Diagnostic", null,
                            new BigDecimal("75.00"), new BigDecimal("21.00"), 60, true)))
                    .isInstanceOf(DoublonCatalogueException.class)
                    .hasMessageContaining("existe deja");

            assertThat(prestation.getLibelle()).isEqualTo("Vidange");
        }

        @Test
        @DisplayName("accepte de conserver son propre nom")
        void accepteSonPropreNom() {
            Prestation prestation = vidangeExistante();
            when(prestations.existsByLibelleIgnoreCaseAndReferenceNot(
                    "Vidange", prestation.getReference())).thenReturn(false);
            when(categories.findByCode("ENTRETIEN")).thenReturn(Optional.of(entretien));

            service.modifierPrestation(prestation.getReference(), new DonneesPrestation(
                    "ENTRETIEN", "VID", "Vidange", null,
                    new BigDecimal("79.00"), new BigDecimal("21.00"), 60, true));

            assertThat(prestation.getPrixHtva()).isEqualByComparingTo(new BigDecimal("79.00"));
        }

        @Test
        @DisplayName("refuse une categorie destinee aux pieces")
        void refuseUneCategorieDePieces() {
            Prestation prestation = vidangeExistante();
            when(prestations.existsByLibelleIgnoreCaseAndReferenceNot(
                    "Vidange", prestation.getReference())).thenReturn(false);
            when(categories.findByCode("P_FILTRES")).thenReturn(Optional.of(filtres));

            assertThatThrownBy(() -> service.modifierPrestation(prestation.getReference(),
                    new DonneesPrestation("P_FILTRES", "VID", "Vidange", null,
                            new BigDecimal("75.00"), new BigDecimal("21.00"), 60, true)))
                    .isInstanceOf(RegleMetierException.class)
                    .hasMessageContaining("destinee aux pieces");

            assertThat(prestation.getCategorie()).isEqualTo(entretien);
        }

        @Test
        @DisplayName("leve une exception sur une reference inconnue")
        void echoueSurUneReferenceInconnue() {
            UUID reference = UUID.randomUUID();
            when(prestations.findByReference(reference)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.modifierPrestation(reference, donneesVidange()))
                    .isInstanceOf(RessourceIntrouvableException.class);
        }
    }

    @Nested
    @DisplayName("creation de piece (A4)")
    class CreationPiece {

        @Test
        @DisplayName("rattache la piece a une categorie de pieces, avec son stock initial")
        void creeUnePiece() {
            when(pieces.existsByReferenceFabricant("F-001")).thenReturn(false);
            when(categories.findByCode("P_FILTRES")).thenReturn(Optional.of(filtres));
            when(pieces.save(any(Piece.class))).thenAnswer(i -> i.getArgument(0));

            Piece resultat = service.creerPiece(donneesFiltre());

            assertThat(resultat.getReferenceFabricant()).isEqualTo("F-001");
            assertThat(resultat.getMarque()).isEqualTo("Bosch");
            assertThat(resultat.getQuantiteStock()).isEqualTo(8);
            assertThat(resultat.getSeuilAlerte()).isEqualTo(2);
            assertThat(resultat.isActif()).isTrue();
        }

        @Test
        @DisplayName("refuse une reference fabricant deja enregistree")
        void refuseUneReferenceDupliquee() {
            when(pieces.existsByReferenceFabricant("F-001")).thenReturn(true);

            assertThatThrownBy(() -> service.creerPiece(donneesFiltre()))
                    .isInstanceOf(DoublonCatalogueException.class)
                    .hasMessageContaining("deja enregistree")
                    .extracting("champ").isEqualTo("referenceFabricant");

            verify(pieces, never()).save(any());
        }

        @Test
        @DisplayName("refuse une categorie destinee aux prestations")
        void refuseUneCategorieDeServices() {
            when(pieces.existsByReferenceFabricant("F-001")).thenReturn(false);
            when(categories.findByCode("ENTRETIEN")).thenReturn(Optional.of(entretien));

            assertThatThrownBy(() -> service.creerPiece(new DonneesPiece(
                    "ENTRETIEN", "F-001", "Filtre", null, null,
                    new BigDecimal("12.50"), new BigDecimal("21.00"), 0, 0, true)))
                    .isInstanceOf(RegleMetierException.class)
                    .hasMessageContaining("destinee aux prestations");
        }
    }

    @Nested
    @DisplayName("modification de piece (A5)")
    class ModificationPiece {

        @Test
        @DisplayName("applique tous les champs modifiables, reference fabricant exclue")
        void modifieLesChamps() {
            Piece piece = new Piece(filtres, "F-001", "Filtre a huile", new BigDecimal("12.50"));
            ReflectionTestUtils.setField(piece, "id", 9L);
            when(pieces.findByReference(piece.getReference())).thenReturn(Optional.of(piece));
            Categorie eclairage = new Categorie("P_ECLAIRAGE", "Eclairage", TypeCategorie.PIECE);
            when(categories.findByCode("P_ECLAIRAGE")).thenReturn(Optional.of(eclairage));

            service.modifierPiece(piece.getReference(), new DonneesPiece(
                    "P_ECLAIRAGE", "AUTRE-REF", "Ampoule H7", "Philips", "Vendue par deux",
                    new BigDecimal("9.90"), new BigDecimal("6.00"), 20, 5, false));

            assertThat(piece.getLibelle()).isEqualTo("Ampoule H7");
            assertThat(piece.getMarque()).isEqualTo("Philips");
            assertThat(piece.getDescription()).isEqualTo("Vendue par deux");
            assertThat(piece.getCategorie()).isEqualTo(eclairage);
            assertThat(piece.getPrixHtva()).isEqualByComparingTo(new BigDecimal("9.90"));
            assertThat(piece.getTauxTva()).isEqualByComparingTo(new BigDecimal("6.00"));
            assertThat(piece.getQuantiteStock()).isEqualTo(20);
            assertThat(piece.getSeuilAlerte()).isEqualTo(5);
            assertThat(piece.isActif()).isFalse();
            // La reference fabricant est l ancre d unicite de la piece : inchangee.
            assertThat(piece.getReferenceFabricant()).isEqualTo("F-001");
        }
    }

    @Nested
    @DisplayName("historisation des modifications (A2, A5)")
    class Historisation {

        private final Utilisateur paul = new Utilisateur("admin@garage.be", "$2a$12$h",
                "Garage", "Paul", TypeUtilisateur.ADMINISTRATEUR);

        private Prestation vidangePersistee() {
            Prestation prestation = new Prestation(entretien, "VID", "Vidange",
                    new BigDecimal("75.00"), 60);
            prestation.setTauxTva(new BigDecimal("21.00"));
            prestation.setDescription("Huile et filtre");
            ReflectionTestUtils.setField(prestation, "id", 7L);
            when(prestations.findByReference(prestation.getReference()))
                    .thenReturn(Optional.of(prestation));
            when(categories.findByCode("ENTRETIEN")).thenReturn(Optional.of(entretien));
            return prestation;
        }

        private Piece filtrePersiste() {
            Piece piece = new Piece(filtres, "F-001", "Filtre a huile", new BigDecimal("12.50"));
            piece.setTauxTva(new BigDecimal("21.00"));
            piece.setMarque("Bosch");
            piece.setDescription("Visserie comprise");
            piece.setQuantiteStock(8);
            piece.setSeuilAlerte(2);
            ReflectionTestUtils.setField(piece, "id", 9L);
            when(pieces.findByReference(piece.getReference())).thenReturn(Optional.of(piece));
            return piece;
        }

        /** Les donnees de la prestation persistee, avec le seul prix modifie. */
        private DonneesPrestation vidangeAvecPrix(String prixHtva) {
            return new DonneesPrestation("ENTRETIEN", "VID", "Vidange", "Huile et filtre",
                    new BigDecimal(prixHtva), new BigDecimal("21.00"), 60, true);
        }

        @Test
        @DisplayName("A2 : un seul champ modifie ecrit exactement une ligne (qui, quand, quoi)")
        void unChampUneLigne() {
            Prestation prestation = vidangePersistee();
            when(auteurCourant.resoudre()).thenReturn(paul);

            service.modifierPrestation(prestation.getReference(), vidangeAvecPrix("89.00"));

            assertThat(lignesHistorisees()).singleElement().satisfies(ligne -> {
                assertThat(ligne.getTypeEntite()).isEqualTo(TypeEntiteCatalogue.PRESTATION);
                assertThat(ligne.getEntiteId()).isEqualTo(7L);
                assertThat(ligne.getChampModifie()).isEqualTo("prixHtva");
                assertThat(ligne.getValeurAvant()).isEqualTo("75.00");
                assertThat(ligne.getValeurApres()).isEqualTo("89.00");
                // Le « quand » vient de l horloge injectee, jamais d un Instant.now().
                assertThat(ligne.getHorodatage()).isEqualTo(MAINTENANT);
                // Le « qui » vient du contexte de securite, jamais d un parametre de requete.
                assertThat(ligne.getAuteur()).isSameAs(paul);
            });
        }

        @Test
        @DisplayName("A2 : chaque champ change produit sa propre ligne")
        void uneLigneParChampChange() {
            Prestation prestation = vidangePersistee();

            service.modifierPrestation(prestation.getReference(), new DonneesPrestation(
                    "ENTRETIEN", "VID", "Vidange longue duree", "Huile et filtre",
                    new BigDecimal("89.00"), new BigDecimal("21.00"), 45, true));

            assertThat(lignesHistorisees())
                    .extracting(HistoriqueModificationCatalogue::getChampModifie,
                            HistoriqueModificationCatalogue::getValeurAvant,
                            HistoriqueModificationCatalogue::getValeurApres)
                    .containsExactly(
                            tuple("libelle", "Vidange", "Vidange longue duree"),
                            tuple("prixHtva", "75.00", "89.00"),
                            tuple("dureeMinutes", "60", "45"));
        }

        @Test
        @DisplayName("A2 : une modification qui ne change rien n ecrit aucune ligne")
        void aucuneLigneSansChangement() {
            Prestation prestation = vidangePersistee();

            service.modifierPrestation(prestation.getReference(), vidangeAvecPrix("75.00"));

            verifyNoInteractions(historiques);
        }

        @Test
        @DisplayName("A2 : le meme prix a une autre echelle n invente pas de modification")
        void aucuneLignePourUneEchelleDifferente() {
            Prestation prestation = vidangePersistee();

            // Le formulaire renvoie « 75 » la ou la base stocke « 75.00 » : meme montant.
            service.modifierPrestation(prestation.getReference(), vidangeAvecPrix("75"));

            verifyNoInteractions(historiques);
        }

        @Test
        @DisplayName("A2 : une valeur qui disparait est tracee comme telle")
        void traceLesValeursNulles() {
            Prestation prestation = vidangePersistee();

            service.modifierPrestation(prestation.getReference(), new DonneesPrestation(
                    "ENTRETIEN", "VID", "Vidange", null,
                    new BigDecimal("75.00"), new BigDecimal("21.00"), 60, true));

            assertThat(lignesHistorisees()).singleElement().satisfies(ligne -> {
                assertThat(ligne.getChampModifie()).isEqualTo("description");
                assertThat(ligne.getValeurAvant()).isEqualTo("Huile et filtre");
                assertThat(ligne.getValeurApres()).isNull();
            });
        }

        @Test
        @DisplayName("A2 : une modification refusee ne laisse aucune trace")
        void aucuneLigneSurRefus() {
            Prestation prestation = new Prestation(entretien, "VID", "Vidange",
                    new BigDecimal("75.00"), 60);
            ReflectionTestUtils.setField(prestation, "id", 7L);
            when(prestations.findByReference(prestation.getReference()))
                    .thenReturn(Optional.of(prestation));
            when(prestations.existsByLibelleIgnoreCaseAndReferenceNot(
                    "Diagnostic", prestation.getReference())).thenReturn(true);

            assertThatThrownBy(() -> service.modifierPrestation(prestation.getReference(),
                    new DonneesPrestation("ENTRETIEN", "VID", "Diagnostic", null,
                            new BigDecimal("75.00"), new BigDecimal("21.00"), 60, true)))
                    .isInstanceOf(DoublonCatalogueException.class);

            verifyNoInteractions(historiques);
        }

        @Test
        @DisplayName("A5 : la piece historise ses champs propres, stock et seuil compris")
        void historisationDeLaPiece() {
            Piece piece = filtrePersiste();
            Categorie eclairage = new Categorie("P_ECLAIRAGE", "Eclairage", TypeCategorie.PIECE);
            when(categories.findByCode("P_ECLAIRAGE")).thenReturn(Optional.of(eclairage));

            service.modifierPiece(piece.getReference(), new DonneesPiece(
                    "P_ECLAIRAGE", "F-001", "Ampoule H7", "Philips", "Visserie comprise",
                    new BigDecimal("9.90"), new BigDecimal("6.00"), 20, 5, false));

            assertThat(lignesHistorisees())
                    .allSatisfy(ligne -> {
                        assertThat(ligne.getTypeEntite()).isEqualTo(TypeEntiteCatalogue.PIECE);
                        assertThat(ligne.getEntiteId()).isEqualTo(9L);
                    })
                    .extracting(HistoriqueModificationCatalogue::getChampModifie)
                    // La description, seule inchangee, est absente ; la reference
                    // fabricant est immuable, elle n est jamais comparee.
                    .containsExactly("categorie", "libelle", "marque", "prixHtva", "tauxTva",
                            "quantiteStock", "seuilAlerte", "actif");
        }

        @Test
        @DisplayName("sans utilisateur identifiable, la trace existe quand meme sans auteur")
        void traceSansAuteur() {
            Prestation prestation = vidangePersistee();
            when(auteurCourant.resoudre()).thenReturn(null);

            service.modifierPrestation(prestation.getReference(), vidangeAvecPrix("89.00"));

            assertThat(lignesHistorisees()).singleElement()
                    .extracting(HistoriqueModificationCatalogue::getAuteur).isNull();
        }

        @Test
        @DisplayName("l historique est expose en DTO : l entite ne franchit pas le service")
        void historiqueExposeEnDto() {
            Prestation prestation = new Prestation(entretien, "VID", "Vidange",
                    new BigDecimal("75.00"), 60);
            ReflectionTestUtils.setField(prestation, "id", 7L);
            when(prestations.findByReference(prestation.getReference()))
                    .thenReturn(Optional.of(prestation));
            when(historiques.historiqueDe(TypeEntiteCatalogue.PRESTATION, 7L)).thenReturn(List.of(
                    new HistoriqueModificationCatalogue(TypeEntiteCatalogue.PRESTATION, 7L,
                            "prixHtva", "75.00", "89.00", MAINTENANT, paul)));

            List<ModificationCatalogueVue> historique =
                    service.historiquePrestation(prestation.getReference());

            assertThat(historique).singleElement().satisfies(vue -> {
                assertThat(vue.champ()).isEqualTo("prixHtva");
                assertThat(vue.valeurAvant()).isEqualTo("75.00");
                assertThat(vue.valeurApres()).isEqualTo("89.00");
                assertThat(vue.horodatage()).isEqualTo(MAINTENANT);
                assertThat(vue.auteur()).isEqualTo("Paul Garage");
            });
        }
    }

    @Nested
    @DisplayName("vues back-office")
    class Vues {

        @Test
        @DisplayName("le catalogue admin expose aussi les elements inactifs")
        void exposeLesInactifs() {
            Prestation inactive = new Prestation(entretien, "VID", "Vidange",
                    new BigDecimal("75.00"), 60);
            inactive.desactiver();
            when(prestations.catalogueComplet()).thenReturn(List.of(inactive));

            List<ArticleVueAdmin> vues = service.prestationsPourAdmin();

            assertThat(vues).hasSize(1);
            assertThat(vues.get(0).actif()).isFalse();
            assertThat(vues.get(0).identifiant()).isEqualTo("VID");
        }
    }

    @Nested
    @DisplayName("suppression ou desactivation (A3, A6, RM-29)")
    class SuppressionOuDesactivation {

        private Prestation vidangePersistee() {
            Prestation prestation = new Prestation(entretien, "VID", "Vidange",
                    new BigDecimal("75.00"), 60);
            when(prestations.findByReference(prestation.getReference()))
                    .thenReturn(Optional.of(prestation));
            return prestation;
        }

        @Test
        @DisplayName("RM-29 : refuse la suppression definitive d une prestation referencee")
        void refuseLaSuppressionDUnePrestationReferencee() {
            Prestation prestation = vidangePersistee();
            when(prestations.nombreReferencesHistoriques(any())).thenReturn(3L);

            assertThatThrownBy(() -> service.supprimerDefinitivementPrestation(prestation.getReference()))
                    .isInstanceOf(SuppressionRefuseeException.class)
                    .hasMessageContaining("RM-29")
                    .hasMessageContaining("desactivation");

            // Jamais de suppression physique d une entite referencee.
            verify(prestations, never()).delete(any());
        }

        @Test
        @DisplayName("RM-29 : supprime definitivement une prestation sans aucune reference")
        void supprimeUnePrestationLibre() {
            Prestation prestation = vidangePersistee();
            when(prestations.nombreReferencesHistoriques(any())).thenReturn(0L);

            service.supprimerDefinitivementPrestation(prestation.getReference());

            verify(prestations).delete(prestation);
        }

        @Test
        @DisplayName("RM-29 : refuse la suppression definitive d une piece referencee")
        void refuseLaSuppressionDUnePieceReferencee() {
            Piece piece = new Piece(filtres, "F-001", "Filtre a huile", new BigDecimal("12.50"));
            when(pieces.findByReference(piece.getReference())).thenReturn(Optional.of(piece));
            when(pieces.nombreReferencesHistoriques(any())).thenReturn(1L);

            assertThatThrownBy(() -> service.supprimerDefinitivementPiece(piece.getReference()))
                    .isInstanceOf(SuppressionRefuseeException.class)
                    .extracting("nombreReferences").isEqualTo(1L);

            verify(pieces, never()).delete(any());
        }

        @Test
        @DisplayName("RM-29 : supprime definitivement une piece sans aucune reference")
        void supprimeUnePieceLibre() {
            Piece piece = new Piece(filtres, "F-001", "Filtre a huile", new BigDecimal("12.50"));
            when(pieces.findByReference(piece.getReference())).thenReturn(Optional.of(piece));
            when(pieces.nombreReferencesHistoriques(any())).thenReturn(0L);

            service.supprimerDefinitivementPiece(piece.getReference());

            verify(pieces).delete(piece);
        }

        @Test
        @DisplayName("RM-29 : une reference apparue pendant la suppression est traduite en refus metier")
        void traduitLaCourseEnRefusMetier() {
            Prestation prestation = vidangePersistee();
            when(prestations.nombreReferencesHistoriques(any())).thenReturn(0L);
            // La FK ON DELETE RESTRICT joue son role de seconde ligne au flush.
            doThrow(new DataIntegrityViolationException("fk_rdv_service_svc"))
                    .when(prestations).flush();

            assertThatThrownBy(() -> service.supprimerDefinitivementPrestation(prestation.getReference()))
                    .isInstanceOf(SuppressionRefuseeException.class);
        }

        @Test
        @DisplayName("RM-28 : la desactivation reste possible pour un element reference")
        void desactiveSansSupprimer() {
            Prestation prestation = vidangePersistee();

            service.desactiverPrestation(prestation.getReference());

            assertThat(prestation.isActif()).isFalse();
            assertThat(prestation.estSupprime()).isFalse();
            verify(prestations, never()).delete(any());
        }

        @Test
        @DisplayName("reactive un element desactive")
        void reactive() {
            Piece piece = new Piece(filtres, "F-001", "Filtre a huile", new BigDecimal("12.50"));
            piece.desactiver();
            when(pieces.findByReference(piece.getReference())).thenReturn(Optional.of(piece));

            service.activerPiece(piece.getReference());

            assertThat(piece.isActif()).isTrue();
        }

        @Test
        @DisplayName("le diagnostic expose le nombre de references et l action possible")
        void proposeLActionAdequate() {
            Prestation prestation = vidangePersistee();
            when(prestations.nombreReferencesHistoriques(any())).thenReturn(2L);

            PropositionSuppression proposition =
                    service.propositionSuppressionPrestation(prestation.getReference());

            assertThat(proposition.nombreReferences()).isEqualTo(2);
            assertThat(proposition.suppressionPossible()).isFalse();
            assertThat(proposition.libelle()).isEqualTo("Vidange");
            assertThat(proposition.identifiant()).isEqualTo("VID");
        }
    }

    @Nested
    @DisplayName("gestion du stock")
    class Stock {

        private Piece pieceAvecStock(int quantite) {
            Piece piece = new Piece(filtres, "F-001", "Filtre a huile", new BigDecimal("12.50"));
            piece.setQuantiteStock(quantite);
            piece.setSeuilAlerte(2);
            return piece;
        }

        @Test
        @DisplayName("reapprovisionne une piece")
        void reapprovisionne() {
            Piece piece = pieceAvecStock(3);
            when(pieces.findByReference(piece.getReference())).thenReturn(Optional.of(piece));

            service.reapprovisionner(piece.getReference(), 10);

            assertThat(piece.getQuantiteStock()).isEqualTo(13);
        }

        @Test
        @DisplayName("signale les pieces sous le seuil d alerte")
        void signaleLesPiecesEnAlerte() {
            Piece piece = pieceAvecStock(1);
            when(pieces.enAlerteDeStock()).thenReturn(List.of(piece));

            assertThat(service.piecesEnAlerteDeStock()).containsExactly(piece);
        }
    }
}
