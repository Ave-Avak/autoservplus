package be.autoservplus.retractation.service;

import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.communication.service.DetailsAccuseRetractationCourriel;
import be.autoservplus.communication.service.ServiceCourriel;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.intervention.domain.StatutIntervention;
import be.autoservplus.intervention.repository.InterventionRepository;
import be.autoservplus.notification.domain.TypeNotification;
import be.autoservplus.notification.service.NotificationService;
import be.autoservplus.reservation.repository.ParametreAtelierRepository;
import be.autoservplus.retractation.domain.DemandeAnnulation;
import be.autoservplus.retractation.domain.StatutDemandeAnnulation;
import be.autoservplus.retractation.repository.DemandeAnnulationRepository;
import be.autoservplus.reservation.service.support.FormatageRdv;
import be.autoservplus.retractation.service.dto.DeclarationRetractationVue;
import be.autoservplus.retractation.service.dto.RetractationVue;
import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.domain.DelaiRetractation;
import be.autoservplus.vente.domain.StatutCommande;
import be.autoservplus.vente.repository.CommandeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cote membre de la retractation (F30, RM-23) : controle d eligibilite et depot de
 * la demande.
 *
 * <p><b>Ce que le systeme peut trancher seul</b> : la commande appartient bien au
 * demandeur, elle a ete payee, le delai legal court encore, aucune demande n est
 * deja pendante. Tout le reste (la piece a-t-elle ete montee, deballee, abimee)
 * suppose un constat physique que seul l atelier peut faire, et part donc a la
 * validation administrateur ({@code AdminRetractationService}). Ce partage est la
 * raison d etre du flux en deux temps voulu par le CdC (P372-373).</p>
 *
 * <p>Point de départ du délai de quatorze jours, selon la nature de la commande
 * (CDE, art. VI.47 §2). Pour une prestation, la conclusion du contrat : le délai se
 * calcule et une demande tardive est refusée ici. Pour des pièces, la prise de
 * possession du bien, que le garage saisit au back-office comme date de remise (V59).
 * Dans les deux cas le délai se compte en jours par {@link DelaiRetractation}. Tant que la date
 * n'est pas saisie, la demande est toujours enregistrée et le garage la refuse au besoin,
 * motif à l'appui. Compter depuis la conclusion pour des pièces refuserait une
 * rétractation que la loi admet encore : la fenêtre serait plus courte que la fenêtre
 * légale, au détriment du consommateur.</p>
 *
 * <p>Le délai se compte en jours du garage, jusqu'à la dernière heure du dernier jour,
 * et non en durée de 14 fois 24 heures, qui le fermerait à l'heure de la commande.
 * L'horloge est injectée ({@link Clock}), jamais {@code Instant.now()} : c'est ce qui
 * rend la fenêtre testable à date gelée.</p>
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("isAuthenticated()")
public class RetractationService {

    private static final Logger log = LoggerFactory.getLogger(RetractationService.class);

    private final CommandeRepository commandes;
    private final DemandeAnnulationRepository demandes;
    private final InterventionRepository interventions;
    private final ServiceCourriel courriel;
    private final ParametreAtelierRepository parametres;
    private final NotificationService notifications;
    private final Clock horloge;

    public RetractationService(CommandeRepository commandes,
                               DemandeAnnulationRepository demandes,
                               InterventionRepository interventions,
                               ServiceCourriel courriel,
                               ParametreAtelierRepository parametres,
                               NotificationService notifications,
                               Clock horloge) {
        this.commandes = commandes;
        this.demandes = demandes;
        this.interventions = interventions;
        this.courriel = courriel;
        this.parametres = parametres;
        this.notifications = notifications;
        this.horloge = horloge;
    }

    /**
     * Déclaration de rétractation en ligne à confirmer (CDE, art. VI.61/2, §2) : nom du
     * consommateur, contrat visé, adresse de l'accusé de réception. Refusée d'emblée par
     * les mêmes gardes que la demande, pour qu'un membre ne remplisse pas une
     * déclaration que l'envoi refuserait.
     *
     * @throws RessourceIntrouvableException   référence inconnue, ou commande d'autrui
     * @throws RetractationImpossibleException le contrôle automatique refuse
     */
    public DeclarationRetractationVue declaration(String email, UUID referenceCommande) {
        Commande commande = commandeDuMembre(referenceCommande, email);
        MotifRefusRetractation refus = refusEventuel(commande, horloge.instant());
        if (refus != null) {
            throw new RetractationImpossibleException(refus);
        }
        Utilisateur membre = commande.getMembre();
        return new DeclarationRetractationVue(commande.getReference(), commande.getNumero(),
                FormatageRdv.eurosEcran(commande.getMontantTvac()), membre.nomComplet(),
                membre.getEmail());
    }

