package be.autoservplus.vente.service;

import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.repository.PieceRepository;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.domain.LignePanier;
import be.autoservplus.vente.domain.Paiement;
import be.autoservplus.vente.domain.SituationPaiement;
import be.autoservplus.vente.domain.StatutCommande;
import be.autoservplus.vente.domain.StatutPaiement;
import be.autoservplus.vente.repository.CommandeRepository;
import be.autoservplus.vente.repository.PaiementRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Paiement d une commande (seconde moitie de F14) : initiation aupres du
 * prestataire, traitement des notifications entrantes, decrement du stock au
 * paiement confirme, expiration RM-21 (portee par le job dedie).
 *
 * <p><b>Securite du webhook (strategie §11)</b> : le payload entrant n est
 * JAMAIS cru. L identifiant recu sert uniquement a retrouver le paiement. Le
 * statut authentique est RELU aupres du prestataire via la passerelle. Un
 * attaquant qui connait l URL du webhook ne peut donc pas forger un « paye ».</p>
 *
 * <p><b>Idempotence par l etat</b> : un webhook rejoue retombe sur une commande
 * deja PAYEE et un paiement deja REUSSI, aucun double decrement, aucun double
 * evenement, aucune double ecriture. Les courses (webhook contre job
 * d expiration, webhooks concurrents) sont serialisees par le verrou pessimiste
 * sur la commande, puis tranchees par les gardes d entite : une PAYEE ne
 * redevient pas ANNULEE, une ANNULEE ne devient pas PAYEE.</p>
 *
 * <p>Pas de {@code @PreAuthorize} de classe : {@link #traiterNotification} est
 * appelee par le prestataire, sans session. Seule l initiation, geste du
 * membre, exige l authentification.</p>
 */
@Service
@Transactional(readOnly = true)
public class PaiementService {

    private static final Logger log = LoggerFactory.getLogger(PaiementService.class);

    /** Tentatives que le prestataire peut encore faire aboutir. */
    private static final Set<StatutPaiement> OUVERTS =
            EnumSet.of(StatutPaiement.INITIE, StatutPaiement.EN_COURS);

    /** Index de V51 : au plus une tentative ouverte par commande. */
    static final String INDEX_TENTATIVE_OUVERTE = "uq_paiement_tentative_ouverte";

    private final PaiementRepository paiements;
    private final CommandeRepository commandes;
    private final PieceRepository pieces;
    private final PrestatairePaiement prestataire;
    private final ApplicationEventPublisher evenements;
    private final TransactionOperations transaction;
    private final Clock horloge;
    private final String urlPublique;

    public PaiementService(PaiementRepository paiements,
                           CommandeRepository commandes,
                           PieceRepository pieces,
                           PrestatairePaiement prestataire,
                           ApplicationEventPublisher evenements,
                           TransactionOperations transaction,
                           Clock horloge,
                           @Value("${autoservplus.url-publique}") String urlPublique) {
        this.paiements = paiements;
        this.commandes = commandes;
        this.pieces = pieces;
        this.prestataire = prestataire;
        this.evenements = evenements;
        this.transaction = transaction;
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
     * Envoie le membre vers une page de paiement de sa commande, en reprenant la
     * tentative ouverte plutôt qu'en créant une seconde (F14).
     *
     * <p>Chaque tentative créée est un paiement chez le prestataire. Entre un paiement
     * effectif et sa notification, la commande attend toujours, le bouton « payer »
     * reste affiché, et une seconde tentative encaisserait deux fois. Les tentatives
     * encore ouvertes en base sont donc relues chez le prestataire d'abord, et l'état
     * relu s'applique par le même chemin que la notification :</p>
     * <ul>
     *   <li>encaissée : la commande passe PAYEE, facture comprise, et rien n'est créé.</li>
     *   <li>en cours de traitement, sans page de paiement : refus, le membre attend
     *       la notification.</li>
     *   <li>ouverte : le membre retrouve la page de cette tentative.</li>
     *   <li>échouée, annulée ou expirée : une nouvelle tentative est créée.</li>
     * </ul>
     *
     * <p>Trois transactions, et non une. La relecture se fait hors transaction,
     * pour ne pas tenir une connexion à la base pendant l'aller-retour, ce que la
     * notification fait encore (dette V2, voir {@link EtatPaiement}). La dernière
     * transaction verrouille la commande, comme le webhook et le job RM-21, puis
     * revérifie l'absence de tentative ouverte avant d'en créer une : deux gestes
     * simultanés du membre n'en ouvrent qu'une. La création chez le prestataire reste
     * dans cette transaction, comme avant ce changement.</p>
     *
     * <p>{@code SUPPORTS} plutôt que la lecture seule de la classe : appelée par le
     * contrôleur, la méthode ne tourne dans aucune transaction et chaque bloc ouvre la
     * sienne. Une transaction appelante, celle d'un test par exemple, est rejointe et
     * non suspendue, sans quoi les données qu'elle n'a pas validées seraient
     * invisibles.</p>
     */
    @Transactional(propagation = Propagation.SUPPORTS)
    @PreAuthorize("isAuthenticated()")
    public DepartPaiement initierPaiement(UUID referenceCommande, String email) {
        List<String> ouvertes = transaction.execute(statut -> {
            Commande commande = commandeDuMembre(referenceCommande, email);
            exigerAttenteDePaiement(commande);
            return paiements.findByCommandeAndStatutIn(commande, OUVERTS).stream()
                    .sorted(Comparator.comparing(Paiement::getDateInitiation)
                            .thenComparing(Paiement::getId).reversed())
                    .map(Paiement::getReferenceMollie)
                    .filter(reference -> reference != null && !reference.isBlank())
                    .toList();
        });

        boolean enCours = false;
        String pageOuverte = null;
        for (String reference : ouvertes) {
            EtatPaiement etat = prestataire.lireEtat(reference);
            transaction.execute(statut -> appliquer(paiementPrestataire(reference), etat));
            switch (etat.statut()) {
                case EN_COURS -> enCours = true;
                case INITIE -> {
                    if (etat.urlPaiement() == null) {
                        // Ouverte mais sans page : rien à reprendre, et rien ne dit
                        // qu'elle ne va pas aboutir. Elle bloque comme une tentative
                        // en cours plutôt que d'en laisser ouvrir une seconde.
                        enCours = true;
                    } else if (pageOuverte == null) {
                        pageOuverte = etat.urlPaiement();
                    }
                }
                case REUSSI, ECHOUE, EXPIRE, REMBOURSE -> {
                    // Close : la dernière transaction constate la commande payée, ou
                    // crée une nouvelle tentative.
                }
            }
        }

        boolean bloquee = enCours;
        String reprise = pageOuverte;
        try {
            return decider(referenceCommande, email, bloquee, reprise);
        } catch (DataIntegrityViolationException violation) {
            if (!violeLaTentativeOuverteUnique(violation)) {
                throw violation;
            }
            // Filet de l'index uq_paiement_tentative_ouverte : une tentative a été
            // insérée sans passer par le verrou de la commande. Le paiement créé chez le
            // prestataire reste ouvert sans que personne y soit envoyé, et expire seul.
            log.warn("Tentative de paiement refusée par l'index pour la commande {} : une "
                    + "autre tentative est ouverte.", referenceCommande);
            return DepartPaiement.sans(DepartPaiement.Issue.TENTATIVE_CONCURRENTE);
        }
    }

    private DepartPaiement decider(UUID referenceCommande, String email, boolean bloquee,
                                   String reprise) {
        return transaction.execute(statut -> {
            Commande commande = commandes.verrouillerParId(
                            commandeDuMembre(referenceCommande, email).getId())
                    .orElseThrow(() -> new RessourceIntrouvableException(
                            "Commande", referenceCommande));
            if (commande.getStatut() == StatutCommande.PAYEE) {
                return DepartPaiement.sans(DepartPaiement.Issue.DEJA_PAYEE);
            }
            exigerAttenteDePaiement(commande);
            if (bloquee) {
                return DepartPaiement.sans(DepartPaiement.Issue.EN_COURS);
            }
            if (reprise != null) {
                return DepartPaiement.vers(reprise);
            }
            if (!paiements.findByCommandeAndStatutIn(commande, OUVERTS).isEmpty()) {
                // Ouverte entre la relecture et le verrou, par un autre geste du membre.
                return DepartPaiement.sans(DepartPaiement.Issue.TENTATIVE_CONCURRENTE);
            }
            if (commande.delaiDePaiementDepasse(horloge.instant(),
                    ExpirationCommandesJob.DELAI_PAIEMENT)) {
                // Passé l'échéance RM-21, le job n'attend plus que les tentatives déjà
                // ouvertes. En créer une à chaque échec ferait vivre la commande sans fin.
                return DepartPaiement.sans(DepartPaiement.Issue.DELAI_DEPASSE);
            }
            return DepartPaiement.vers(creerTentative(commande));
        });
    }

    private String creerTentative(Commande commande) {
        Paiement paiement = new Paiement(commande, commande.getMontantTvac(), horloge.instant());
        PaiementCree cree = prestataire.creerPaiement(new DemandePaiement(
                commande.getNumero(), commande.getMontantTvac(),
                paiement.getDevise(), paiement.getCleIdempotence(),
                urlRetour(commande.getReference()), urlNotification()));
        paiement.enregistrerReferencePrestataire(cree.referencePrestataire());
        paiements.saveAndFlush(paiement);
        return cree.urlRedirection();
    }

    /** La violation vient-elle de l'index d'unicité de la tentative ouverte (V51) ? */
    static boolean violeLaTentativeOuverteUnique(Throwable erreur) {
        for (Throwable cause = erreur; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException contrainte
                    && INDEX_TENTATIVE_OUVERTE.equalsIgnoreCase(contrainte.getConstraintName())) {
                return true;
            }
        }
        return false;
    }

    private static void exigerAttenteDePaiement(Commande commande) {
        if (commande.getStatut() != StatutCommande.EN_ATTENTE_PAIEMENT) {
            throw new PaiementImpossibleException();
        }
    }

    // --- retour du membre depuis la page du prestataire --------------------------------

    /**
     * Constate l issue du paiement au retour du membre, et rend la situation du
     * paiement a l issue de ce constat : payee, en cours de traitement, echouee, ou
     * encore a payer apres un abandon.
     *
     * <p><b>Le retour n est PAS une preuve de paiement</b>, et rien ici ne le traite
     * comme tel. Le prestataire y renvoie le membre quelle que soit l issue (y compris
     * apres un abandon pur et simple) et l adresse est une URL que
     * n importe qui peut ouvrir. Ce qui tranche reste ce qui tranchait deja : le
     * statut RELU chez le prestataire par {@link #traiterNotification}. Le retour ne
     * fait que declencher cette relecture plus tot.</p>
     *
     * <p><b>Pourquoi ce declenchement est necessaire et non redondant.</b> La
     * notification serveur a serveur n arrive pas toujours : elle n est meme pas
     * demandee quand le site n est pas joignable depuis l exterieur, ce qui est le cas
     * de tout poste de developpement. Sans cette reconciliation, la commande resterait
     * EN_ATTENTE_PAIEMENT apres un paiement reussi, puis serait annulee par le job
     * RM-21 au bout de trente minutes, soit un encaissement reel sans commande en face.</p>
     *
     * <p><b>Aucun risque de double effet</b> : l appel emprunte exactement le chemin du
     * webhook, dont l idempotence vient de l etat sous verrou pessimiste. Webhook et
     * retour peuvent donc arriver dans n importe quel ordre, ou tous les deux : la
     * facture n est emise qu une fois, sur la transition reelle, et la numerotation
     * continue sans trou n est pas plus exposee qu avant.</p>
     *
     * <p>Cette absence de double effet porte sur le traitement d'une notification, pas
     * sur l'initiation : relire un paiement ne dit rien des autres tentatives de la
     * commande. C'est {@link #initierPaiement} qui refuse d'en ouvrir une seconde tant
     * qu'une tentative peut encore aboutir.</p>
     */
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public SituationPaiement constaterRetour(UUID referenceCommande, String email) {
        Commande commande = commandeDuMembre(referenceCommande, email);
        Optional<Paiement> derniere = paiements.findFirstByCommandeOrderByDateInitiationDescIdDesc(commande);
        derniere.map(Paiement::getReferenceMollie)
                // Une tentative sans reference prestataire n a jamais quitte le site :
                // il n y a rien a relire chez lui.
                .filter(reference -> reference != null && !reference.isBlank())
                .ifPresentOrElse(
                        reference -> journaliserRetour(commande, traiterNotification(reference)),
                        () -> log.info("Retour du membre pour la commande {} : statut relu chez "
                                        + "le prestataire = aucun, aucune tentative n a quitte "
                                        + "le site.",
                                commande.getNumero()));
        // La commande et la tentative sont les entités gérées que la relecture vient de
        // faire évoluer : leurs statuts sont ceux qu'elle a constatés.
        return SituationPaiement.de(commande.getStatut(),
                derniere.map(Paiement::getStatut).orElse(null),
                commande.delaiDePaiementDepasse(horloge.instant(),
                        ExpirationCommandesJob.DELAI_PAIEMENT));
    }

    /**
     * Trace du retour du membre, a lire cote a cote avec celle de la notification
     * serveur a serveur ecrite par le controleur de webhook.
     *
     * <p>Le numero de commande, et non sa reference technique : c est lui qui figure
     * sur la facture et dans les autres traces de ce service, donc le seul identifiant
     * par lequel l exploitant relie une ligne de journal a un dossier. Ni montant, ni
     * adresse, ni identifiant d acces : une trace d exploitation n a pas besoin de
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
     *         des deux declencheurs (retour du membre, notification serveur a serveur)
     *         redige SA ligne de journal. Rendre l information plutot que de l ecrire
     *         ici est ce qui permet de distinguer les deux chemins, que
     *         {@code docs/deploiement.md} demande de voir arriver tour a tour. Une
     *         ligne ecrite au fond de cette methode serait identique dans les deux cas.
     */
    @Transactional
    public IssueRelecture traiterNotification(String referencePrestataire) {
        Paiement paiement = paiementPrestataire(referencePrestataire);
        // Strategie securite §11 : seul l etat relu chez le prestataire fait foi.
        return appliquer(paiement, prestataire.lireEtat(referencePrestataire));
    }

    /**
     * Applique un état que l'appelant vient de lire chez le prestataire, par le chemin
     * de la notification. Le job RM-21 lit hors transaction, pour ne pas tenir une
     * connexion pendant l'aller-retour, puis rend ici ce qu'il a lu.
     */
    @Transactional
    public IssueRelecture constater(String referencePrestataire, EtatPaiement etat) {
        return appliquer(paiementPrestataire(referencePrestataire), etat);
    }

    private Paiement paiementPrestataire(String referencePrestataire) {
        return paiements.findByReferenceMollie(referencePrestataire)
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "Paiement", referencePrestataire));
    }

    /**
     * Applique un état relu chez le prestataire, dans la transaction de l'appelant :
     * celle de la notification, ou celle qu'ouvre la reprise d'une tentative après une
     * relecture faite hors transaction.
     */
    private IssueRelecture appliquer(Paiement paiement, EtatPaiement etat) {
        String referencePrestataire = paiement.getReferenceMollie();
        // Le moyen arrive dans la meme reponse que le statut, et se pose des qu il est
        // connu, y compris sur un paiement qui n a pas encore abouti. L entite refuse
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
     * serialise ce traitement avec le job d expiration et les webhooks rejoues.
     * L etat relu sous verrou decide ensuite, une fois pour toutes.
     */
    private IssueRelecture.Effet confirmer(Paiement paiement) {
        Commande commande = commandes.verrouillerParId(paiement.getCommande().getId())
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "Commande", paiement.getCommande().getId()));

        if (commande.getStatut() == StatutCommande.PAYEE) {
            // Webhook rejoue : tout est deja fait. On aligne au besoin le paiement
            // sur la realite, et rien d autre : ni stock, ni evenement.
            confirmerLePaiementSeul(paiement);
            return IssueRelecture.Effet.DEJA_TRAITE;
        }
        if (commande.getStatut() == StatutCommande.ANNULEE) {
            return encaisserApresAnnulation(paiement, commande);
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

    /**
     * L'encaissement est réel mais la commande est annulée, et ne ressuscite pas : le
     * stock et les prix ont pu changer depuis. Le paiement est enregistré comme reçu
     * après annulation, le garage et le membre sont prévenus une fois, et un
     * administrateur le rembourse depuis le back-office. Le job RM-21 lit le statut
     * chez le prestataire avant d'annuler : ce cas reste celui d'une course entre sa
     * lecture et la notification, ou d'une tentative close avant ce changement.
     */
    private IssueRelecture.Effet encaisserApresAnnulation(Paiement paiement, Commande commande) {
        if (paiement.getStatut() == StatutPaiement.REUSSI
                || paiement.getStatut() == StatutPaiement.REMBOURSE) {
            // Notification rejouée : déjà enregistré, déjà signalé.
            return IssueRelecture.Effet.DEJA_TRAITE;
        }
        paiement.constaterEncaissementApresAnnulation(horloge.instant());
        paiements.saveAndFlush(paiement);
        log.warn("Paiement {} encaissé sur la commande {} déjà annulée : à rembourser "
                        + "depuis le back-office.",
                paiement.getReference(), commande.getNumero());
        evenements.publishEvent(new PaiementApresAnnulationEvent(
                paiement.getReference(), commande.getReference()));
        return IssueRelecture.Effet.ENCAISSE_APRES_ANNULATION;
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
     * (FOR UPDATE, ordre deterministe par id, les lignes arrivent triees) puis
     * decrementee. Si le stock est devenu insuffisant (un autre membre a paye la
     * meme piece entre-temps, rien n etait reserve) la commande reste PAYEE (on
     * n annule pas un encaissement), le stock plancher a 0, le drapeau
     * {@code rupture_a_honorer} leve l alerte et la trace ci-dessous dit QUOI
     * honorer. Le garage gere hors ligne.
     */
    private void decrementerStock(Commande commande) {
        List<LignePanier> lignes = commandes.lignesDe(commande);
        List<String> ruptures = new ArrayList<>();
        for (LignePanier ligne : lignes) {
            // Ceinture et bretelles. lignesDe ne rend deja QUE les lignes de piece, car
            // son JOIN FETCH est une jointure interne. Ce garde n est pas
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
        if (!commande.getMembre().getEmail().equals(email)) {
            throw new RessourceIntrouvableException("Commande", reference);
        }
        return commande;
    }
}
