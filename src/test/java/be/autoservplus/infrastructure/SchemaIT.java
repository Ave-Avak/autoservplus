package be.autoservplus.infrastructure;

import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

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

    // --- F23 : suppression de compte par anonymisation (V28) --------------------------

    @Test
    @DisplayName("anonymise_le existe et est NULLABLE : c'est un marqueur, pas un etat force")
    void marqueurDAnonymisation() {
        String nullable = jdbc.queryForObject(
                "SELECT is_nullable FROM information_schema.columns "
                        + "WHERE table_name = 'utilisateur' AND column_name = 'anonymise_le'",
                String.class);
        assertThat(nullable)
                .as("Un compte vivant n a pas de date d anonymisation")
                .isEqualTo("YES");
    }

    @Test
    @DisplayName("SUPPRIME etait deja admis par ck_utilisateur_statut : rien n a ete elargi")
    void statutSupprimeDejaAdmis() {
        // Verification faite avant d ecrire V28 : le socle V1 portait deja la valeur,
        // il n y avait pas de valeur ANONYMISE a inventer.
        assertThat(jdbc.queryForObject(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint "
                        + "WHERE conname = 'ck_utilisateur_statut'", String.class))
                .contains("SUPPRIME");
    }

    @Test
    @DisplayName("la liste des colonnes balayees couvre TOUT le schema : une table oubliee casse la build")
    void listeDesTracesExhaustive() {
        // C est la garde anti-peremption de la liste enumeree. Elle remplace la
        // derivation au runtime : celle-ci couvrait tout automatiquement mais ne
        // s enoncait pas, alors qu un effacement legal doit pouvoir dire exactement
        // ce qu il ecrase. Ici la liste est ecrite noir sur blanc dans V28, et ce
        // test echoue le jour ou une table arrive sans y etre ajoutee — au moment ou
        // l on peut encore agir, pas au moment de l effacement.
        List<String> oubliees = jdbc.queryForList("""
                SELECT c.table_name || '.' || c.column_name
                FROM information_schema.columns c
                WHERE c.table_schema = 'public'
                  AND c.column_name IN ('created_by', 'updated_by')
                  AND NOT EXISTS (
                      SELECT 1 FROM fn_tables_traces_audit() d
                      WHERE d.nom_table = c.table_name AND d.nom_colonne = c.column_name)
                ORDER BY 1
                """, String.class);

        assertThat(oubliees)
                .as("Colonnes d audit absentes de fn_tables_traces_audit() : "
                        + "les ajouter a V28, sinon l adresse du membre y survivrait")
                .isEmpty();
    }

    @Test
    @DisplayName("la liste ne declare aucune colonne fantome")
    void listeSansColonneFantome() {
        // L inverse du test precedent : une entree qui ne correspond a rien ferait
        // echouer le balayage a l execution, c est-a-dire pendant une suppression de
        // compte — le pire moment pour decouvrir une faute de frappe.
        List<String> fantomes = jdbc.queryForList("""
                SELECT d.nom_table || '.' || d.nom_colonne
                FROM fn_tables_traces_audit() d
                WHERE NOT EXISTS (
                    SELECT 1 FROM information_schema.columns c
                    WHERE c.table_schema = 'public'
                      AND c.table_name = d.nom_table AND c.column_name = d.nom_colonne)
                ORDER BY 1
                """, String.class);

        assertThat(fantomes).isEmpty();
    }

    @Test
    @Transactional
    @DisplayName("le balayage remplace l'adresse par le jeton, pas par NULL")
    void balayageDesTracesDAudit() {
        jdbc.update("INSERT INTO categorie (code, libelle, type, created_by, updated_by) "
                + "VALUES ('IT-ANON', 'Test', 'PIECE', 'cible@exemple.be', 'cible@exemple.be')");

        Integer modifiees = jdbc.queryForObject(
                "SELECT fn_anonymiser_traces_audit('cible@exemple.be', 'anonyme-0@supprime.invalid')",
                Integer.class);

        assertThat(modifiees).isGreaterThanOrEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM categorie WHERE created_by = 'cible@exemple.be' "
                        + "OR updated_by = 'cible@exemple.be'", Integer.class)).isZero();
        // Le jeton et non NULL : les colonnes sont nullables, mais savoir QUE la ligne
        // a ete creee par un compte desormais anonymise reste de la tracabilite.
        assertThat(jdbc.queryForObject(
                "SELECT created_by FROM categorie WHERE code = 'IT-ANON'", String.class))
                .isEqualTo("anonyme-0@supprime.invalid");
    }

    @Test
    @Transactional
    @DisplayName("sur une facture, le balayage passe mais le trigger protege toujours le comptable")
    void balayageCompatibleAvecLImmuabilite() {
        // La reponse a « vous disiez ne jamais toucher une facture » : on ne touche
        // que des metadonnees techniques d audit. Le contenu comptable, lui, reste
        // aussi verrouille qu avant — les deux assertions le prouvent cote a cote.
        Long membreId = jdbc.queryForObject(
                "SELECT id FROM utilisateur WHERE email = 'admin@autoservplus.be'", Long.class);
        Long commandeId = jdbc.queryForObject("""
                INSERT INTO commande (numero, membre_id, statut, montant_htva, montant_tva, montant_tvac)
                VALUES ('CMD-IT-ANON', ?, 'PAYEE', 10.00, 2.10, 12.10) RETURNING id
                """, Long.class, membreId);
        Long factureId = jdbc.queryForObject("""
                INSERT INTO facture (numero, exercice, sequence_annuelle, commande_id, membre_id,
                                     montant_htva, montant_tva, montant_tvac, created_by, updated_by)
                VALUES ('2026-9001', 2026, 9001, ?, ?, 10.00, 2.10, 12.10,
                        'cible@exemple.be', 'cible@exemple.be') RETURNING id
                """, Long.class, commandeId, membreId);

        jdbc.queryForObject(
                "SELECT fn_anonymiser_traces_audit('cible@exemple.be', 'anonyme-0@supprime.invalid')",
                Integer.class);

        // La trace d audit a bien ete anonymisee...
        assertThat(jdbc.queryForObject(
                "SELECT created_by FROM facture WHERE id = ?", String.class, factureId))
                .isEqualTo("anonyme-0@supprime.invalid");
        // ... et le montant, lui, reste intouchable.
        assertThat(jdbc.queryForObject(
                "SELECT montant_tvac FROM facture WHERE id = ?",
                java.math.BigDecimal.class, factureId)).isEqualByComparingTo("12.10");
        // Le trigger leve un RAISE EXCEPTION plpgsql (SQLSTATE P0001), que Spring ne
        // classe pas en violation d integrite comme un CHECK : c est le message qui
        // fait foi, et il vient bien de fn_facture_immuable.
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE facture SET montant_tvac = 99.99 WHERE id = ?", factureId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("immuable");
    }

    @Test
    @DisplayName("le balayage ne fait rien sur une adresse absente ou identique au jeton")
    void balayageSansEffet() {
        assertThat(jdbc.queryForObject(
                "SELECT fn_anonymiser_traces_audit('inconnu@exemple.be', 'jeton')", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject(
                "SELECT fn_anonymiser_traces_audit('meme', 'meme')", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject(
                "SELECT fn_anonymiser_traces_audit(NULL, 'jeton')", Integer.class))
                .isZero();
    }

    // --- F30 : retractation et note de credit (V27) -----------------------------------

    @Test
    @DisplayName("la note de credit est immuable en base, comme la facture (tg_avoir_immuable)")
    void avoirImmuable() {
        // Le socle protegeait la facture et laissait l avoir nu. Une note de credit
        // est pourtant un document comptable au meme titre : sa correction ne passe
        // pas davantage par un UPDATE.
        Integer triggers = jdbc.queryForObject(
                "SELECT count(*) FROM pg_trigger WHERE tgname = 'tg_avoir_immuable'", Integer.class);
        assertThat(triggers).isEqualTo(1);
    }

    @Test
    @DisplayName("une facture porte au plus un avoir (uq_avoir_facture, perimetre V1)")
    void unSeulAvoirParFacture() {
        // L unicite dit aussi le perimetre : l annulation est TOTALE en V1.
        // L annulation partielle par ligne (V2) devra lever cet index.
        Boolean unique = jdbc.queryForObject(
                "SELECT indisunique FROM pg_index i JOIN pg_class c ON c.oid = i.indexrelid "
                        + "WHERE c.relname = 'uq_avoir_facture'", Boolean.class);
        assertThat(unique).isTrue();
        // L index simple du socle a bien ete remplace, pas double.
        Integer ancien = jdbc.queryForObject(
                "SELECT count(*) FROM pg_class WHERE relname = 'ix_avoir_facture'", Integer.class);
        assertThat(ancien).isZero();
    }

    @Test
    @DisplayName("le compteur d'avoirs existe et la sequence V9 est marquee inutilisee")
    void compteurAvoirs() {
        // Une sequence PostgreSQL laisse des trous au rollback : disqualifiant pour
        // un document rectificatif comme pour la facture (AR n°1, art. 5 et 12).
        Integer table = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_name = 'compteur_avoir'",
                Integer.class);
        assertThat(table).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT obj_description('seq_numero_avoir'::regclass, 'pg_class')", String.class))
                .contains("INUTILISEE");
    }

    @Test
    @Transactional
    @DisplayName("une seule demande d'annulation EN_ATTENTE par commande")
    void uneSeuleDemandeEnAttenteParCommande() {
        Long commandeId = commandeDeTest("CMD-SCHEMA-IT-1");

        jdbc.update("INSERT INTO demande_annulation (commande_id, statut) VALUES (?, 'EN_ATTENTE')",
                commandeId);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO demande_annulation (commande_id, statut) VALUES (?, 'EN_ATTENTE')",
                commandeId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_demande_annulation_en_attente");
    }

    @Test
    @Transactional
    @DisplayName("une demande tranchee porte toujours son decideur et sa date")
    void decisionTracee() {
        Long commandeId = commandeDeTest("CMD-SCHEMA-IT-2");

        // L etat « validee par personne » doit etre inexprimable, pas surveille.
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO demande_annulation (commande_id, statut) VALUES (?, 'REFUSEE')",
                commandeId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_demande_annulation_decision");
    }

    @Test
    @Transactional
    @DisplayName("une demande validee porte toujours son avoir, une autre n'en porte jamais")
    void avoirReserveALaValidation() {
        Long commandeId = commandeDeTest("CMD-SCHEMA-IT-3");
        Long adminId = jdbc.queryForObject(
                "SELECT id FROM utilisateur WHERE email = 'admin@autoservplus.be'", Long.class);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO demande_annulation (commande_id, statut, decide_par, decide_le) "
                        + "VALUES (?, 'VALIDEE', ?, now())", commandeId, adminId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_demande_annulation_avoir");
    }

    /** Commande PAYEE minimale, de quoi accrocher une demande d annulation. */
    private Long commandeDeTest(String numero) {
        return jdbc.queryForObject(
                "INSERT INTO commande (numero, membre_id, statut, montant_htva, montant_tva, montant_tvac) "
                        + "SELECT ?, id, 'PAYEE', 10.00, 2.10, 12.10 FROM utilisateur "
                        + "WHERE email = 'admin@autoservplus.be' RETURNING id",
                Long.class, numero);
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