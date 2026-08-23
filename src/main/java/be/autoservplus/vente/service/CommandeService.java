package be.autoservplus.vente.service;

import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.identite.domain.Consentement;
import be.autoservplus.identite.domain.TypeDocumentConsentement;
import be.autoservplus.identite.repository.ConsentementRepository;
import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.domain.LignePanier;
import be.autoservplus.vente.domain.Paiement;
import be.autoservplus.vente.domain.Panier;
import be.autoservplus.vente.domain.StatutPaiement;
import be.autoservplus.vente.repository.CommandeRepository;
import be.autoservplus.vente.repository.PaiementRepository;
import be.autoservplus.vente.repository.PanierRepository;
import be.autoservplus.vente.web.dto.CommandeDetailVue;
import be.autoservplus.vente.web.dto.CommandeHistoriqueVue;
import be.autoservplus.vente.web.dto.ConfirmationCommandeVue;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.ZoneId;
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
    private final PaiementRepository paiements;
    private final PanierRepository paniers;
    private final ConsentementRepository consentements;
    private final GenerateurNumeroCommande numeros;
    private final Clock horloge;

    public CommandeService(CommandeRepository commandes,
                           PaiementRepository paiements,
                           PanierRepository paniers,
                           ConsentementRepository consentements,
                           GenerateurNumeroCommande numeros,
                           Clock horloge) {
        this.commandes = commandes;
        this.paiements = paiements;
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
                                                  boolean renonciationVi53,
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
            // Contrainte F13 prolongee a la conversion : on ne commande pas un article
            // retire de la vente depuis son ajout. Vrai des deux natures.
            if (ligne.estService()) {
                // Une prestation n a pas de stock : c est du temps d atelier, pas un
                // article denombrable. Seule son activite se reverifie.
                if (!ligne.getPrestation().isActif()) {
                    throw new PrestationInactiveException(ligne.getLibelleFige());
                }
                continue;
            }
            Piece piece = ligne.getPiece();
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
        // VI.53 (F12) : la renonciation n a de sens que pour un panier de services.
        // Sur un panier de pieces, une case cochee par un client bricolant le
        // formulaire est IGNOREE — validation serveur, jamais confiance au client.
        boolean panierDeServices = panier.estPanierDeServices();
        boolean renonciationRetenue = panierDeServices && renonciationVi53;

        Commande commande = commandes.saveAndFlush(new Commande(
                numeros.prochain(), panier.getMembre(),
                panier.totalHtva(), panier.totalTva(), panier.totalTvac(),
                horloge.instant(), renonciationRetenue));
        commande.reprendreLignes(lignes);

        // Preuve contractuelle F24, dans la MEME transaction : un rollback de la
        // conversion emporte la preuve — jamais de consentement orphelin.
        consentements.save(Consentement.acceptation(panier.getMembre(),
                TypeDocumentConsentement.CGV, Consentement.CGV_VERSION_COURANTE,
                adresseIp, horloge.instant()));

        // Preuve VI.53 (F12), ecrite UNIQUEMENT si la question a ete posee — donc si
        // le panier contient des services. Le refus s enregistre autant que l accord :
        // sans ligne, l absence serait ambigue entre « a refuse » et « jamais
        // interroge », exactement le raisonnement tenu pour les cookies en V29.
        //
        // Meme transaction que la commande et que l etat : jamais d etat sans preuve,
        // jamais de preuve sans etat. On DECIDE sur commande.renonciation_vi53, on
        // PROUVE par cette ligne.
        if (panierDeServices) {
            consentements.save(Consentement.decision(panier.getMembre(),
                    TypeDocumentConsentement.RENONCIATION_RETRACTATION,
                    Consentement.RENONCIATION_VERSION_COURANTE,
                    renonciationRetenue, adresseIp, horloge.instant()));
        }

        return ConfirmationCommandeVue.de(commande);
    }

    /**
     * Page de confirmation. Une commande d autrui remonte comme introuvable (404,
     * meme code qu une reference inconnue) — meme mecanisme que le reste.
     */
    public ConfirmationCommandeVue confirmation(UUID reference, String email) {
        return ConfirmationCommandeVue.de(commandeDuMembre(reference, email));
    }

    /**
     * Detail d une commande passee (F32) : ses lignes aux prix figes, ses totaux et
     * son paiement.
     *
     * <p>Les liens vers la facture et vers la retractation ne sont pas resolus ici :
     * la vente ignore ces deux modules, c est le controleur qui assemble — meme
     * partage que pour la liste.</p>
     */
    public CommandeDetailVue detail(UUID reference, String email) {
        Commande commande = commandeDuMembre(reference, email);
        return CommandeDetailVue.de(commande, commandes.lignesDe(commande),
                paiementAbouti(commande), ZoneId.of(horloge.getZone().getId()));
    }

    /**
     * Le paiement qui a effectivement abouti, ou {@code null}. REMBOURSE compte au
     * meme titre que REUSSI : l encaissement a bien eu lieu, et c est un second
     * mouvement qui le contre-passe — masquer le moyen de paiement d une commande
     * remboursee reviendrait a nier qu elle a ete payee.
     *
     * <p>Les tentatives echouees ou expirees sont ecartees : une commande peut en
     * compter plusieurs, elles ne disent rien de la maniere dont elle a ete reglee.</p>
     */
    private Paiement paiementAbouti(Commande commande) {
        return paiements.findByCommandeAndStatutIn(commande,
                        List.of(StatutPaiement.REUSSI, StatutPaiement.REMBOURSE))
                .stream()
                .findFirst()
                .orElse(null);
    }

    /**
     * Charge une commande en verifiant qu elle appartient au membre. La commande
     * d autrui remonte comme introuvable — 404, le meme code qu une reference
     * inconnue : un 403 confirmerait a un tiers que cette commande existe.
     */
    private Commande commandeDuMembre(UUID reference, String email) {
        Commande commande = commandes.findByReference(reference)
                .orElseThrow(() -> new RessourceIntrouvableException("Commande", reference));
        if (!commande.getMembre().getEmail().equalsIgnoreCase(email)) {
            throw new RessourceIntrouvableException("Commande", reference);
        }
        return commande;
    }

    /**
     * Historique des commandes du membre connecte (F32), de la plus recente a la
     * plus ancienne. Les champs de facture des vues restent nuls : le module vente
     * ignore la facturation, c est le controleur qui les complete.
     */
    public List<CommandeHistoriqueVue> historiqueDuMembre(String email) {
        ZoneId zone = ZoneId.of(horloge.getZone().getId());
        return commandes.historiqueDuMembre(email).stream()
                .map(commande -> CommandeHistoriqueVue.de(commande, zone))
                .toList();
    }
}
