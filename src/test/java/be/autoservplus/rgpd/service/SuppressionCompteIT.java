package be.autoservplus.rgpd.service;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PieceRepository;
import be.autoservplus.facturation.domain.Facture;
import be.autoservplus.facturation.service.FactureService;
import be.autoservplus.facturation.service.PdfFactureService;
import be.autoservplus.identite.domain.Langue;
import be.autoservplus.identite.domain.StatutUtilisateur;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.reservation.domain.Motorisation;
import be.autoservplus.reservation.domain.Vehicule;
import be.autoservplus.reservation.repository.VehiculeRepository;
import be.autoservplus.vente.domain.StatutPaiement;
import be.autoservplus.vente.service.PanierService;
import be.autoservplus.vente.service.PrestatairePaiementFictif;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Suppression de compte contre une vraie base (F23) : c est ici que se verifient les
 * points qu aucun test unitaire ne peut voir — les documents comptables restent
 * intacts, le balayage des colonnes d audit porte reellement, la ligne anonymisee
 * reste jointe malgre le {@code @SQLRestriction}, et l ancienne adresse redevient
 * libre.
 *
 * <p>Volontairement <b>sans</b> {@code @Transactional} de classe : la facture nait
 * d un listener AFTER_COMMIT et le balayage est une fonction native. Une transaction
 * englobante rollbackee ne prouverait rien. Consequence : les donnees restent en base
 * du conteneur, d ou les adresses uniques par test.</p>
 *
 * <p>Ce fichier n exerce que le <b>service</b> : ce qui passe par le controleur — le
 * refus sans confirmation, l impossibilite de viser le compte d autrui, la revocation
 * de session — vit dans {@code SuppressionCompteWebIT}. La separation n est pas
 * cosmetique : elle laisse ce fichier compiler et passer sans la couche web, donc
 * prouver l anonymisation dans le commit qui la livre.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("Suppression de compte (integration)")
class SuppressionCompteIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);
    private static final String MOT_DE_PASSE = "MonMotDePasse2026!";
    private static final String MEMBRE = "ROLE_MEMBRE";

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private VehiculeRepository vehicules;
    @Autowired private CategorieRepository categories;
    @Autowired private PieceRepository pieces;
    @Autowired private PanierService panierService;
    @Autowired private FactureService factures;
    @Autowired private PdfFactureService pdfFactures;
    @Autowired private SuppressionCompteService suppression;
    @Autowired private PrestatairePaiementFictif prestataireFictif;
    @Autowired private PasswordEncoder encodeur;
    @Autowired private TransactionTemplate transactions;
    @Autowired private JdbcTemplate jdbc;

    // --- montage ------------------------------------------------------------------------

    /** Membre neuf, adresse unique : la classe ne rollbacke pas. */
    private Utilisateur membre() {
        String email = "membre-" + COMPTEUR.getAndIncrement() + "@exemple.be";
        return transactions.execute(statut -> {
            Utilisateur nouveau = new Utilisateur(email, encodeur.encode(MOT_DE_PASSE),
                    "Dupont", "Marie", TypeUtilisateur.MEMBRE);
            nouveau.setTelephone("+32 470 12 34 56");
            nouveau.setRue("Rue de la Loi");
            nouveau.setCodePostal("1000");
            nouveau.setLocalite("Bruxelles");
            nouveau.confirmerAdresseEmail();
            return utilisateurs.saveAndFlush(nouveau);
        });
    }

    private Vehicule vehicule(Utilisateur proprietaire, String plaque) {
        return executerEn(proprietaire.getEmail(), () -> vehicules.saveAndFlush(
                new Vehicule(proprietaire, plaque, "VW", "Golf", Motorisation.DIESEL)));
    }

    private Piece piece() {
        Categorie categorie = transactions.execute(statut -> categories.findByCode("IT-SUP-CAT")
                .orElseGet(() -> categories.save(
                        new Categorie("IT-SUP-CAT", "Pieces", TypeCategorie.PIECE))));
        return transactions.execute(statut -> {
            Piece piece = new Piece(categorie, "IT-SUP-" + COMPTEUR.getAndIncrement(),
                    "Piece de test", new BigDecimal("19.99"));
            piece.setTauxTva(new BigDecimal("21.00"));
            piece.setQuantiteStock(50);
            return pieces.save(piece);
        });
    }

    /** Panier, commande, paiement confirme : la commande ressort PAYEE et facturee. */
    private UUID commandePayee(Utilisateur acheteur) throws Exception {
        Piece piece = piece();
        executerEn(acheteur.getEmail(), () -> {
            panierService.ajouterPiece(acheteur.getEmail(), piece.getReference(), 2);
            return null;
        });
        String redirection = mvc.perform(post("/commande").with(user(acheteur.getEmail()))
                        .with(csrf()).param("cgv", "true"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        UUID reference = UUID.fromString(
                redirection.replace("/commande/", "").replace("/confirmation", ""));

        String paiement = mvc.perform(post("/commande/{ref}/payer", reference)
                        .with(user(acheteur.getEmail())).with(csrf()))
                .andExpect(redirectedUrlPattern("/paiement-fictif/*"))
                .andReturn().getResponse().getRedirectedUrl();
        String referencePrestataire = paiement.substring(paiement.lastIndexOf('/') + 1);
        prestataireFictif.programmerStatut(referencePrestataire, StatutPaiement.REUSSI);
        mvc.perform(post("/webhooks/paiement").with(anonymous())
                .param("id", referencePrestataire)).andExpect(status().isOk());
        return reference;
    }

    private void supprimer(Utilisateur compte) {
        executerEn(compte.getEmail(), () -> {
            suppression.supprimer(compte.getEmail(), MOT_DE_PASSE, "SUPPRIMER");
            return null;
        });
    }

    private Utilisateur recharger(Long id) {
        return transactions.execute(statut -> utilisateurs.findById(id).orElseThrow());
    }

    // --- scenarios ----------------------------------------------------------------------

    @Test
    @DisplayName("le compte est vide de toute donnee personnelle et l'acces est mort")
    void anonymisationDuCompte() {
        Utilisateur marie = membre();
        String adresseReelle = marie.getEmail();

        supprimer(marie);

        Utilisateur apres = recharger(marie.getId());
        assertThat(apres.getEmail()).isEqualTo("anonyme-" + marie.getId() + "@supprime.invalid");
        assertThat(apres.nomComplet()).isEqualTo("Client supprimé");
        assertThat(apres.getTelephone()).isNull();
        assertThat(apres.getRue()).isNull();
        assertThat(apres.getLocalite()).isNull();
        assertThat(apres.getStatut()).isEqualTo(StatutUtilisateur.SUPPRIME);
        assertThat(apres.getAnonymiseLe()).isNotNull();
        // L ancien mot de passe ne connecte plus, et la verification refuse
        // proprement : le hachage reste un BCrypt valide.
        assertThat(encodeur.matches(MOT_DE_PASSE, apres.getMotDePasseHache())).isFalse();
        assertThat(utilisateurs.findByEmailIgnoreCase(adresseReelle)).isEmpty();
    }

    @Test
    @DisplayName("la ligne reste jointe : deleted_at vide, le compte est encore lisible")
    void ligneNonMasquee() {
        Utilisateur marie = membre();

        supprimer(marie);

        // Le piege du @SQLRestriction : renseigner deleted_at masquerait la ligne de
        // toutes les requetes, et une facture conservee ne pourrait plus resoudre son
        // titulaire. findById passe par la restriction — s il rend la ligne, elle
        // n est pas masquee.
        assertThat(jdbc.queryForObject(
                "SELECT deleted_at FROM utilisateur WHERE id = ?", Object.class, marie.getId()))
                .isNull();
        Optional<Utilisateur> relue =
                transactions.execute(s -> utilisateurs.findById(marie.getId()));
        assertThat(relue).isPresent();
    }

    @Test
    @DisplayName("les documents comptables sont INTACTS : montants, numero, chemin PDF")
    void documentsComptablesIntacts() throws Exception {
        Utilisateur marie = membre();
        UUID commande = commandePayee(marie);
        Facture facture = transactions.execute(s -> factures.factureDe(commande).orElseThrow());
        String numeroAvant = facture.getNumero();
        BigDecimal montantAvant = facture.getMontantTvac();

        supprimer(marie);

        // Article 17.3.b : l obligation de conservation (art. 60 Code TVA) prime sur
        // l effacement pour ces documents precis. Rien n est regenere, rien n est
        // reecrit.
        assertThat(jdbc.queryForObject("SELECT numero FROM facture WHERE id = ?",
                String.class, facture.getId())).isEqualTo(numeroAvant);
        assertThat(jdbc.queryForObject("SELECT montant_tvac FROM facture WHERE id = ?",
                BigDecimal.class, facture.getId())).isEqualByComparingTo(montantAvant);
        // La commande garde ses montants ; seule la ligne utilisateur a change.
        assertThat(jdbc.queryForObject("SELECT montant_tvac FROM commande WHERE reference = ?",
                BigDecimal.class, commande)).isEqualByComparingTo(montantAvant);
    }

    @Test
    @DisplayName("la facture d'un compte anonymise resout son titulaire en « Client supprime »")
    void factureAfficheClientSupprime() throws Exception {
        Utilisateur marie = membre();
        UUID commande = commandePayee(marie);
        Facture facture = transactions.execute(s -> factures.factureDe(commande).orElseThrow());

        supprimer(marie);

        // La relation ne casse pas et n affiche pas un vide : c est tout l interet
        // d avoir laisse la ligne vivante.
        String titulaire = transactions.execute(s -> factures.pourMembre(
                facture.getReference(),
                "anonyme-" + marie.getId() + "@supprime.invalid").getMembre().nomComplet());
        assertThat(titulaire).isEqualTo("Client supprimé");
    }

    @Test
    @DisplayName("la facture regeneree apres anonymisation porte le marqueur dans la langue du client")
    void factureRegenereePorteLeMarqueurTraduit() throws Exception {
        Utilisateur marie = membre();
        // Cliente neerlandophone : c est le cas que le marqueur fige en francais
        // aurait casse.
        transactions.executeWithoutResult(statut -> {
            Utilisateur relue = utilisateurs.findById(marie.getId()).orElseThrow();
            relue.setLangue(Langue.nl);
            utilisateurs.saveAndFlush(relue);
        });
        UUID commande = commandePayee(marie);
        Facture facture = transactions.execute(s -> factures.factureDe(commande).orElseThrow());

        supprimer(marie);

        // Le PDF archive n est jamais regenere ; mais s il a disparu, la
        // reconstruction lit le compte anonymise. La langue, elle, n est pas effacee
        // par l anonymisation : ce n est pas une donnee identifiante.
        byte[] pdf = transactions.execute(s -> pdfFactures.pdfDe(
                factures.pourMembre(facture.getReference(),
                        "anonyme-" + marie.getId() + "@supprime.invalid")));
        assertThat(pdf).startsWith("%PDF".getBytes());
        // Le flux de contenu est compresse : on extrait le texte plutot que de
        // chercher dans les octets, comme le fait GenerateurPdfFactureTest.
        String texte = texteDe(pdf);
        assertThat(texte)
                .as("Le document doit porter le marqueur neerlandais, pas le francais")
                .contains("Verwijderde klant")
                .doesNotContain("Client supprim");
    }

    /** Texte de la premiere page, flux de contenu decompresse. */
    private static String texteDe(byte[] pdf) throws Exception {
        PdfReader lecteur = new PdfReader(pdf);
        try {
            return new PdfTextExtractor(lecteur).getTextFromPage(1);
        } finally {
            lecteur.close();
        }
    }

    @Test
    @DisplayName("les colonnes d'audit ne contiennent plus l'adresse reelle")
    void tracesDAuditBalayees() throws Exception {
        Utilisateur marie = membre();
        String adresseReelle = marie.getEmail();
        vehicule(marie, "1-SUP-" + marie.getId());
        commandePayee(marie);

        // Avant : JpaAuditingConfig a bien ecrit l adresse en clair.
        assertThat(compterOccurrences(adresseReelle)).isPositive();

        supprimer(marie);

        // Apres : plus une seule occurrence, dans AUCUNE table du schema.
        assertThat(compterOccurrences(adresseReelle))
                .as("Un effacement qui laisse l identifiant de connexion en clair n en est pas un")
                .isZero();
    }

    /** Occurrences de l adresse dans toutes les colonnes d audit du schema. */
    private long compterOccurrences(String adresse) {
        return jdbc.queryForObject("""
                SELECT coalesce(sum(n), 0) FROM (
                    SELECT (SELECT count(*) FROM vehicule    WHERE created_by = ? OR updated_by = ?) AS n
                    UNION ALL SELECT (SELECT count(*) FROM commande     WHERE created_by = ? OR updated_by = ?)
                    UNION ALL SELECT (SELECT count(*) FROM paiement     WHERE created_by = ? OR updated_by = ?)
                    UNION ALL SELECT (SELECT count(*) FROM panier       WHERE created_by = ? OR updated_by = ?)
                    UNION ALL SELECT (SELECT count(*) FROM ligne_panier WHERE created_by = ? OR updated_by = ?)
                    UNION ALL SELECT (SELECT count(*) FROM consentement WHERE created_by = ? OR updated_by = ?)
                    UNION ALL SELECT (SELECT count(*) FROM utilisateur  WHERE created_by = ? OR updated_by = ?)
                ) t
                """, Long.class, adresse, adresse, adresse, adresse, adresse, adresse,
                adresse, adresse, adresse, adresse, adresse, adresse, adresse, adresse);
    }

    @Test
    @DisplayName("APRES COMMIT, la propre ligne du membre ne porte plus son adresse")
    void propreLignePostCommit() {
        Utilisateur marie = membre();
        String adresseReelle = marie.getEmail();
        String jeton = "anonyme-" + marie.getId() + "@supprime.invalid";

        supprimer(marie);

        // Le point delicat : l audit JPA pose updated_by au flush. Si le balayage
        // passait AVANT ce flush, ou si un re-tampon survenait au commit, l adresse
        // reapparaitrait sur la ligne meme qu on vient de vider. L assertion porte
        // donc sur l etat en base APRES commit, lu en SQL brut — la lire par
        // l entite ne prouverait rien, le cache de session pouvant la masquer.
        assertThat(jdbc.queryForObject(
                "SELECT updated_by FROM utilisateur WHERE id = ?", String.class, marie.getId()))
                .isNotEqualTo(adresseReelle)
                .isEqualTo(jeton);
        assertThat(jdbc.queryForObject(
                "SELECT created_by FROM utilisateur WHERE id = ?", String.class, marie.getId()))
                .isNotEqualTo(adresseReelle);
    }

    @Test
    @DisplayName("les traces d'audit portent le jeton resoluble, jamais NULL")
    void jetonPlutotQueNull() {
        Utilisateur marie = membre();
        String jeton = "anonyme-" + marie.getId() + "@supprime.invalid";
        vehicule(marie, "1-SUP-" + COMPTEUR.getAndIncrement());

        supprimer(marie);

        // Les colonnes d audit sont nullables : NULL passerait. Le jeton est prefere
        // parce qu il reste resoluble — savoir QUE la ligne a ete creee par le compte
        // 42, desormais anonymise, est encore de la tracabilite ; un NULL la perdrait.
        assertThat(jdbc.queryForList(
                "SELECT created_by FROM vehicule WHERE membre_id = ?", String.class, marie.getId()))
                .allSatisfy(valeur -> assertThat(valeur).isEqualTo(jeton));
    }

    @Test
    @DisplayName("vehicule sans historique : supprime physiquement")
    void vehiculeSansHistoriqueSupprime() {
        Utilisateur marie = membre();
        Vehicule golf = vehicule(marie, "1-SUP-" + COMPTEUR.getAndIncrement());

        supprimer(marie);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM vehicule WHERE id = ?",
                Integer.class, golf.getId())).isZero();
    }

    @Test
    @DisplayName("vehicule reference par un rendez-vous : anonymise, FK intacte")
    void vehiculeAvecHistoriqueAnonymise() {
        Utilisateur marie = membre();
        Vehicule golf = vehicule(marie, "1-SUP-" + COMPTEUR.getAndIncrement());
        // Un RDV suffit : fk_rdv_vehicule est en ON DELETE RESTRICT. poste_id est
        // NOT NULL et V17 seede les postes ; l intervalle est decale par vehicule
        // pour ne pas heurter l exclusion ex_rdv_poste_intervalle.
        jdbc.update("""
                INSERT INTO rdv (numero, membre_id, vehicule_id, poste_id, statut, debut, fin)
                SELECT ?, ?, ?, p.id, 'EN_ATTENTE',
                       now() + (? * interval '1 day'), now() + (? * interval '1 day') + interval '1 hour'
                FROM poste_atelier p WHERE p.actif = TRUE AND p.deleted_at IS NULL
                ORDER BY p.id LIMIT 1
                """, "RDV-SUP-" + golf.getId(), marie.getId(), golf.getId(),
                golf.getId(), golf.getId());

        supprimer(marie);

        assertThat(jdbc.queryForObject("SELECT plaque FROM vehicule WHERE id = ?",
                String.class, golf.getId())).isEqualTo("ANON-" + golf.getId());
        assertThat(jdbc.queryForObject("SELECT actif FROM vehicule WHERE id = ?",
                Boolean.class, golf.getId())).isFalse();
        assertThat(jdbc.queryForObject("SELECT numero_chassis FROM vehicule WHERE id = ?",
                String.class, golf.getId())).isNull();
        // La FK tient : l historique d atelier reste lisible pour le garage.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rdv WHERE vehicule_id = ?",
                Integer.class, golf.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("l'ancienne adresse redevient libre : la reinscription est possible")
    void reinscriptionPossible() {
        Utilisateur marie = membre();
        String adresseReelle = marie.getEmail();

        supprimer(marie);

        // uq_utilisateur_email porte sur toute la table : le jeton libere l adresse.
        Utilisateur nouveau = transactions.execute(statut -> utilisateurs.saveAndFlush(
                new Utilisateur(adresseReelle, encodeur.encode(MOT_DE_PASSE),
                        "Dupont", "Marie", TypeUtilisateur.MEMBRE)));
        assertThat(nouveau.getId()).isNotEqualTo(marie.getId());
        assertThat(utilisateurs.findByEmailIgnoreCase(adresseReelle)).isPresent();
    }

    @Test
    @DisplayName("un compte deja anonymise ne se supprime pas deux fois")
    void pasDeDoubleSuppression() {
        Utilisateur marie = membre();
        String adresseReelle = marie.getEmail();
        supprimer(marie);

        // L ancienne adresse ne resout plus aucun compte : c est la garde
        // d existence qui refuse, avant meme celle du mot de passe. L entite en
        // porte une troisieme (estAnonymise), qu on n atteint donc jamais par ce
        // chemin — trois etages pour une operation irreversible.
        assertThatThrownBy(() -> executerEn(adresseReelle, () -> {
            suppression.supprimer(adresseReelle, MOT_DE_PASSE, "SUPPRIMER");
            return null;
        })).isInstanceOf(RessourceIntrouvableException.class);

        // Et par le jeton anonyme, le hachage inerte refuse la re-authentification :
        // personne ne connait le secret aleatoire qui l a produit.
        String jeton = "anonyme-" + marie.getId() + "@supprime.invalid";
        assertThatThrownBy(() -> executerEn(jeton, () -> {
            suppression.supprimer(jeton, MOT_DE_PASSE, "SUPPRIMER");
            return null;
        })).isInstanceOf(ReauthentificationEchoueeException.class);
    }

    // --- helper d identite ---------------------------------------------------------------

    /**
     * Execute sous une identite donnee, dans sa transaction. Le contexte est pose
     * explicitement : la chaine de filtres vide le {@code SecurityContextHolder} a la
     * fin de chaque requete MockMvc, et les services portent {@code @PreAuthorize}.
     * C est aussi ce qui fait ecrire les colonnes d audit avec la bonne adresse.
     */
    private <T> T executerEn(String email, java.util.function.Supplier<T> action) {
        SecurityContext contexte = SecurityContextHolder.createEmptyContext();
        List<SimpleGrantedAuthority> roles = List.of(new SimpleGrantedAuthority(MEMBRE));
        contexte.setAuthentication(new UsernamePasswordAuthenticationToken(
                new User(email, "n/a", roles), "n/a", roles));
        SecurityContextHolder.setContext(contexte);
        try {
            return transactions.execute(statut -> action.get());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