    /**
     * Depose une demande de retractation sur une commande du membre connecte.
     *
     * <p>L identite vient du contexte de securite et la commande est retrouvee par sa
     * reference publique : une commande qui n est pas celle du demandeur remonte
     * comme introuvable, donc 404 et jamais 403, meme mecanisme que partout ailleurs,
     * un 403 confirmerait a un tiers que cette commande existe.</p>
     *
     * @param motifMembre facultatif : le droit de retractation est inconditionnel, le
     *                    consommateur n a pas a se justifier (CDE, art. VI.47)
     * @throws RessourceIntrouvableException     reference inconnue, ou commande d autrui
     * @throws RetractationImpossibleException   le controle automatique refuse
     */
    @Transactional
    public DemandeAnnulation demander(String email, UUID referenceCommande, String motifMembre) {
        Commande commande = commandeDuMembre(referenceCommande, email);
        MotifRefusRetractation refus = refusEventuel(commande, horloge.instant());
        if (refus != null) {
            throw new RetractationImpossibleException(refus);
        }
        DemandeAnnulation demande = demandes.save(
                new DemandeAnnulation(commande, motifMembre, horloge.instant()));
        log.info("Demande de retractation {} deposee sur la commande {}.",
                demande.getReference(), commande.getNumero());
        // Accusé immédiat (VI.61/2, §4), parti après la validation de la transaction :
        // la rétractation est exercée dès sa soumission, avant toute réponse du garage.
        Utilisateur membre = commande.getMembre();
        courriel.envoyerAccuseRetractation(new DetailsAccuseRetractationCourriel(
                membre.getEmail(), membre.getPrenom(), membre.getLangue(), membre.nomComplet(),
                commande.getNumero(), motifMembre, demande.getDateDemande()));
        // Le garage est prévenu par courriel et dans le back-office (F30 : la demande est
        // « envoyée à l'administrateur pour validation »). Sans signal, la demande n'était
        // vue qu'en ouvrant la file, et le délai de remboursement courait déjà.
        courriel.envoyerAvisRetractationAuGarage(commande.getNumero(), membre.nomComplet(),
                demande.getDateDemande());
        notifications.deposerAuxAdministrateurs(TypeNotification.RETRACTATION_DEMANDEE,
                commande.getNumero());
        return demande;
    }

    /**
     * Etat de la retractation pour chaque commande du membre, indexe par reference de
     * commande : de quoi decider, ligne par ligne, s il faut afficher le bouton de
     * demande, un rappel « demande en cours », ou le lien vers la note de credit.
     *
     * <p>Une seule requete pour les commandes et une pour les demandes, jamais une
     * requete par ligne : l historique d un client fidele en compterait des dizaines.</p>
     */
    public Map<UUID, RetractationVue> etatsDuMembre(String email) {
        Instant maintenant = horloge.instant();

        // Derniere demande par commande. La requete est triee du plus ancien au plus
        // recent : ecraser au fil du parcours laisse donc la plus recente en place.
        Map<UUID, DemandeAnnulation> derniere = new HashMap<>();
        for (DemandeAnnulation demande : demandes.demandesDuMembre(email)) {
            derniere.put(demande.getCommande().getReference(), demande);
        }

        Map<UUID, RetractationVue> etats = new LinkedHashMap<>();
        for (Commande commande : commandes.historiqueDuMembre(email)) {
            etats.put(commande.getReference(),
                    vuePour(commande, derniere.get(commande.getReference()), maintenant));
        }
        return etats;
    }

    /**
     * Le meme etat, pour une seule commande : ce dont le detail d une commande a
     * besoin (F32).
     *
     * <p>Passe par {@link #vuePour} comme {@link #etatsDuMembre} : l eligibilite est
     * calculee par le <b>meme</b> {@link #refusEventuel}, et la vue construite par le
     * meme code. Recalculer la regle ici la ferait diverger de la liste au premier
     * amendement : le membre verrait un bouton sur un ecran et pas sur l autre, pour
     * la meme commande.</p>
     *
     * <p>La commande d autrui remonte en 404 via {@link #commandeDuMembre}, comme
     * partout ailleurs.</p>
     */
    public RetractationVue etatDeLaCommande(String email, UUID referenceCommande) {
        Commande commande = commandeDuMembre(referenceCommande, email);
        // historiqueDe trie du plus recent au plus ancien : le premier est la derniere
        // demande, celle que l ecran doit montrer.
        DemandeAnnulation derniere = demandes.historiqueDe(commande).stream()
                .findFirst()
                .orElse(null);
        return vuePour(commande, derniere, horloge.instant());
    }

    /** Construction unique de la vue, partagee par la liste et le detail. */
    private RetractationVue vuePour(Commande commande, DemandeAnnulation derniereDemande,
                                    Instant maintenant) {
        boolean demandable = refusEventuel(commande, maintenant) == null;
        return derniereDemande == null
                ? RetractationVue.sansDemande(commande.getReference(), demandable)
                : RetractationVue.avecDemande(derniereDemande, demandable);
    }

