package be.autoservplus.facturation.service;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.Traductions;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PieceRepository;
import be.autoservplus.identite.domain.Langue;
import be.autoservplus.identite.domain.StatutUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.support.MembresDeTest;
import be.autoservplus.support.SocleIntegration;
import be.autoservplus.vente.domain.StatutPaiement;
import be.autoservplus.vente.service.PanierService;
import be.autoservplus.vente.service.PrestatairePaiementFictif;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Parcours commun aux tests de la langue des documents : un membre d'une langue donnée
 * achète une pièce traduite et une pièce restée en français, puis paie. Chaque classe
 * concrète fixe le siège du garage, donc les langues qu'il admet.
 *
 * <p>Sans {@code @Transactional} : la facture naît d'un écouteur exécuté après la
 * validation du paiement.</p>
 */
abstract class ParcoursLangueDesDocuments extends SocleIntegration {

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);

    @Autowired protected MockMvc mvc;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private CategorieRepository categories;
    @Autowired private PieceRepository pieces;
    @Autowired private PanierService panierService;
    @Autowired private FactureService factures;
    @Autowired private PdfFactureService pdfFactures;
    @Autowired private PrestatairePaiementFictif prestataireFictif;
    @Autowired private TransactionTemplate transactions;

    protected Utilisateur membre(Langue langue) {
        String email = "langue-" + COMPTEUR.getAndIncrement() + "@exemple.be";
        return transactions.execute(statut -> {
            Utilisateur membre = MembresDeTest.membre(email, "{noop}inutilise", "Dupont", "Marie");
            membre.confirmerAdresseEmail();
            membre.setStatut(StatutUtilisateur.ACTIF);
            membre.setLangue(langue);
            return utilisateurs.saveAndFlush(membre);
        });
    }

    /** Une pièce traduite (« Oliefilter », « Oil filter ») et une pièce restée en français, au panier. */
    protected void remplirLePanier(Utilisateur acheteur) {
        int numero = COMPTEUR.getAndIncrement();
        List<UUID> articles = transactions.execute(statut -> {
            Categorie categorie = categories.findByCode("IT-LNG").orElseGet(
                    () -> categories.save(new Categorie("IT-LNG", "Filtration", TypeCategorie.PIECE)));
            Piece traduite = new Piece(categorie, "IT-LNG-T-" + numero, "Filtre à huile", new BigDecimal("12.40"));
            traduite.setQuantiteStock(50);
            traduite.traduire(new Traductions("Oliefilter", "Oil filter", null, null));
            Piece francaise = new Piece(categorie, "IT-LNG-F-" + numero, "Joint de bouchon", new BigDecimal("3.00"));
            francaise.setQuantiteStock(50);
            return List.of(pieces.save(traduite).getReference(), pieces.save(francaise).getReference());
        });
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                acheteur.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_MEMBRE"))));
        try {
            articles.forEach(article -> panierService.ajouterPiece(acheteur.getEmail(), article, 1));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /** Le panier de {@link #remplirLePanier}, puis la commande. */
    protected UUID commander(Utilisateur acheteur) throws Exception {
        remplirLePanier(acheteur);
        String redirection = mvc.perform(post("/commande").with(user(acheteur.getEmail()))
                        .with(csrf()).param("cgv", "true"))
                .andExpect(redirectedUrlPattern("/commande/*/confirmation"))
                .andReturn().getResponse().getRedirectedUrl();
        return UUID.fromString(redirection.replace("/commande/", "").replace("/confirmation", ""));
    }

    protected void payer(Utilisateur acheteur, UUID commande) throws Exception {
        String paiement = mvc.perform(post("/commande/{ref}/payer", commande)
                        .with(user(acheteur.getEmail())).with(csrf()))
                .andExpect(redirectedUrlPattern("/paiement-fictif/*"))
                .andReturn().getResponse().getRedirectedUrl();
        String referencePrestataire = paiement.substring(paiement.lastIndexOf('/') + 1);
        prestataireFictif.programmerStatut(referencePrestataire, StatutPaiement.REUSSI);
        mvc.perform(post("/webhooks/paiement").with(anonymous()).param("id", referencePrestataire))
                .andExpect(status().isOk());
    }

    protected String langueDeLaCommande(UUID commande) {
        return jdbc.queryForObject("SELECT langue_document FROM commande WHERE reference = ?", String.class, commande);
    }

    protected String langueDeLaFacture(UUID commande) {
        return jdbc.queryForObject("""
                SELECT f.langue_document FROM facture f JOIN commande c ON c.id = f.commande_id
                WHERE c.reference = ?""", String.class, commande);
    }

    protected List<String> libellesFiges(UUID commande) {
        return jdbc.queryForList("""
                SELECT l.libelle_fige FROM ligne_panier l JOIN commande c ON c.id = l.commande_id
                WHERE c.reference = ? ORDER BY l.id""", String.class, commande);
    }

    protected Map<String, Object> ligne(UUID commande, String libelle) {
        return jdbc.queryForMap("""
                SELECT l.prix_unitaire_htva, l.taux_tva FROM ligne_panier l JOIN commande c ON c.id = l.commande_id
                WHERE c.reference = ? AND l.libelle_fige = ?""", commande, libelle);
    }

    protected String texteDeLaFacture(UUID commande) throws Exception {
        byte[] pdf = transactions.execute(statut -> pdfFactures.pdfDe(factures.factureDe(commande).orElseThrow()));
        PdfReader lecteur = new PdfReader(pdf);
        StringBuilder texte = new StringBuilder();
        for (int page = 1; page <= lecteur.getNumberOfPages(); page++) {
            texte.append(new PdfTextExtractor(lecteur).getTextFromPage(page));
        }
        return texte.toString();
    }
}
