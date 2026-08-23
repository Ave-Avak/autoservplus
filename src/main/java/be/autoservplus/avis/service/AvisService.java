package be.autoservplus.avis.service;

import be.autoservplus.avis.domain.Avis;
import be.autoservplus.avis.repository.AvisRepository;
import be.autoservplus.avis.service.dto.AvisVue;
import be.autoservplus.avis.service.dto.SyntheseAvis;
import be.autoservplus.common.exception.RegleMetierException;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.identite.domain.StatutUtilisateur;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.intervention.domain.Intervention;
import be.autoservplus.intervention.domain.StatutIntervention;
import be.autoservplus.intervention.repository.InterventionRepository;
import be.autoservplus.notification.domain.TypeNotification;
import be.autoservplus.notification.service.NotificationService;
import be.autoservplus.reservation.repository.ParametreAtelierRepository;
import be.autoservplus.reservation.service.support.FormatageRdv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Depot et consultation des avis (BL-4), cote membre et cote public.
 *
 * <p><b>Trois conditions au depot</b>, toutes verifiees avant la premiere ecriture :
 * l intervention appartient au demandeur, elle est TERMINEE, et elle n a pas deja son
 * avis. La derniere est doublee par {@code uq_avis_intervention} en base — le controle
 * applicatif donne un message utile, l index empeche la course entre deux onglets.</p>
 *
 * <p><b>L intervention d autrui remonte en {@link RessourceIntrouvableException}</b>,
 * donc 404 et non 403 : repondre « interdit » confirmerait que la reference existe.</p>
 *
 * <p><b>La consultation publique n est pas gardee</b> : la fiche prestation est
 * accessible sans compte ({@code /services/**} est en {@code permitAll}), et ses avis
 * en font partie. Seules les methodes qui engagent un membre portent
 * {@code @PreAuthorize}.</p>
 */
@Service
@Transactional(readOnly = true)
public class AvisService {

    private static final Logger log = LoggerFactory.getLogger(AvisService.class);

    private final AvisRepository avis;
    private final InterventionRepository interventions;
    private final UtilisateurRepository membres;
    private final ParametreAtelierRepository parametres;
    private final NotificationService notifications;
    private final Clock horloge;

    public AvisService(AvisRepository avis,
                       InterventionRepository interventions,
                       UtilisateurRepository membres,
                       ParametreAtelierRepository parametres,
                       NotificationService notifications,
                       Clock horloge) {
        this.avis = avis;
        this.interventions = interventions;
        this.membres = membres;
        this.parametres = parametres;
        this.notifications = notifications;
        this.horloge = horloge;
    }

    // --- consultation publique ---------------------------------------------------------

    /** Note moyenne et volume d avis publies d une prestation. */
    public SyntheseAvis synthese(UUID referencePrestation) {
        SyntheseAvis synthese = avis.syntheseParPrestation(referencePrestation);
        return synthese == null ? SyntheseAvis.vide() : synthese;
    }

    /** Avis publies d une prestation, du plus recent au plus ancien. */
    public List<AvisVue> publiesPour(UUID referencePrestation) {
        ZoneId zone = parametres.courants().zone();
        return avis.publiesPourPrestation(referencePrestation).stream()
                .map(a -> AvisVue.de(a, FormatageRdv.jourLisible(a.getDateDepot(), zone)))
                .toList();
    }

    // --- depot par le membre -----------------------------------------------------------

    /**
     * Indique si le membre peut encore deposer un avis sur cette intervention. Sert a
     * n afficher le lien que lorsqu il menera quelque part — le controle reel reste
     * fait au depot, un bouton absent n etant pas une securite.
     */
    @PreAuthorize("isAuthenticated()")
    public boolean peutDeposer(String email, UUID referenceIntervention) {
        Intervention intervention = interventions.findByReference(referenceIntervention)
                .orElse(null);
        return intervention != null
                && appartientA(intervention, email)
                && intervention.getStatut() == StatutIntervention.TERMINEE
                && !avis.existsByIntervention(intervention);
    }

    /**
     * Charge l intervention notable du membre, en refusant tout ce qui ne l est pas.
     *
     * @throws RessourceIntrouvableException reference inconnue ou intervention d autrui
     * @throws RegleMetierException          travaux non termines, ou avis deja depose
     */
    @PreAuthorize("isAuthenticated()")
    public Intervention interventionNotable(String email, UUID referenceIntervention) {
        Intervention intervention = interventions.findByReference(referenceIntervention)
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "Intervention", referenceIntervention));
        if (!appartientA(intervention, email)) {
            throw new RessourceIntrouvableException("Intervention", referenceIntervention);
        }
        if (intervention.getStatut() != StatutIntervention.TERMINEE) {
            throw new RegleMetierException(
                    "Un avis ne peut être déposé que sur une intervention terminée.");
        }
        if (avis.existsByIntervention(intervention)) {
            throw new RegleMetierException("Vous avez déjà déposé un avis sur cette intervention.");
        }
        return intervention;
    }

    /**
     * Depose l avis du membre sur son intervention terminee.
     *
     * <p>Le garage est prevenu par une notification in-app (BL-6) : un avis appelle une
     * reaction, et sans signalement il ne serait decouvert qu au hasard d une
     * consultation. La notification part <b>dans la meme transaction</b>, contrairement
     * aux notifications d evenement : il n y a pas d evenement ici, le depot est
     * l action elle-meme, et un echec doit annuler l ensemble plutot que laisser un
     * avis silencieux.</p>
     */
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public Avis deposer(String email, UUID referenceIntervention, short note, String commentaire) {
        Utilisateur membre = membre(email);
        Intervention intervention = interventionNotable(email, referenceIntervention);

        Avis depose = avis.save(
                new Avis(membre, intervention, note, commentaire, horloge.instant()));
        prevenirLeGarage(intervention.getNumero());
        return depose;
    }

    // --- helpers privees ---------------------------------------------------------------

    /**
     * Le titulaire est celui du rendez-vous. Une intervention creee sans rendez-vous
     * n a pas de titulaire identifiable : personne ne peut la noter.
     */
    private boolean appartientA(Intervention intervention, String email) {
        return intervention.getRdv() != null
                && intervention.getRdv().getMembre().getEmail().equalsIgnoreCase(email);
    }

    private Utilisateur membre(String email) {
        return membres.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur", email));
    }

    /**
     * Notifie chaque administrateur actif. Aucun destinataire n est une situation
     * normale en demonstration (base sans administrateur) : on journalise sans lever,
     * un avis depose ne doit pas echouer faute de lecteur.
     */
    private void prevenirLeGarage(String numeroIntervention) {
        List<Utilisateur> administrateurs = membres.findByTypeUtilisateurAndStatut(
                TypeUtilisateur.ADMINISTRATEUR, StatutUtilisateur.ACTIF);
        if (administrateurs.isEmpty()) {
            log.warn("Avis depose sur l intervention {} : aucun administrateur actif a prevenir.",
                    numeroIntervention);
            return;
        }
        administrateurs.forEach(administrateur -> notifications.deposer(
                administrateur, TypeNotification.AVIS_DEPOSE, numeroIntervention));
    }
}
