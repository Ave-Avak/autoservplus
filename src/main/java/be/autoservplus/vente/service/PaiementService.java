package be.autoservplus.vente.service;

import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.repository.PieceRepository;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.domain.LignePanier;
import be.autoservplus.vente.domain.Paiement;
import be.autoservplus.vente.domain.StatutCommande;
import be.autoservplus.vente.domain.StatutPaiement;
import be.autoservplus.vente.repository.CommandeRepository;
import be.autoservplus.vente.repository.PaiementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Paiement d une commande (seconde moitie de F14) : initiation aupres du
 * prestataire, traitement des notifications entrantes, decrement du stock au
 * paiement confirme, expiration RM-21 (portee par le job dedie).
 *
 * <p><b>Securite du webhook (strategie §11)</b> : le payload entrant n est
 * JAMAIS cru. L identifiant recu sert uniquement a retrouver le paiement ; le
 * statut authentique est RELU aupres du prestataire via la passerelle. Un
 * attaquant qui connait l URL du webhook ne peut donc pas forger un « paye ».</p>
 *
 * <p><b>Idempotence par l etat</b> : un webhook rejoue retombe sur une commande
 * deja PAYEE et un paiement deja REUSSI — aucun double decrement, aucun double
 * evenement, aucune double ecriture. Les courses (webhook contre job
 * d expiration, webhooks concurrents) sont serialisees par le verrou pessimiste
 * sur la commande, puis tranchees par les gardes d entite : une PAYEE ne
 * redevient pas ANNULEE, une ANNULEE ne devient pas PAYEE.</p>
 *
 * <p>Pas de {@code @PreAuthorize} de classe : {@link #traiterNotification} est
 * appelee par le prestataire, sans session — seule l initiation, geste du
 * membre, exige l authentification.</p>
 */
@Service
@Transactional(readOnly = true)
public class PaiementService {

    private static final Logger log = LoggerFactory.getLogger(PaiementService.class);

    private final PaiementRepository paiements;
    private final CommandeRepository commandes;
    private final PieceRepository pieces;
    private final PrestatairePaiement prestataire;
    private final ApplicationEventPublisher evenements;
    private final Clock horloge;
    private final String urlPublique;

    public PaiementService(PaiementRepository paiements,
                           CommandeRepository commandes,
                           PieceRepository pieces,
                           PrestatairePaiement prestataire,
                           ApplicationEventPublisher evenements,
                           Clock horloge,
                           @Value("${autoservplus.url-publique}") String urlPublique) {
        this.paiements = paiements;
        this.commandes = commandes;
        this.pieces = pieces;
        this.prestataire = prestataire;
        this.evenements = evenements;
        this.horloge = horloge;
        // Barre finale retiree une fois pour toutes : la concatenation qui suit
        // produirait sinon un double separateur, et une URL de retour invalide ne se
        // decouvrirait que chez le prestataire.
        this.urlPublique = urlPublique.endsWith("/")
                ? urlPublique.substring(0, urlPublique.length() - 1)
                : urlPublique;
    }

    // --- initiation -------------------------------------------------------------------

    /**
     * Cree un paiement INITIE pour la commande et retourne l URL de redirection
     * du prestataire. Un re-essai apres echec cree un NOUVEAU paiement — la
     * commande reste EN_ATTENTE_PAIEMENT pendant son delai RM-21.
     */
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public String initierPaiement(UUID referenceCommande, String email) {
        Commande commande = commandeDuMembre(referenceCommande, email);
        if (commande.getStatut() != StatutCommande.EN_ATTENTE_PAIEMENT) {
            throw new PaiementImpossibleException();
        }
        Paiement paiement = new Paiement(commande, commande.getMontantTvac(), horloge.instant());
        PaiementCree cree = prestataire.creerPaiement(new DemandePaiement(
                commande.getNumero(), commande.getMontantTvac(),
                paiement.getDevise(), paiement.getCleIdempotence(),
                urlRetour(commande.getReference()), urlNotification()));
        paiement.enregistrerReferencePrestataire(cree.referencePrestataire());
        paiements.saveAndFlush(paiement);
        return cree.urlRedirection();
    }

    // --- retour du membre depuis la page du prestataire --------------------------------

    /**
     * Constate l issue du paiement au retour du membre, et rend {@code true} si la
     * commande est payee a l issue de ce constat.
     *
     * <p><b>Le retour n est PAS une preuve de paiement</b>, et rien ici ne le traite
     * comme tel. Le prestataire y renvoie le membre quelle que soit l issue —
     * y compris apres un abandon pur et simple — et l adresse est une URL que
     * n importe qui peut ouvrir. Ce qui tranche reste ce qui tranchait deja : le
     * statut RELU chez le prestataire par {@link #traiterNotification}. Le retour ne
     * fait que declencher cette relecture plus tot.</p>
     *
     * <p><b>Pourquoi ce declenchement est necessaire et non redondant.</b> La
     * notification serveur a serveur n arrive pas toujours : elle n est meme pas
     * demandee quand le site n est pas joignable depuis l exterieur, ce qui est le cas
     * de tout poste de developpement. Sans cette reconciliation, la commande resterait
     * EN_ATTENTE_PAIEMENT apres un paiement reussi, puis serait annulee par le job
     * RM-21 au bout de trente minutes — un encaissement reel sans commande en face.</p>
     *
     * <p><b>Aucun risque de double effet</b> : l appel emprunte exactement le chemin du
     * webhook, dont l idempotence vient de l etat sous verrou pessimiste. Webhook et
     * retour peuvent donc arriver dans n importe quel ordre, ou tous les deux : la
     * facture n est emise qu une fois, sur la transition reelle, et la numerotation
     * continue sans trou n est pas plus exposee qu avant.</p>
     */
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public boolean constaterRetour(UUID referenceCommande, String email) {
        Commande commande = commandeDuMembre(referenceCommande, email);
        paiements.findFirstByCommandeOrderByDateInitiationDescIdDesc(commande)
                .map(Paiement::getReferenceMollie)
                // Une tentative sans reference prestataire n a jamais quitte le site :
                // il n y a rien a relire chez lui.
                .filter(reference -> reference != null && !reference.isBlank())
                .ifPresentOrElse(
                        reference -> journaliserRetour(commande, traiterNotification(reference)),
                        () -> log.info("Retour du membre pour la commande {} : statut relu chez "
                                        + "le prestataire = aucun, aucune tentative n a quitte "
                                        + "le site.",
                                commande.getNumero()));
        return commande.getStatut() == StatutCommande.PAYEE;
    }

    /**
     * Trace du retour du membre, a lire cote a cote avec celle de la notification
     * serveur a serveur ecrite par le controleur de webhook.
     *
     * <p>Le numero de commande, et non sa reference technique : c est lui qui figure
     * sur la facture et dans les autres traces de ce service, donc le seul identifiant
     * par lequel l exploitant relie une ligne de journal a un dossier. Ni montant, ni
     * adresse, ni identifiant d acces — une trace d exploitation n a pas besoin de
     * porter une donnee personnelle pour etre utile.</p>
     */
    private void journaliserRetour(Commande commande, IssueRelecture issue) {
        log.info("Retour du membre pour la commande {} : statut relu chez le prestataire "
                        + "= {}, {}.",
                commande.getNumero(), issue.statutRelu(), issue.effet().libelle());
    }

    // --- notification entrante (webhook) ----------------------------------------------

    /**
     * Traite une notification du prestataire. Rejouable sans double effet : voir
     * le Javadoc de classe pour la securite (statut relu, jamais le payload) et
     * l idempotence par l etat.
     *
     * @return ce que la relecture a constate et ce qu elle a change, pour que chacun
     *         des deux declencheurs — retour du membre, notification serveur a serveur
     *         — redige SA ligne de journal. Rendre l information plutot que de l ecrire
     *         ici est ce qui permet de distinguer les deux chemins, que
     *         {@code docs/deploiement.md} demande de voir arriver tour a tour ; une
     *         ligne ecrite au fond de cette methode serait identique dans les deux cas.
     */
    @Transactional
    public IssueRelecture traiterNotification(String referencePrestataire) {
        Paiement paiement = paiements.findByReferenceMollie(referencePrestataire)
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "Paiement", referencePrestataire));
        // Strategie securite §11 : seul l etat relu chez le prestataire fait foi.
        EtatPaiement etat = prestataire.lireEtat(referencePrestataire);
        // Le moyen arrive dans la meme reponse que le statut, et se pose des qu il est
        // connu — y compris sur un paiement qui n a pas encore abouti. L entite refuse
        // de le reecrire, donc un rejeu ne peut pas l alterer.
        paiement.enregistrerMethode(etat.methode());
        StatutPaiement statutAuthentique = etat.statut();
        IssueRelecture.Effet effet = switch (statutAuthentique) {
            case REUSSI -> confirmer(paiement);
            case ECHOUE, EXPIRE -> clore(paiement, statutAuthentique);
            case EN_COURS -> {
                if (paiement.getStatut() == StatutPaiement.INITIE) {
                    paiement.mettreEnCours();
                    paiements.saveAndFlush(paiement);
                    yield IssueRelecture.Effet.EN_ATTENTE;
                }
                yield IssueRelecture.Effet.DEJA_TRAITE;
            }
            case INITIE -> IssueRelecture.Effet.EN_ATTENTE; // rien de neuf a constater
            case REMBOURSE -> {
                log.warn("Statut REMBOURSE recu pour le paiement {} : bloc retractation "
                        + "a venir, ignore.", referencePrestataire);
                yield IssueRelecture.Effet.DEJA_TRAITE;
            }
        };
        return new IssueRelecture(statutAuthentique, effet);
    }

    /**
     * Paiement confirme par le prestataire. Le verrou pessimiste sur la commande
     * serialise ce traitement avec le job d expiration et les webhooks rejoues ;
     * l etat relu sous verrou decide ensuite, une fois pour toutes.
     */
    private IssueRelecture.Effet confirmer(Paiement paiement) {
        Commande commande = commandes.verrouillerParId(paiement.getCommande().getId())
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "Commande", paiement.getCommande().getId()));

        if (commande.getStatut() == StatutCommande.PAYEE) {
            // Webhook rejoue : tout est deja fait. On aligne au besoin le paiement
            // sur la realite, et rien d autre — ni stock, ni evenement.
            confirmerLePaiementSeul(paiement);
            return IssueRelecture.Effet.DEJA_TRAITE;
        }
        if (commande.getStatut() == StatutCommande.ANNULEE) {
            // Course perdue contre le job RM-21 : l encaissement est reel mais la
            // commande ne ressuscite pas (garde d entite). Le trop-percu se traite
            // hors ligne — le Refund est le bloc retractation.
            confirmerLePaiementSeul(paiement);
            log.warn("Paiement {} encaisse sur la commande {} deja annulee : "
                            + "remboursement a traiter hors ligne.",
                    paiement.getReference(), commande.getNumero());
            // Rien n a ete ecrit sur la commande. L avertissement ci-dessus porte le
            // detail de ce cas rare ; la ligne du declencheur n a pas a le repeter.
            return IssueRelecture.Effet.DEJA_TRAITE;
        }

        paiement.confirmer(horloge.instant());
        commande.confirmerPaiement(horloge.instant());
        decrementerStock(commande);
        paiements.saveAndFlush(paiement);
        commandes.saveAndFlush(commande);
        // Publie UNE fois, sur la transition reelle uniquement : point d accroche de
        // la facture (RM-22, bloc suivant) et du courriel de confirmation.
        evenements.publishEvent(new CommandePayeeEvent(commande.getReference()));
        return IssueRelecture.Effet.FACTURE_EMISE;
    }

    private void confirmerLePaiementSeul(Paiement paiement) {
        if (paiement.getStatut() != StatutPaiement.REUSSI) {
            paiement.confirmer(horloge.instant());
            paiements.saveAndFlush(paiement);
        }
    }

    /** Echec ou expiration : terminal pour CE paiement, la commande attend toujours. */
    private IssueRelecture.Effet clore(Paiement paiement, StatutPaiement cible) {
        if (paiement.estTermine()) {
            return IssueRelecture.Effet.DEJA_TRAITE; // rejouee : deja constate
        }
        if (cible == StatutPaiement.ECHOUE) {
            paiement.echouer(horloge.instant());
        } else {
            paiement.expirer(horloge.instant());
        }
        paiements.saveAndFlush(paiement);
        return IssueRelecture.Effet.TENTATIVE_CLOSE;
    }

    /**
     * Regle (a) du decrement : au paiement confirme, chaque piece est verrouillee
     * (FOR UPDATE, ordre deterministe par id — les lignes arrivent triees) puis
     * decrementee. Si le stock est devenu insuffisant — un autre membre a paye la
     * meme piece entre-temps, rien n etait reserve — la commande reste PAYEE (on
     * n annule pas un encaissement), le stock plancher a 0, le drapeau
     * {@code rupture_a_honorer} leve l alerte et la trace ci-dessous dit QUOI
     * honorer. Le garage gere hors ligne.
     */
    private void decrementerStock(Commande commande) {
        List<LignePanier> lignes = commandes.lignesDe(commande);
        List<String> ruptures = new ArrayList<>();
        for (LignePanier ligne : lignes) {
            // Ceinture et bretelles. lignesDe ne rend deja QUE les lignes de piece —
            // son JOIN FETCH est une jointure interne — donc ce garde n est pas
            // atteint aujourd hui. Il protege d un elargissement futur de la requete,
            // qui ferait sinon lever une NPE sur getPiece() au moment du paiement.
            if (ligne.estService()) {
                continue;
            }
            Piece piece = pieces.verrouillerParId(ligne.getPiece().getId())
                    .orElseThrow(() -> new RessourceIntrouvableException(
                            "Piece", ligne.getPiece().getId()));
            int demande = ligne.getQuantite();
            int disponible = piece.getQuantiteStock();
            int servi = Math.min(demande, disponible);
            if (servi < demande) {
                commande.signalerRupture();
                ruptures.add("%s (demande %d, en stock %d)"
                        .formatted(ligne.getLibelleFige(), demande, disponible));
            }
            if (servi > 0) {
                piece.retirerDuStock(servi);
            }
        }
        if (!ruptures.isEmpty()) {
            log.warn("Rupture a honorer sur la commande {} : {}",
                    commande.getNumero(), String.join(" ; ", ruptures));
        }
    }

    // --- adresses absolues remises au prestataire --------------------------------------

    /**
     * Ou le prestataire renvoie le membre une fois la page de paiement quittee.
     *
     * <p>Absolue et derivee de {@code autoservplus.url-publique}, non de la requete
     * en cours : le prestataire renvoie un navigateur vers le site tel qu il est
     * joignable depuis l exterieur, ce que l URL vue par le serveur derriere un
     * reverse proxy ne dit pas.</p>
     */
    private String urlRetour(UUID referenceCommande) {
        return urlPublique + "/commande/" + referenceCommande + "/retour";
    }

    /** Ou le prestataire notifie le serveur, sans navigateur ni session. */
    private String urlNotification() {
        return urlPublique + "/webhooks/paiement";
    }

    // --- helpers ----------------------------------------------------------------------

    private Commande commandeDuMembre(UUID reference, String email) {
        Commande commande = commandes.findByReference(reference)
                .orElseThrow(() -> new RessourceIntrouvableException("Commande", reference));
        if (!commande.getMembre().getEmail().equalsIgnoreCase(email)) {
            throw new RessourceIntrouvableException("Commande", reference);
        }
        return commande;
    }
}
