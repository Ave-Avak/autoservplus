package be.autoservplus.vente.service;

import be.autoservplus.config.IdentiteGarage;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.common.temps.FuseauGarage;
import be.autoservplus.identite.domain.Consentement;
import be.autoservplus.identite.domain.TypeDocumentConsentement;
import be.autoservplus.identite.repository.ConsentementRepository;
import be.autoservplus.legal.domain.TypeDocumentVersionne;
import be.autoservplus.legal.service.VersionsDocumentsService;
import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.domain.LignePanier;
import be.autoservplus.vente.domain.Paiement;
import be.autoservplus.vente.domain.Panier;
import be.autoservplus.vente.domain.SituationPaiement;
import be.autoservplus.vente.domain.StatutCommande;
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
 * Conversion du panier validé en commande (première moitié de F14, sans paiement).
 *
 * <p><b>Atomicité</b> : gardes (CGV, panier non vide, pièces actives, stock),
 * création de la commande, réaffectation des lignes et preuve d'acceptation des
 * CGV vivent dans UNE transaction. Un refus n'écrit rien, ni commande partielle,
 * ni panier à moitié converti, ni preuve orpheline.</p>
 *
 * <p><b>Stock</b> : revérifié ligne à ligne au moment de convertir, car deux membres
 * ont pu paniériser la même pièce, aucun n'a réservé (la réservation soft RM-21
 * est une évolution V2). Le stock n'est PAS décrémenté ici : le dictionnaire le
 * décrémente au paiement confirmé. Fenêtre de course résiduelle assumée entre
 * conversion et paiement, à documenter.</p>
 *
 * <p><b>Lignes déplacées, jamais recopiées</b> : la même ligne passe de
 * {@code panier_id} à {@code commande_id}, valeurs figées intactes (RM-30). Le
 * panier-contenant survit et se recharge vide. Attention : ne PAS retirer les
 * lignes de la collection du panier dans cette transaction, car {@code orphanRemoval}
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
    private final VersionsDocumentsService versionsDocuments;
    private final IdentiteGarage identite;
    private final Clock horloge;
    private final FuseauGarage fuseau;

    public CommandeService(CommandeRepository commandes,
                           PaiementRepository paiements,
                           PanierRepository paniers,
                           ConsentementRepository consentements,
                           GenerateurNumeroCommande numeros,
                           VersionsDocumentsService versionsDocuments,
                           IdentiteGarage identite,
                           Clock horloge,
                           FuseauGarage fuseau) {
        this.fuseau = fuseau;
        this.commandes = commandes;
        this.paiements = paiements;
        this.paniers = paniers;
        this.consentements = consentements;
        this.numeros = numeros;
        this.versionsDocuments = versionsDocuments;
        this.identite = identite;
        this.horloge = horloge;
    }

    /**
     * Convertit le panier du membre en commande EN_ATTENTE_PAIEMENT (RM-19).
     *
     * @param cgvAcceptees état REEL de la case côté serveur : un POST forgé sans
     *                     le paramètre est refusé ici, pas par le navigateur
     * @param adresseIp    IP de la requête pour la preuve d'acceptation. Peut être
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
        // La facture émise au paiement imprime le nom et l'adresse du client (AR n°1,
        // art. 5) : sans adresse complète, aucune commande.
        if (!panier.getMembre().adressePostaleComplete()) {
            throw new AdresseIncompleteException();
        }
        List<LignePanier> lignes = panier.getLignes();
        for (LignePanier ligne : lignes) {
            // Contrainte F13 prolongée à la conversion : on ne commande pas un article
            // retiré de la vente depuis son ajout. Vrai des deux natures.
            if (ligne.estService()) {
                // Une prestation n'a pas de stock : c'est du temps d'atelier, pas un
                // article dénombrable. Seule son activité se revérifie.
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

        // Montants figés (RM-30) : sommés ligne à ligne des valeurs figées du panier,
        // TVA = TVAC - HTVA par construction, et le CHECK ck_commande_coherence passera.
        // VI.53 (F12) : la renonciation n'a de sens que pour un panier de services.
        // Sur un panier de pièces, une case cochée par un client bricolant le
        // formulaire est IGNOREE : validation serveur, jamais confiance au client.
        boolean panierDeServices = panier.estPanierDeServices();
        boolean renonciationRetenue = panierDeServices && renonciationVi53;

        Commande commande = commandes.saveAndFlush(new Commande(
                numeros.prochain(), panier.getMembre(),
                panier.totalHtva(), panier.totalTva(), panier.totalTvac(),
                horloge.instant(), renonciationRetenue,
                // La langue des documents se décide ici, une fois : la facture et l'avoir
                // la reprendront, et les lignes s'y figent à l'instant suivant.
                identite.langueDuDocument(panier.getMembre().getLangue())));
        commande.reprendreLignes(lignes);

        // Preuve contractuelle F24, dans la MÊME transaction : un rollback de la
        // conversion emporte la preuve, jamais de consentement orphelin.
        // La version vient de version_document (F24) et non plus d'une constante : elle
        // désigne une ligne dont le texte est archivé, donc une preuve qui dit QUOI a
        // été accepté et pas seulement QUE quelque chose l'a été. La version écrite est
        // celle en vigueur à la validation. Elle peut différer de celle affichée au
        // récapitulatif si une version est publiée entre les deux. Parade non réalisée :
        // transmettre la version affichée et refuser l'envoi si elle n'est plus en vigueur.
        consentements.save(Consentement.acceptation(panier.getMembre(),
                TypeDocumentConsentement.CGV,
                versionsDocuments.versionCourante(TypeDocumentVersionne.CGV),
                adresseIp, horloge.instant()).pourLaCommande(commande.getId()));

        // Preuve VI.53 (F12), écrite UNIQUEMENT si la question a été posée, donc si
        // le panier contient des services. Le refus s'enregistre autant que l'accord :
        // sans ligne, l'absence serait ambiguë entre « a refusé » et « jamais
        // interrogé », exactement le raisonnement tenu pour les cookies en V29.
        //
        // Même transaction que la commande et que l'état : jamais d'état sans preuve,
        // jamais de preuve sans état. On DECIDE sur commande.renonciation_vi53, on
        // PROUVE par cette ligne.
        if (panierDeServices) {
            consentements.save(Consentement.decision(panier.getMembre(),
                    TypeDocumentConsentement.RENONCIATION_RETRACTATION,
                    versionsDocuments.versionCourante(TypeDocumentVersionne.RENONCIATION_RETRACTATION),
                    renonciationRetenue, adresseIp, horloge.instant()).pourLaCommande(commande.getId()));
        }

        return ConfirmationCommandeVue.de(commande, SituationPaiement.A_PAYER,
                lignes.stream().anyMatch(ligne -> !ligne.estService()));
    }

    /**
     * Page de confirmation. Une commande d'autrui remonte comme introuvable (404,
     * même code qu'une référence inconnue) : même mécanisme que le reste.
     */
    public ConfirmationCommandeVue confirmation(UUID reference, String email) {
        Commande commande = commandeDuMembre(reference, email);
        return ConfirmationCommandeVue.de(commande, situation(commande),
                !commandes.lignesDe(commande).isEmpty(), recuApresAnnulation(commande),
                fuseau.zone());
    }

    /**
     * Paiement arrivé après l'annulation de la commande, ou {@code null}. Seule une
     * commande annulée peut en porter un : les autres ne coûtent aucune lecture.
     */
    private Paiement recuApresAnnulation(Commande commande) {
        if (commande.getStatut() != StatutCommande.ANNULEE) {
            return null;
        }
        return paiements.findFirstByCommandeAndRecuApresAnnulationLeIsNotNull(commande).orElse(null);
    }

    /**
     * Situation du paiement telle que les écrans la disent : la commande, et pour une
     * commande en attente sa dernière tentative. La tentative n'est lue que pour une
     * commande qui attend encore son paiement, seule à en dépendre.
     */
    public SituationPaiement situation(Commande commande) {
        StatutPaiement derniere = commande.getStatut() == StatutCommande.EN_ATTENTE_PAIEMENT
                ? paiements.findFirstByCommandeOrderByDateInitiationDescIdDesc(commande)
                        .map(Paiement::getStatut).orElse(null)
                : null;
        return SituationPaiement.de(commande.getStatut(), derniere,
                commande.delaiDePaiementDepasse(horloge.instant(),
                        ExpirationCommandesJob.DELAI_PAIEMENT));
    }

    /**
     * Détail d'une commande passée (F32) : ses lignes aux prix figés, ses totaux et
     * son paiement.
     *
     * <p>Les liens vers la facture et vers la rétractation ne sont pas résolus ici :
     * la vente ignore ces deux modules, c'est le contrôleur qui assemble, même
     * partage que pour la liste.</p>
     */
    public CommandeDetailVue detail(UUID reference, String email) {
        Commande commande = commandeDuMembre(reference, email);
        return CommandeDetailVue.de(commande, commandes.lignesDe(commande),
                paiementAbouti(commande), situation(commande), fuseau.zone());
    }

    /**
     * Le paiement qui a effectivement abouti, ou {@code null}. REMBOURSE compte au
     * même titre que REUSSI : l'encaissement a bien eu lieu, et c'est un second
     * mouvement qui le contre-passe, masquer le moyen de paiement d'une commande
     * remboursée reviendrait à nier qu'elle a été payée.
     *
     * <p>Les tentatives échouées ou expirées sont écartées : une commande peut en
     * compter plusieurs, elles ne disent rien de la manière dont elle a été réglée.</p>
     */
    private Paiement paiementAbouti(Commande commande) {
        return paiements.findByCommandeAndStatutIn(commande,
                        List.of(StatutPaiement.REUSSI, StatutPaiement.REMBOURSE))
                .stream()
                .findFirst()
                .orElse(null);
    }

    /**
     * Charge une commande en vérifiant qu'elle appartient au membre. La commande
     * d'autrui remonte comme introuvable, 404, le même code qu'une référence
     * inconnue : un 403 confirmerait à un tiers que cette commande existe.
     */
    private Commande commandeDuMembre(UUID reference, String email) {
        Commande commande = commandes.findByReference(reference)
                .orElseThrow(() -> new RessourceIntrouvableException("Commande", reference));
        if (!commande.getMembre().getEmail().equals(email)) {
            throw new RessourceIntrouvableException("Commande", reference);
        }
        return commande;
    }

    /**
     * Historique des commandes du membre connecté (F32), de la plus récente à la
     * plus ancienne. Les champs de facture des vues restent nuls : le module vente
     * ignore la facturation, c'est le contrôleur qui les complète.
     */
    /** L'adresse que la facture imprimera est-elle complète ? Le récapitulatif prévient. */
    public boolean adresseComplete(String email) {
        return paniers.findByMembreEmail(email)
                .map(panier -> panier.getMembre().adressePostaleComplete())
                .orElse(true);
    }

    public List<CommandeHistoriqueVue> historiqueDuMembre(String email) {
        ZoneId zone = fuseau.zone();
        return commandes.historiqueDuMembre(email).stream()
                .map(commande -> CommandeHistoriqueVue.de(commande, situation(commande),
                        recuApresAnnulation(commande), zone))
                .toList();
    }
}
