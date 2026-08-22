package be.autoservplus.vente.service;

import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.identite.domain.Consentement;
import be.autoservplus.identite.domain.TypeDocumentConsentement;
import be.autoservplus.identite.repository.ConsentementRepository;
import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.domain.LignePanier;
import be.autoservplus.vente.domain.Panier;
import be.autoservplus.vente.repository.CommandeRepository;
import be.autoservplus.vente.repository.PanierRepository;
import be.autoservplus.vente.web.dto.ConfirmationCommandeVue;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Conversion du panier valide en commande (premiere moitie de F14, sans paiement).
 *
 * <p><b>Atomicite</b> : gardes (CGV, panier non vide, pieces actives, stock),
 * creation de la commande, reaffectation des lignes et preuve d acceptation des
 * CGV vivent dans UNE transaction — un refus n ecrit rien, ni commande partielle,
 * ni panier a moitie converti, ni preuve orpheline.</p>
 *
 * <p><b>Stock</b> : reverifie ligne a ligne au moment de convertir — deux membres
 * ont pu paniériser la meme piece, aucun n a reserve (la reservation soft RM-21
 * est une evolution V2). Le stock n est PAS decremente ici : le dictionnaire le
 * decremente au paiement confirme. Fenetre de course residuelle assumee entre
 * conversion et paiement, a documenter.</p>
 *
 * <p><b>Lignes deplacees, jamais recopiees</b> : la meme ligne passe de
 * {@code panier_id} a {@code commande_id}, valeurs figees intactes (RM-30). Le
 * panier-contenant survit et se recharge vide. Attention : ne PAS retirer les
 * lignes de la collection du panier dans cette transaction — {@code orphanRemoval}
 * les supprimerait physiquement, commande comprise (voir
 * {@code LignePanier#rattacherA}).</p>
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("isAuthenticated()")
public class CommandeService {

    private final CommandeRepository commandes;
    private final PanierRepository paniers;
    private final ConsentementRepository consentements;
    private final GenerateurNumeroCommande numeros;
    private final Clock horloge;

    public CommandeService(CommandeRepository commandes,
                           PanierRepository paniers,
                           ConsentementRepository consentements,
                           GenerateurNumeroCommande numeros,
                           Clock horloge) {
        this.commandes = commandes;
        this.paniers = paniers;
        this.consentements = consentements;
        this.numeros = numeros;
        this.horloge = horloge;
    }

    /**
     * Convertit le panier du membre en commande EN_ATTENTE_PAIEMENT (RM-19).
     *
     * @param cgvAcceptees etat REEL de la case cote serveur — un POST forge sans
     *                     le parametre est refuse ici, pas par le navigateur
     * @param adresseIp    IP de la requete pour la preuve d acceptation ; peut etre
     *                     nulle, ne transite ni par les URL ni par les journaux
     */
    @Transactional
    public ConfirmationCommandeVue passerCommande(String email, boolean cgvAcceptees,
                                                  String adresseIp) {
        if (!cgvAcceptees) {
            throw new CgvNonAccepteesException();
        }
        Panier panier = paniers.findByMembreEmail(email)
                .orElseThrow(PanierVideException::new);
        if (panier.estVide()) {
            throw new PanierVideException();
        }
        List<LignePanier> lignes = panier.getLignes();
        for (LignePanier ligne : lignes) {
            Piece piece = ligne.getPiece();
            // Contrainte F13 prolongee a la conversion : on ne commande pas un
            // article retire de la vente depuis son ajout.
            if (!piece.isActif()) {
                throw new PieceInactiveException(ligne.getLibelleFige());
            }
            if (ligne.getQuantite() > piece.getQuantiteStock()) {
                throw new StockInsuffisantException(ligne.getLibelleFige(),
                        ligne.getQuantite(), piece.getQuantiteStock());
            }
        }

        // Montants figes (RM-30) : sommes ligne a ligne des valeurs figees du panier,
        // TVA = TVAC - HTVA par construction — le CHECK ck_commande_coherence passera.
        Commande commande = commandes.saveAndFlush(new Commande(
                numeros.prochain(), panier.getMembre(),
                panier.totalHtva(), panier.totalTva(), panier.totalTvac(),
                horloge.instant()));
        commande.reprendreLignes(lignes);

        // Preuve contractuelle F24, dans la MEME transaction : un rollback de la
        // conversion emporte la preuve — jamais de consentement orphelin.
        consentements.save(Consentement.acceptation(panier.getMembre(),
                TypeDocumentConsentement.CGV, Consentement.CGV_VERSION_COURANTE,
                adresseIp, horloge.instant()));

        return ConfirmationCommandeVue.de(commande);
    }

    /**
     * Page de confirmation. Une commande d autrui remonte comme introuvable (404,
     * meme code qu une reference inconnue) — meme mecanisme que le reste.
     */
    public ConfirmationCommandeVue confirmation(UUID reference, String email) {
        Commande commande = commandes.findByReference(reference)
                .orElseThrow(() -> new RessourceIntrouvableException("Commande", reference));
        if (!commande.getMembre().getEmail().equalsIgnoreCase(email)) {
            throw new RessourceIntrouvableException("Commande", reference);
        }
        return ConfirmationCommandeVue.de(commande);
    }
}
