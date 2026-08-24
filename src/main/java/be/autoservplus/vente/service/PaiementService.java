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

    // --- notification entrante (webhook) ----------------------------------------------

    /**
     * Traite une notification du prestataire. Rejouable sans double effet : voir
     * le Javadoc de classe pour la securite (statut relu, jamais le payload) et
     * l idempotence par l etat.
     */
    @Transactional
    public void traiterNotification(String referencePrestataire) {
        Paiement paiement = paiements.findByReferenceMollie(referencePrestataire)
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "Paiement", referencePrestataire));
        // Strategie securite §11 : seul le statut relu chez le prestataire fait foi.
        StatutPaiement statutAuthentique = prestataire.lireStatut(referencePrestataire);
        switch (statutAuthentique) {
            case REUSSI -> confirmer(paiement);
            case ECHOUE, EXPIRE -> clore(paiement, statutAuthentique);
            case EN_COURS -> {
                if (paiement.getStatut() == StatutPaiement.INITIE) {
                    paiement.mettreEnCours();
                    paiements.saveAndFlush(paiement);
                }
            }
            case INITIE -> { /* rien de neuf a constater */ }
            case REMBOURSE -> log.warn(
                    "Statut REMBOURSE recu pour le paiement {} : bloc retractation a venir, ignore.",
                    referencePrestataire);
        }
    }

    /**
     * Paiement confirme par le prestataire. Le verrou pessimiste sur la commande
     * serialise ce traitement avec le job d expiration et les webhooks rejoues ;
     * l etat relu sous verrou decide ensuite, une fois pour toutes.
     */
    private void confirmer(Paiement paiement) {
        Commande commande = commandes.verrouillerParId(paiement.getCommande().getId())
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "Commande", paiement.getCommande().getId()));

        if (commande.getStatut() == StatutCommande.PAYEE) {
            // Webhook rejoue : tout est deja fait. On aligne au besoin le paiement
            // sur la realite, et rien d autre — ni stock, ni evenement.
            confirmerLePaiementSeul(paiement);
            return;
        }
        if (commande.getStatut() == StatutCommande.ANNULEE) {
            // Course perdue contre le job RM-21 : l encaissement est reel mais la
            // commande ne ressuscite pas (garde d entite). Le trop-percu se traite
            // hors ligne — le Refund est le bloc retractation.
            confirmerLePaiementSeul(paiement);
            log.warn("Paiement {} encaisse sur la commande {} deja annulee : "
                            + "remboursement a traiter hors ligne.",
                    paiement.getReference(), commande.getNumero());
            return;
        }

        paiement.confirmer(horloge.instant());
        commande.confirmerPaiement(horloge.instant());
        decrementerStock(commande);
        paiements.saveAndFlush(paiement);
        commandes.saveAndFlush(commande);
        // Publie UNE fois, sur la transition reelle uniquement : point d accroche de
        // la facture (RM-22, bloc suivant) et du courriel de confirmation.
        evenements.publishEvent(new CommandePayeeEvent(commande.getReference()));
    }

    private void confirmerLePaiementSeul(Paiement paiement) {
        if (paiement.getStatut() != StatutPaiement.REUSSI) {
            paiement.confirmer(horloge.instant());
            paiements.saveAndFlush(paiement);
        }
    }

    /** Echec ou expiration : terminal pour CE paiement, la commande attend toujours. */
    private void clore(Paiement paiement, StatutPaiement cible) {
        if (paiement.estTermine()) {
            return; // notification rejouee : deja constate
        }
        if (cible == StatutPaiement.ECHOUE) {
            paiement.echouer(horloge.instant());
        } else {
            paiement.expirer(horloge.instant());
        }
        paiements.saveAndFlush(paiement);
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
