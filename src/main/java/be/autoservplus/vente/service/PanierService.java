package be.autoservplus.vente.service;

import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.repository.PieceRepository;
import be.autoservplus.common.exception.ConflitConcurrenceException;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.vente.domain.LignePanier;
import be.autoservplus.vente.domain.Panier;
import be.autoservplus.vente.repository.PanierRepository;
import be.autoservplus.vente.web.dto.PanierVue;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Panier d achat du membre connecte (F13) : consultation, ajout d une piece,
 * modification de quantite, retrait de ligne, vidage.
 *
 * <p>L identite vient toujours du contexte de securite, transmise en email par le
 * controleur ({@code @AuthenticationPrincipal}) — jamais d un parametre de requete.
 * L ownership est verifie par le chargement : une ligne n est cherchee que dans le
 * panier du membre, une ligne d autrui remonte donc en
 * {@link RessourceIntrouvableException} (404, meme code qu un id inconnu) sans
 * confirmer son existence — meme mecanisme que {@code interventionDuMembre}.</p>
 *
 * <p><b>RM-19</b> (au plus un panier par membre) : trouve-ou-cree porte par les
 * chemins d ecriture uniquement — une lecture ne cree pas de ligne en base, un GET
 * reste sans effet de bord. Le dernier arbitre est l index unique partiel
 * {@code uq_panier_membre_actif} : deux creations concurrentes ne peuvent pas
 * passer toutes les deux, la perdante est traduite en
 * {@link ConflitConcurrenceException}.</p>
 *
 * <p><b>F13 / stock</b> : le controle porte sur le stock <b>physique</b> de la
 * piece, sans decrementation a l ajout — la reservation virtuelle de 30 minutes
 * (RM-21) est une evolution V2 documentee. Consequence assumee : deux membres
 * peuvent mettre la derniere piece au panier, le premier qui commandera l aura.</p>
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("isAuthenticated()")
public class PanierService {

    private final PanierRepository paniers;
    private final PieceRepository pieces;
    private final UtilisateurRepository utilisateurs;

    public PanierService(PanierRepository paniers,
                         PieceRepository pieces,
                         UtilisateurRepository utilisateurs) {
        this.paniers = paniers;
        this.pieces = pieces;
        this.utilisateurs = utilisateurs;
    }

    // --- consultation -----------------------------------------------------------------

    public PanierVue panierDuMembre(String email) {
        return paniers.findByMembreEmail(email).map(PanierVue::de).orElseGet(PanierVue::vide);
    }

    /** Nombre d articles pour le compteur d en-tete ; 0 si aucun panier n existe. */
    public int nombreArticles(String email) {
        return paniers.nombreArticles(email);
    }

    // --- ecritures --------------------------------------------------------------------

    /**
     * Ajoute une piece au panier (trouve-ou-cree), en fusionnant avec la ligne
     * existante le cas echeant. Le controle de stock porte sur la quantite
     * <b>cumulee</b> : trois ajouts de 2 ne peuvent pas contourner un stock de 5.
     */
    @Transactional
    public PanierVue ajouterPiece(String email, UUID referencePiece, int quantite) {
        exigerQuantitePositive(quantite);
        Piece piece = pieces.findByReference(referencePiece)
                .orElseThrow(() -> new RessourceIntrouvableException("Piece", referencePiece));
        if (!piece.isActif()) {
            throw new PieceInactiveException(piece.getLibelle());
        }
        Panier panier = trouveOuCree(email);
        int dejaAuPanier = panier.quantitePour(piece);
        if (dejaAuPanier + quantite > piece.getQuantiteStock()) {
            // La quantite encore disponible tient compte de ce que le panier contient
            // deja : c est ce que le membre peut effectivement AJOUTER.
            throw new StockInsuffisantException(piece.getLibelle(), quantite,
                    Math.max(0, piece.getQuantiteStock() - dejaAuPanier));
        }
        panier.ajouterPiece(piece, quantite);
        return PanierVue.de(paniers.saveAndFlush(panier));
    }

    /**
     * Change la quantite d une ligne. Le stock est controle sur la <b>nouvelle
     * quantite totale</b> de la ligne ; une piece devenue inactive (RM-28) ne peut
     * plus voir sa quantite augmenter — la reduire reste permis, on peut toujours
     * renoncer a un article, pas en acquerir davantage.
     */
    @Transactional
    public PanierVue modifierQuantite(String email, Long ligneId, int quantite) {
        exigerQuantitePositive(quantite);
        Panier panier = panierExistant(email, ligneId);
        LignePanier ligne = panier.ligne(ligneId)
                .orElseThrow(() -> new RessourceIntrouvableException("LignePanier", ligneId));
        Piece piece = ligne.getPiece();
        if (quantite > ligne.getQuantite() && !piece.isActif()) {
            throw new PieceInactiveException(ligne.getLibelleFige());
        }
        if (quantite > piece.getQuantiteStock()) {
            throw new StockInsuffisantException(ligne.getLibelleFige(), quantite,
                    piece.getQuantiteStock());
        }
        panier.modifierQuantite(ligneId, quantite);
        return PanierVue.de(paniers.saveAndFlush(panier));
    }

    @Transactional
    public PanierVue retirerLigne(String email, Long ligneId) {
        Panier panier = panierExistant(email, ligneId);
        if (!panier.retirerLigne(ligneId)) {
            throw new RessourceIntrouvableException("LignePanier", ligneId);
        }
        return PanierVue.de(paniers.saveAndFlush(panier));
    }

    /**
     * Retire toutes les lignes. Sans panier existant, l operation est un simple
     * non-evenement : l etat vise — un panier vide — est deja atteint, et un 404
     * revelerait sans necessite qu aucun panier n a jamais ete cree.
     */
    @Transactional
    public void vider(String email) {
        paniers.findByMembreEmail(email).ifPresent(panier -> {
            panier.vider();
            paniers.saveAndFlush(panier);
        });
    }

    // --- helpers ----------------------------------------------------------------------

    /**
     * Panier du membre pour manipuler une ligne. L absence de panier remonte comme
     * « ligne introuvable », pas « panier introuvable » : la reponse est la meme que
     * pour une ligne d autrui, aucune information n est divulguee.
     */
    private Panier panierExistant(String email, Long ligneId) {
        return paniers.findByMembreEmail(email)
                .orElseThrow(() -> new RessourceIntrouvableException("LignePanier", ligneId));
    }

    private Panier trouveOuCree(String email) {
        return paniers.findByMembreEmail(email).orElseGet(() -> creerPour(email));
    }

    /**
     * Creation du panier (RM-19). Si deux requetes du meme membre creent en meme
     * temps, l index unique partiel en base rejette la seconde : on traduit en
     * conflit de concurrence explicite plutot que de laisser fuir une erreur SQL —
     * la transaction est perdue de toute facon, le membre rejoue son geste et
     * trouve le panier cree par la premiere.
     */
    private Panier creerPour(String email) {
        Utilisateur membre = utilisateurs.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur", email));
        try {
            return paniers.saveAndFlush(new Panier(membre));
        } catch (DataIntegrityViolationException e) {
            throw new ConflitConcurrenceException(
                    "Votre panier vient d'être créé par une autre action, réessayez.");
        }
    }

    private static void exigerQuantitePositive(int quantite) {
        if (quantite < 1) {
            throw new IllegalArgumentException("La quantite doit valoir au moins 1.");
        }
    }
}
