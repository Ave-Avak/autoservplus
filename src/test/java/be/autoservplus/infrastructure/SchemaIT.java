package be.autoservplus.infrastructure;

import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Rejoue l ensemble des migrations sur un PostgreSQL neuf et verifie les elements de
 * schema que les tests unitaires ne peuvent pas voir.
 *
 * <p>Ce test est le seul a garantir que la base de production pourra etre construite
 * a partir de zero : extension, contraintes, donnees structurelles. Il tourne sous
 * Failsafe (suffixe IT) et requiert Docker.</p>
 */
@SpringBootTest
@Testcontainers
@DisplayName("Schema de base de donnees")
class SchemaIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UtilisateurRepository utilisateurs;

    @Test
    @DisplayName("installe l extension btree_gist requise par la contrainte d exclusion")
    void installeBtreeGist() {
        Integer nombre = jdbc.queryForObject(
                "SELECT count(*) FROM pg_extension WHERE extname = 'btree_gist'", Integer.class);
        assertThat(nombre).isEqualTo(1);
    }

    @Test
    @DisplayName("cree la ligne unique de parametres d atelier")
    void creeLesParametres() {
        Integer nombre = jdbc.queryForObject("SELECT count(*) FROM parametre_atelier", Integer.class);
        assertThat(nombre).isEqualTo(1);
    }

    @Test
    @DisplayName("pose la contrainte d exclusion sur les rendez-vous")
    void poseLaContrainteDExclusion() {
        String type = jdbc.queryForObject(
                "SELECT contype FROM pg_constraint WHERE conname = 'ex_rdv_poste_intervalle'", String.class);
        assertThat(type).isEqualTo("x");
    }

    @Test
    @DisplayName("le seed insere au moins un poste d atelier actif")
    void leSeedPostesEstApplique() {
        // V17 insere 3 postes. Sans au moins un poste actif, la capacite de
        // l atelier est nulle et le calcul de disponibilite renvoie 0 creneau
        // partout : la reservation devient impossible sans message d erreur.
        // Ce test localise vite le manque quand le check de chaine (DisponibiliteIT)
        // tombe rouge.
        Integer nombre = jdbc.queryForObject(
                "SELECT count(*) FROM poste_atelier WHERE actif = TRUE AND deleted_at IS NULL",
                Integer.class);
        assertThat(nombre)
                .as("Sans poste actif, la reservation ne peut proposer aucun creneau")
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("le seed catalogue insere au moins une prestation par categorie SERVICE")
    void leSeedCatalogueEstApplique() {
        // V16 insere le catalogue de demo (9 prestations sur 6 categories SERVICE).
        // Sans ce test, la suppression accidentelle de V16 ou une regression de FK
        // resterait invisible jusqu au premier essai de reservation.
        Integer nombreServices = jdbc.queryForObject(
                "SELECT count(*) FROM service WHERE actif = TRUE", Integer.class);
        assertThat(nombreServices)
                .as("Le seed doit inserer au moins 6 prestations actives (une par categorie SERVICE)")
                .isGreaterThanOrEqualTo(6);

        Integer categoriesCouvertes = jdbc.queryForObject(
                "SELECT count(DISTINCT s.categorie_id) FROM service s WHERE s.actif = TRUE", Integer.class);
        assertThat(categoriesCouvertes)
                .as("Chaque categorie SERVICE doit avoir au moins une prestation")
                .isGreaterThanOrEqualTo(6);
    }

    @Test
    @DisplayName("le compte admin de seed est connectable avec le mot de passe documente")
    void leCompteAdminDeSeedEstConnectable() {
        // V10 insere l admin, V15 corrige son hash. Sans le test, un desaccord entre
        // le hash et le mot de passe documente (comme c etait le cas avant V15) reste
        // invisible jusqu au premier essai de connexion.
        Utilisateur admin = utilisateurs.findByEmailIgnoreCase("admin@autoservplus.be")
                .orElseThrow(() -> new AssertionError(
                        "Le seed V10 doit inserer un compte admin@autoservplus.be"));

        assertThat(new BCryptPasswordEncoder(12).matches("ChangezMoi2026!", admin.getMotDePasseHache()))
                .as("Le hash BCrypt du seed doit correspondre au mot de passe documente \"ChangezMoi2026!\"")
                .isTrue();
    }

    @Test
    @DisplayName("ne contient plus la table des creneaux generes")
    void supprimeLesCreneaux() {
        Integer nombre = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_name = 'creneau_horaire'",
                Integer.class);
        assertThat(nombre).isZero();
    }

    // --- RM-15 : le devis initial est structurellement obligatoire (V20 + V21) --------

    @Test
    @DisplayName("aucune intervention ne reste sans devis initial apres les migrations")
    void aucunDevisInitialNull() {
        Integer sansDevis = jdbc.queryForObject(
                "SELECT count(*) FROM intervention WHERE montant_devis_htva IS NULL", Integer.class);
        assertThat(sansDevis)
                .as("Le backfill V20 puis le NOT NULL de V21 doivent ne laisser aucun NULL")
                .isZero();
    }

    @Test
    @DisplayName("montant_devis_htva est NOT NULL : l invariant RM-15 est porte par la base")
    void devisInitialObligatoire() {
        String nullable = jdbc.queryForObject(
                "SELECT is_nullable FROM information_schema.columns "
                        + "WHERE table_name = 'intervention' AND column_name = 'montant_devis_htva'",
                String.class);
        assertThat(nullable)
                .as("Sans NOT NULL, une intervention pourrait exister sans base de comparaison")
                .isEqualTo("NO");
    }

    /**
     * Verifie que la colonne porte bien du HORS TVA. Le montant insere est la somme
     * {@code prix_unitaire_htva * quantite} des lignes ; le test echouerait si une
     * evolution y rangeait un TVAC, puisque le total TVAC (121 % au taux belge normal)
     * s ecarte necessairement de cette somme.
     */
    @Test
    @Transactional
    @DisplayName("le devis initial stocke est bien du HTVA, pas du TVAC")
    void devisInitialEstDuHorsTva() {
        Long vehiculeId = jdbc.queryForObject(
                "INSERT INTO vehicule (membre_id, plaque, marque, modele, motorisation) "
                        + "SELECT id, 'IT-SCH-1', 'VW', 'Golf', 'DIESEL' FROM utilisateur "
                        + "WHERE email = 'admin@autoservplus.be' RETURNING id", Long.class);
        Long interventionId = jdbc.queryForObject(
                "INSERT INTO intervention (numero, vehicule_id, statut, montant_devis_htva) "
                        + "VALUES ('INT-SCHEMA-IT', ?, 'PLANIFIEE', 138.00) RETURNING id",
                Long.class, vehiculeId);
        Long serviceId = jdbc.queryForObject("SELECT id FROM service LIMIT 1", Long.class);
        jdbc.update("INSERT INTO ligne_intervention "
                        + "(intervention_id, service_id, libelle_fige, quantite, prix_unitaire_htva, taux_tva) "
                        + "VALUES (?, ?, 'Vidange', 1, 49.00, 21.00), (?, ?, 'Plaquettes', 1, 89.00, 21.00)",
                interventionId, serviceId, interventionId, serviceId);

        java.math.BigDecimal devis = jdbc.queryForObject(
                "SELECT montant_devis_htva FROM intervention WHERE id = ?",
                java.math.BigDecimal.class, interventionId);
        java.math.BigDecimal sommeHt = jdbc.queryForObject(
                "SELECT SUM(prix_unitaire_htva * quantite) FROM ligne_intervention WHERE intervention_id = ?",
                java.math.BigDecimal.class, interventionId);
        java.math.BigDecimal sommeTvac = jdbc.queryForObject(
                "SELECT SUM(prix_unitaire_htva * quantite * (1 + taux_tva / 100)) "
                        + "FROM ligne_intervention WHERE intervention_id = ?",
                java.math.BigDecimal.class, interventionId);

        assertThat(devis).isEqualByComparingTo(sommeHt);
        assertThat(devis)
                .as("Un devis egal au TVAC decalerait le seuil RM-15 de 21 %%")
                .isNotEqualByComparingTo(sommeTvac);
    }

    @Test
    @Transactional
    @DisplayName("une intervention sans devis initial est rejetee par la base")
    void interventionSansDevisRejetee() {
        Long vehiculeId = jdbc.queryForObject(
                "INSERT INTO vehicule (membre_id, plaque, marque, modele, motorisation) "
                        + "SELECT id, 'IT-SCH-2', 'VW', 'Polo', 'ESSENCE' FROM utilisateur "
                        + "WHERE email = 'admin@autoservplus.be' RETURNING id", Long.class);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO intervention (numero, vehicule_id, statut) "
                        + "VALUES ('INT-SANS-DEVIS', ?, 'PLANIFIEE')", vehiculeId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("montant_devis_htva");
    }

    /**
     * V22 a remplace le couple validee/refusee par le seul {@code accord_membre}
     * nullable du dictionnaire. L etat « acceptee ET refusee » n a plus besoin d etre
     * interdit : il est devenu inexprimable. Ce qui reste a verrouiller est l autre
     * incoherence, celle que deux booleens ne savaient pas dire — une ligne du devis
     * initial qui porterait un accord alors qu on ne lui en a jamais demande.
     */
    @Test
    @Transactional
    @DisplayName("une ligne du devis initial ne peut porter d accord (ck_ligne_interv_accord)")
    void accordSurLigneDuDevisInitialRejete() {
        Long vehiculeId = jdbc.queryForObject(
                "INSERT INTO vehicule (membre_id, plaque, marque, modele, motorisation) "
                        + "SELECT id, 'IT-SCH-3', 'VW', 'Up', 'ESSENCE' FROM utilisateur "
                        + "WHERE email = 'admin@autoservplus.be' RETURNING id", Long.class);
        Long interventionId = jdbc.queryForObject(
                "INSERT INTO intervention (numero, vehicule_id, statut, montant_devis_htva) "
                        + "VALUES ('INT-SCHEMA-IT-2', ?, 'PLANIFIEE', 0) RETURNING id",
                Long.class, vehiculeId);
        Long serviceId = jdbc.queryForObject("SELECT id FROM service LIMIT 1", Long.class);

        // Les quatre combinaisons legitimes de l encodage passent. Elles s inserent
        // AVANT la violation : une contrainte violee avorte la transaction PostgreSQL,
        // et tout ordre suivant y serait rejete en 25P02 sans rien prouver.
        jdbc.update("INSERT INTO ligne_intervention (intervention_id, service_id, libelle_fige, "
                        + "quantite, prix_unitaire_htva, taux_tva, ajoutee_en_cours, accord_membre) "
                        + "VALUES (?, ?, 'Devis initial', 1, 10.00, 21.00, false, NULL)",
                interventionId, serviceId);
        for (String accord : new String[] {"NULL", "true", "false"}) {
            jdbc.update("INSERT INTO ligne_intervention (intervention_id, service_id, libelle_fige, "
                            + "quantite, prix_unitaire_htva, taux_tva, ajoutee_en_cours, accord_membre) "
                            + "VALUES (?, ?, 'Ajout', 1, 10.00, 21.00, true, " + accord + ")",
                    interventionId, serviceId);
        }

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO ligne_intervention (intervention_id, service_id, libelle_fige, "
                        + "quantite, prix_unitaire_htva, taux_tva, ajoutee_en_cours, accord_membre) "
                        + "VALUES (?, ?, 'Incoherente', 1, 10.00, 21.00, false, true)",
                interventionId, serviceId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_ligne_interv_accord");
    }

    // Les trois tests suivants operent sur jour_semaine = 7 (dimanche), jamais peuple
    // par le seed V10. @Transactional garantit que les INSERT sont rollback en fin de
    // test, ce qui evite d avoir a nettoyer manuellement et preserve l isolation.

    @Test
    @Transactional
    @DisplayName("rejette deux plages d ouverture chevauchantes le meme jour")
    void rejetteChevauchementPlageOuverture() {
        jdbc.update("INSERT INTO plage_ouverture (jour_semaine, heure_debut, heure_fin) VALUES (7, '08:00', '12:00')");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO plage_ouverture (jour_semaine, heure_debut, heure_fin) VALUES (7, '09:00', '11:00')"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ex_plage_ouverture_chevauchement");
    }

    @Test
    @Transactional
    @DisplayName("accepte deux plages adjacentes le meme jour grace a la borne demi-ouverte")
    void accepteAdjacencesPlageOuverture() {
        jdbc.update("INSERT INTO plage_ouverture (jour_semaine, heure_debut, heure_fin) VALUES (7, '12:00', '13:00')");
        jdbc.update("INSERT INTO plage_ouverture (jour_semaine, heure_debut, heure_fin) VALUES (7, '13:00', '17:00')");

        Integer nombre = jdbc.queryForObject(
                "SELECT count(*) FROM plage_ouverture WHERE jour_semaine = 7", Integer.class);
        assertThat(nombre).isEqualTo(2);
    }

    @Test
    @Transactional
    @DisplayName("ignore les plages soft-deletees dans la contrainte d exclusion")
    void ignoreSoftDeleteePlageOuverture() {
        jdbc.update("INSERT INTO plage_ouverture (jour_semaine, heure_debut, heure_fin, deleted_at) " +
                "VALUES (7, '08:00', '12:00', now())");
        // Une plage active peut occuper l intervalle libere par la suppression logique.
        jdbc.update("INSERT INTO plage_ouverture (jour_semaine, heure_debut, heure_fin) VALUES (7, '09:00', '11:00')");

        Integer actives = jdbc.queryForObject(
                "SELECT count(*) FROM plage_ouverture WHERE jour_semaine = 7 AND deleted_at IS NULL",
                Integer.class);
        assertThat(actives).isEqualTo(1);
    }
}