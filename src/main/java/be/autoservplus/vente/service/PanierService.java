package be.autoservplus.vente.service;

import be.autoservplus.catalogue.domain.ChoixPieces;
import be.autoservplus.catalogue.domain.ChoixPiecesRefuseException;
import be.autoservplus.common.ReserveAuxTests;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.repository.PieceRepository;
import be.autoservplus.catalogue.repository.PrestationRepository;
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
 * Panier d'achat du membre connecté (F13) : consultation, ajout d'une pièce,
 * modification de quantité, retrait de ligne, vidage.
 *
 * <p>L'identité vient toujours du contexte de sécurité, transmise en email par le
 * contrôleur ({@code @AuthenticationPrincipal}) : jamais d'un paramètre de requête.
 * L'ownership est vérifié par le chargement : une ligne n'est cherchée que dans le
 * panier du membre, une ligne d'autrui remonte donc en
 * {@link RessourceIntrouvableException} (404, même code qu'un id inconnu) sans
 * confirmer son existence, même mécanisme que {@code interventionDuMembre}.</p>
 *
 * <p><b>RM-19</b> (au plus un panier par membre) : trouve-ou-crée porté par les
 * chemins d'écriture uniquement. Une lecture ne crée pas de ligne en base, un GET
 * reste sans effet de bord. Le dernier arbitre est l'index unique partiel
 * {@code uq_panier_membre_actif} : deux créations concurrentes ne peuvent pas
 * passer toutes les deux, la perdante est traduite en
 * {@link ConflitConcurrenceException}.</p>
 *
 * <p><b>F13 / stock</b> : le contrôle porte sur le stock <b>physique</b> de la
 * pièce, sans décrémentation à l'ajout. La réservation virtuelle de 30 minutes
 * (RM-21) est une évolution V2 documentée. Conséquence assumée : deux membres
 * peuvent mettre la dernière pièce au panier, le premier qui commandera l'aura.</p>
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("isAuthenticated()")
public class PanierService {

    private final PanierRepository paniers;
    private final PieceRepository pieces;
    private final PrestationRepository prestations;
    private final UtilisateurRepository utilisateurs;

    public PanierService(PanierRepository paniers,
                         PieceRepository pieces,
                         PrestationRepository prestations,
                         UtilisateurRepository utilisateurs) {
        this.paniers = paniers;
        this.pieces = pieces;
        this.prestations = prestations;
        this.utilisateurs = utilisateurs;
    }

    // --- consultation -----------------------------------------------------------------

    public PanierVue panierDuMembre(String email) {
        return paniers.findByMembreEmail(email).map(PanierVue::de).orElseGet(PanierVue::vide);
    }

    /** Nombre d'articles pour le compteur d'en-tête. 0 si aucun panier n'existe. */
    public int nombreArticles(String email) {
        return paniers.nombreArticles(email);
    }

    // --- écritures --------------------------------------------------------------------

    /**
     * Ajoute une prestation au panier (F12), trouve-ou-crée, en fusionnant la ligne
     * existante le cas échéant.
     *
     * <p><b>Aucun contrôle de stock</b>, contrairement aux pièces : une prestation est
     * du temps d'atelier, pas un article dénombrable. Ce que la V1 ne vérifie donc pas
     * ici, c'est la disponibilité d'un créneau. Le service est vendu, sa planification
     * reste une étape séparée (découplage assumé, voir la dette F12-a).</p>
     *
     * <p>Prestation inactive refusée, miroir exact de RM-28 pour les pièces : on ne
     * vend pas ce qui a été retiré du catalogue.</p>
     */
    @ReserveAuxTests
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public PanierVue ajouterService(String email, UUID referencePrestation, int quantite) {
        ChoixPieces parDefaut = prestations.findByReference(referencePrestation)
                .map(Prestation::choixParDefaut).orElse(null);
        return ajouterService(email, referencePrestation, quantite, parDefaut);
    }

    /**
     * Même ajout, avec le choix des pièces du membre. Aucun n'est déduit : la fiche ne
     * présélectionne rien, et un forfait qui propose un prix pose seule sans choix est refusé.
     *
     * @param choix {@code null} quand la prestation ne propose pas de choix
     * @throws ChoixPiecesRefuseException choix absent alors qu'il est proposé, ou l'inverse
     */
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public PanierVue ajouterService(String email, UUID referencePrestation, int quantite, ChoixPieces choix) {
        exigerQuantitePositive(quantite);
        Prestation prestation = prestations.findByReference(referencePrestation)
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "Prestation", referencePrestation));
        if (!prestation.isActif()) {
            throw new PrestationInactiveException(prestation.getLibelle());
        }
        Panier panier = trouveOuCree(email);
        panier.ajouterService(prestation, quantite, choix);
        return PanierVue.de(paniers.saveAndFlush(panier));
    }

    /**
     * Ajoute une pièce au panier (trouve-ou-crée), en fusionnant avec la ligne
     * existante le cas échéant. Le contrôle de stock porte sur la quantité
     * <b>cumulée</b> : trois ajouts de 2 ne peuvent pas contourner un stock de 5.
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
            // La quantité encore disponible tient compte de ce que le panier contient
            // déjà : c'est ce que le membre peut effectivement AJOUTER.
            throw new StockInsuffisantException(piece.getLibelle(), quantite,
                    Math.max(0, piece.getQuantiteStock() - dejaAuPanier));
        }
        panier.ajouterPiece(piece, quantite);
        return PanierVue.de(paniers.saveAndFlush(panier));
    }

    /**
     * Change la quantité d'une ligne. Le stock est contrôlé sur la <b>nouvelle
     * quantité totale</b> de la ligne. Une pièce devenue inactive (contrainte F13)
     * ne peut
     * plus voir sa quantité augmenter : la réduire reste permis, on peut toujours
     * renoncer à un article, pas en acquérir davantage.
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
     * Retire toutes les lignes. Sans panier existant, l'opération est un simple
     * non-événement : l'état visé (un panier vide) est déjà atteint, et un 404
     * révélerait sans nécessité qu'aucun panier n'a jamais été créé.
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
     * Panier du membre pour manipuler une ligne. L'absence de panier remonte comme
     * « ligne introuvable », pas « panier introuvable » : la réponse est la même que
     * pour une ligne d'autrui, aucune information n'est divulguée.
     */
    private Panier panierExistant(String email, Long ligneId) {
        return paniers.findByMembreEmail(email)
                .orElseThrow(() -> new RessourceIntrouvableException("LignePanier", ligneId));
    }

    private Panier trouveOuCree(String email) {
        return paniers.findByMembreEmail(email).orElseGet(() -> creerPour(email));
    }

    /**
     * Création du panier (RM-19). Si deux requêtes du même membre créent en même
     * temps, l'index unique partiel en base rejette la seconde : on traduit en
     * conflit de concurrence explicite plutôt que de laisser fuir une erreur SQL.
     * La transaction est perdue de toute façon, le membre rejoue son geste et
     * trouve le panier créé par la première.
     */
    private Panier creerPour(String email) {
        Utilisateur membre = utilisateurs.findByEmail(email)
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
