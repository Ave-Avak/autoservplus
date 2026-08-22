package be.autoservplus.catalogue.service;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PieceRepository;
import be.autoservplus.catalogue.repository.PrestationRepository;
import be.autoservplus.catalogue.service.dto.ArticleVue;
import be.autoservplus.catalogue.service.dto.DonneesPiece;
import be.autoservplus.catalogue.service.dto.DonneesPrestation;
import be.autoservplus.catalogue.service.dto.ModificationCatalogueVue;
import be.autoservplus.catalogue.service.dto.PropositionSuppression;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.intervention.service.InterventionService;
import be.autoservplus.reservation.domain.Motorisation;
import be.autoservplus.reservation.domain.PosteAtelier;
import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.domain.Vehicule;
import be.autoservplus.reservation.repository.PosteAtelierRepository;
import be.autoservplus.reservation.repository.RdvRepository;
import be.autoservplus.reservation.repository.VehiculeRepository;
import be.autoservplus.vente.service.CommandeService;
import be.autoservplus.vente.service.PanierService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * RM-29 et RM-28 contre un vrai PostgreSQL 16 : les references d historique sont
 * de vraies lignes {@code rdv_service} et {@code ligne_panier}, comptees par les
 * requetes natives des repositories — un JPQL mocke ne prouverait rien ici.
 *
 * <p>Prouve aussi les invariants transverses du lot : la suppression definitive est
 * bien <b>physique</b> (verification JDBC, car {@code @SQLRestriction} masquerait un
 * simple soft delete), une modification de prix catalogue ne reecrit ni les lignes
 * figees d une commande deja passee (A5) ni celles d une intervention deja ouverte
 * (A2), et l historisation des modifications (A2, A5) est ecrite en base, requetable
 * et triee.</p>
 */