    /** Historique des demandes d une commande du membre (la plus recente d abord). */
    public List<DemandeAnnulation> historique(UUID referenceCommande, String email) {
        return demandes.historiqueDe(commandeDuMembre(referenceCommande, email));
    }

    /**
     * Le motif de refus du controle automatique, ou {@code null} si la commande est
     * eligible.
     *
     * <p>Un {@code null} plutot qu un {@code Optional} : la methode est privee, et le
     * seul appelant public la traduit immediatement en exception ou en booleen.
     * L ordre des gardes n est pas neutre : l etat de la commande est verifie avant
     * le delai, pour qu une commande jamais payee reponde « non payee » et non
     * « delai expire » deux semaines plus tard.</p>
     */
    /**
     * Le service a-t-il ete pleinement execute apres que le client y a renonce (F12-b) ?
     *
     * <p><b>Les deux conditions ensemble, jamais l une seule.</b> L article VI.53, 1° CDE
     * ne fait perdre le droit de retractation que si le service est <b>pleinement
     * execute</b> ET que le consommateur avait donne son accord prealable expres. Une
     * renonciation cochee sur un service pas encore commence ne prive de rien : le
     * client garde son droit tant que rien n a ete fait pour lui.</p>
     *
     * <p><b>Critere retenu pour « pleinement execute »</b> : la commande a un dossier
     * d atelier et il est en statut TERMINEE. Une commande ne portant qu une seule
     * intervention (creation idempotente, F12-b), « toutes les lignes executees »
     * revient exactement a « cette intervention est terminee ».</p>
     *
     * <p><b>Limites du critere</b>, a assumer : il repose sur la declaration du garage,
     * qui marque lui-meme TERMINEE. Le systeme ne constate pas l execution, il
     * l enregistre. Et une commande de services SANS dossier ouvert reste retractable,
     * ce qui est le comportement voulu : rien ne prouve qu on a travaille.</p>
     *
     * <p><b>On lit l ETAT, jamais la preuve.</b> {@code commande.renonciation_vi53} est
     * la donnee de decision. La ligne {@code consentement} est la trace juridique, qui
     * n a pas a etre interrogee pour trancher.</p>
     */
    private boolean serviceExecuteApresRenonciation(Commande commande) {
        if (!commande.isRenonciationVi53()) {
            return false;
        }
        return interventions.findByCommandeId(commande.getId())
                .filter(it -> it.getStatut() == StatutIntervention.TERMINEE)
                .isPresent();
    }

    /**
     * Une commande de prestations sans aucune pièce : délai depuis sa conclusion. Une
     * commande qui porte des pièces : délai depuis la date de remise, si le garage l'a
     * saisie, en jours de l'atelier. Le panier refuse de mêler les deux natures (F12-b),
     * mais la base ne l'interdit pas : une commande mixte suit la règle des pièces,
     * plutôt que d'être refusée tout entière.
     */
    private boolean delaiCalculableEtExpire(Commande commande, Instant maintenant) {
        if (!commandes.lignesDe(commande).isEmpty()) {
            return commande.getDateRemise() != null
                    && DelaiRetractation.expire(commande.getDateRemise(),
                            LocalDate.ofInstant(maintenant, parametres.courants().zone()));
        }
        if (commandes.lignesServiceDe(commande).isEmpty()) {
            return false;
        }
        ZoneId zone = parametres.courants().zone();
        return DelaiRetractation.expire(LocalDate.ofInstant(commande.getDateCommande(), zone),
                LocalDate.ofInstant(maintenant, zone));
    }

    private MotifRefusRetractation refusEventuel(Commande commande, Instant maintenant) {
        if (commande.getStatut() == StatutCommande.REMBOURSEE
                || commande.getStatut() == StatutCommande.ANNULEE) {
            return MotifRefusRetractation.COMMANDE_CLOTUREE;
        }
        if (commande.getStatut() != StatutCommande.PAYEE) {
            return MotifRefusRetractation.COMMANDE_NON_PAYEE;
        }
        if (delaiCalculableEtExpire(commande, maintenant)) {
            return MotifRefusRetractation.DELAI_EXPIRE;
        }
        if (demandes.existsByCommandeAndStatut(commande, StatutDemandeAnnulation.EN_ATTENTE)) {
            return MotifRefusRetractation.DEMANDE_DEJA_EN_COURS;
        }
        // F12-b, en DERNIER : les gardes precedentes sont moins couteuses (aucune
        // requete pour le statut et le delai) et repondent a la majorite des cas.
        if (serviceExecuteApresRenonciation(commande)) {
            return MotifRefusRetractation.SERVICE_EXECUTE_APRES_RENONCIATION;
        }
        return null;
    }

    private Commande commandeDuMembre(UUID reference, String email) {
        Commande commande = commandes.findByReference(reference)
                .orElseThrow(() -> new RessourceIntrouvableException("Commande", reference));
        if (!commande.getMembre().getEmail().equals(email)) {
            throw new RessourceIntrouvableException("Commande", reference);
        }
        return commande;
    }
}