@SpringBootTest
@Testcontainers
@Transactional
@WithMockUser(username = "admin@garage.be", roles = "ADMINISTRATEUR")
@DisplayName("AdminCatalogueService (integration)")
class AdminCatalogueServiceIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private AdminCatalogueService admin;
    @Autowired private CatalogueService cataloguePublic;
    @Autowired private CategorieRepository categories;
    @Autowired private PrestationRepository prestations;
    @Autowired private PieceRepository pieces;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private VehiculeRepository vehicules;
    @Autowired private PosteAtelierRepository postes;
    @Autowired private RdvRepository rdvs;
    @Autowired private PanierService panierService;
    @Autowired private CommandeService commandeService;
    @Autowired private InterventionService interventions;
    @Autowired private JdbcTemplate jdbc;

    private Categorie entretien;
    private Categorie freinage;
    private Prestation vidange;
    private Piece plaquettes;
    private Utilisateur marie;

    @BeforeEach
    void setUp() {
        entretien = categories.save(new Categorie("IT-CAT-ENT", "Entretien", TypeCategorie.SERVICE));
        freinage = categories.save(new Categorie("IT-CAT-FRE", "Freinage", TypeCategorie.PIECE));
        vidange = prestations.save(new Prestation(entretien, "IT-CAT-VID", "Vidange",
                new BigDecimal("49.00"), 30));
        plaquettes = new Piece(freinage, "IT-CAT-001", "Plaquettes avant", new BigDecimal("19.99"));
        plaquettes.setQuantiteStock(10);
        plaquettes = pieces.save(plaquettes);
        marie = utilisateurs.save(new Utilisateur(
                "marie@exemple.be", "$2a$12$h", "Dupont", "Marie", TypeUtilisateur.MEMBRE));
        // Le compte derriere le @WithMockUser de classe : sans lui en base, le « qui »
        // du journal des modifications resterait nul faute de cible pour la FK.
        utilisateurs.save(new Utilisateur(
                "admin@garage.be", "$2a$12$h", "Garage", "Paul", TypeUtilisateur.ADMINISTRATEUR));
    }

    private Rdv referencerLaPrestationParUnRdv() {
        Vehicule golf = vehicules.save(new Vehicule(marie, "1-ABC-123", "VW", "Golf", Motorisation.DIESEL));
        PosteAtelier pont = postes.save(new PosteAtelier("Pont de test"));
        return rdvs.saveAndFlush(new Rdv("RDV-IT-CAT-0001", marie, golf, pont,
                Instant.parse("2026-09-14T08:00:00Z"), Duration.ofMinutes(30),
                List.of(vidange), null));
    }

    /** Les donnees de la vidange du setUp, avec les seules valeurs passees en parametre. */
    private DonneesPrestation vidangeAvec(String libelle, String prixHtva, int dureeMinutes) {
        return new DonneesPrestation("IT-CAT-ENT", "IT-CAT-VID", libelle, null,
                new BigDecimal(prixHtva), new BigDecimal("21.00"), dureeMinutes, true);
    }

    @Test
    @WithMockUser(username = "membre@exemple.be", roles = "MEMBRE")
    @DisplayName("@PreAuthorize rejette un utilisateur sans role ADMINISTRATEUR")
    void rejetteNonAdmin() {
        // @WithMockUser de methode surcharge celui de la classe : le rejet vient du
        // @PreAuthorize(hasRole('ADMINISTRATEUR')) de classe sur AdminCatalogueService.
        assertThatThrownBy(() -> admin.prestationsPourAdmin())
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> admin.supprimerDefinitivementPiece(plaquettes.getReference()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("RM-29 : une prestation reservee ne peut etre que desactivee, et reste lisible")
    void prestationReferenceeSeulementDesactivable() {
        referencerLaPrestationParUnRdv();

        PropositionSuppression proposition = admin.propositionSuppressionPrestation(vidange.getReference());
        assertThat(proposition.suppressionPossible()).isFalse();
        assertThat(proposition.nombreReferences()).isEqualTo(1);

        assertThatThrownBy(() -> admin.supprimerDefinitivementPrestation(vidange.getReference()))
                .isInstanceOf(SuppressionRefuseeException.class)
                .hasMessageContaining("RM-29");

        // RM-28 : la desactivation, elle, passe — et la prestation disparait du
        // catalogue public tout en restant chargee par reference pour les historiques.
        admin.desactiverPrestation(vidange.getReference());
        assertThat(cataloguePublic.prestationsActives())
                .extracting(ArticleVue::reference)
                .doesNotContain(vidange.getReference());
        assertThat(prestations.findByReference(vidange.getReference())).isPresent();
    }

    @Test
    @DisplayName("RM-29 : une prestation jamais referencee est supprimee physiquement")
    void prestationLibreSupprimeePhysiquement() {
        assertThat(admin.propositionSuppressionPrestation(vidange.getReference()).suppressionPossible())
                .isTrue();

        admin.supprimerDefinitivementPrestation(vidange.getReference());

        // Preuve de suppression PHYSIQUE : le decompte JDBC contourne @SQLRestriction,
        // qui masquerait aussi bien un simple soft delete.
        Integer lignesEnBase = jdbc.queryForObject(
                "SELECT count(*) FROM service WHERE reference = ?", Integer.class,
                vidange.getReference());
        assertThat(lignesEnBase).isZero();
    }

    @Test
    @DisplayName("RM-29 : une piece presente dans un panier ne peut etre que desactivee")
    void pieceAuPanierSeulementDesactivable() {
        panierService.ajouterPiece("marie@exemple.be", plaquettes.getReference(), 2);

        PropositionSuppression proposition = admin.propositionSuppressionPiece(plaquettes.getReference());
        assertThat(proposition.suppressionPossible()).isFalse();

        assertThatThrownBy(() -> admin.supprimerDefinitivementPiece(plaquettes.getReference()))
                .isInstanceOf(SuppressionRefuseeException.class);

        admin.desactiverPiece(plaquettes.getReference());
        assertThat(cataloguePublic.piecesActives())
                .extracting(ArticleVue::reference)
                .doesNotContain(plaquettes.getReference());
        assertThat(pieces.findByReference(plaquettes.getReference())).isPresent();
    }

    @Test
    @DisplayName("RM-29 : une piece jamais referencee est supprimee physiquement")
    void pieceLibreSupprimeePhysiquement() {
        admin.supprimerDefinitivementPiece(plaquettes.getReference());

        Integer lignesEnBase = jdbc.queryForObject(
                "SELECT count(*) FROM piece WHERE reference = ?", Integer.class,
                plaquettes.getReference());
        assertThat(lignesEnBase).isZero();
    }

    @Test
    @DisplayName("A5 : modifier le prix catalogue ne reecrit pas les lignes d une commande passee")
    void modificationSansEffetSurLaCommandeEmise() {
        panierService.ajouterPiece("marie@exemple.be", plaquettes.getReference(), 2);
        commandeService.passerCommande("marie@exemple.be", true, null);

        admin.modifierPiece(plaquettes.getReference(), new DonneesPiece(
                "IT-CAT-FRE", "IT-CAT-001", "Plaquettes avant renforcees", "Brembo", null,
                new BigDecimal("25.00"), new BigDecimal("21.00"), 8, 2, true));
        pieces.flush();

        // La ligne de commande garde le prix et le libelle figes a l ajout au panier.
        assertThat(jdbc.queryForObject(
                "SELECT prix_unitaire_htva FROM ligne_panier WHERE piece_id = ?",
                BigDecimal.class, plaquettes.getId()))
                .isEqualByComparingTo(new BigDecimal("19.99"));
        assertThat(jdbc.queryForObject(
                "SELECT libelle_fige FROM ligne_panier WHERE piece_id = ?",
                String.class, plaquettes.getId()))
                .isEqualTo("Plaquettes avant");

        // Le catalogue, lui, est bien a jour.
        assertThat(pieces.findByReference(plaquettes.getReference()).orElseThrow().getPrixHtva())
                .isEqualByComparingTo(new BigDecimal("25.00"));
    }

    @Test
    @DisplayName("A2 : modifier le catalogue ne reecrit pas les lignes d une intervention ouverte")
    void modificationSansEffetSurLInterventionEmise() {
        Rdv rdv = referencerLaPrestationParUnRdv();
        interventions.creerDepuisRdv(rdv);

        admin.modifierPrestation(vidange.getReference(), vidangeAvec("Vidange complete", "79.00", 45));
        prestations.flush();

        // La ligne d intervention garde le prix et le libelle figes a l ouverture du
        // dossier : le catalogue du jour n a pas voix au chapitre sur un devis emis.
        assertThat(jdbc.queryForObject(
                "SELECT prix_unitaire_htva FROM ligne_intervention WHERE service_id = ?",
                BigDecimal.class, vidange.getId()))
                .isEqualByComparingTo(new BigDecimal("49.00"));
        assertThat(jdbc.queryForObject(
                "SELECT libelle_fige FROM ligne_intervention WHERE service_id = ?",
                String.class, vidange.getId()))
                .isEqualTo("Vidange");
        // Le devis initial, fige a la creation de l intervention, ne bouge pas non plus.
        assertThat(jdbc.queryForObject(
                "SELECT montant_devis_htva FROM intervention WHERE rdv_id = ?",
                BigDecimal.class, rdv.getId()))
                .isEqualByComparingTo(new BigDecimal("49.00"));

        // Le catalogue, lui, est bien a jour : c est la seule chose qui a change.
        assertThat(prestations.findByReference(vidange.getReference()).orElseThrow().getPrixHtva())
                .isEqualByComparingTo(new BigDecimal("79.00"));
    }

    @Test
    @DisplayName("A2 : l historique est requetable, du plus recent au plus ancien, avec son auteur")
    void historiqueRequetableEtTrie() {
        admin.modifierPrestation(vidange.getReference(), vidangeAvec("Vidange", "59.00", 30));
        admin.modifierPrestation(vidange.getReference(), vidangeAvec("Vidange express", "59.00", 30));

        List<ModificationCatalogueVue> historique = admin.historiquePrestation(vidange.getReference());

        assertThat(historique)
                .extracting(ModificationCatalogueVue::champ,
                        ModificationCatalogueVue::valeurAvant,
                        ModificationCatalogueVue::valeurApres)
                .as("Le plus recent en tete : le renommage, puis le changement de prix")
                .containsExactly(
                        tuple("libelle", "Vidange", "Vidange express"),
                        tuple("prixHtva", "49.00", "59.00"));

        // « Qui » : resolu depuis le contexte de securite du @WithMockUser de classe,
        // puis remonte a l entite pour poser une vraie cle etrangere.
        assertThat(historique).extracting(ModificationCatalogueVue::auteur)
                .containsOnly("Paul Garage");
        assertThat(historique).allSatisfy(vue -> assertThat(vue.horodatage()).isNotNull());
    }

    @Test
    @DisplayName("A5 : l historique d une piece est bien ecrit en base, une ligne par champ")
    void historiqueDeLaPieceEnBase() {
        admin.modifierPiece(plaquettes.getReference(), new DonneesPiece(
                "IT-CAT-FRE", "IT-CAT-001", "Plaquettes avant", "Brembo", null,
                new BigDecimal("24.99"), new BigDecimal("21.00"), 10, 0, true));
        pieces.flush();

        // Lecture JDBC : les lignes existent vraiment en base, pas seulement dans le
        // contexte de persistance. Seuls la marque et le prix ont change.
        assertThat(jdbc.queryForList(
                "SELECT champ_modifie, valeur_avant, valeur_apres, auteur_id"
                        + " FROM historique_modification_catalogue"
                        + " WHERE type_entite = 'PIECE' AND entite_id = ?"
                        + " ORDER BY champ_modifie", plaquettes.getId()))
                .extracting(ligne -> ligne.get("champ_modifie"),
                        ligne -> ligne.get("valeur_avant"),
                        ligne -> ligne.get("valeur_apres"))
                .containsExactly(
                        tuple("marque", null, "Brembo"),
                        tuple("prixHtva", "19.99", "24.99"));

        assertThat(admin.historiquePiece(plaquettes.getReference())).hasSize(2);
    }

    @Test
    @DisplayName("le journal survit a la suppression physique de ce qu il trace (A3, A6)")
    void journalSurvitALaSuppression() {
        admin.modifierPiece(plaquettes.getReference(), new DonneesPiece(
                "IT-CAT-FRE", "IT-CAT-001", "Plaquettes renforcees", null, null,
                new BigDecimal("19.99"), new BigDecimal("21.00"), 10, 0, true));
        Long idSupprime = plaquettes.getId();

        // RM-29 : jamais referencee, la piece part physiquement. L absence de FK sur
        // entite_id est ce qui permet a la trace de lui survivre.
        admin.supprimerDefinitivementPiece(plaquettes.getReference());

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM historique_modification_catalogue WHERE entite_id = ?"
                        + " AND type_entite = 'PIECE'", Integer.class, idSupprime))
                .isEqualTo(1);
    }
}
