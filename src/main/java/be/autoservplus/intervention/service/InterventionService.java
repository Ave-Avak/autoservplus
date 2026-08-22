package be.autoservplus.intervention.service;

import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.repository.PrestationRepository;
import be.autoservplus.common.exception.ConflitConcurrenceException;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.communication.service.DetailsDepassementCourriel;
import be.autoservplus.communication.service.DetailsRdvCourriel;
import be.autoservplus.communication.service.ServiceCourriel;
import be.autoservplus.identite.service.AuteurCourant;
import be.autoservplus.intervention.domain.HistoriqueStatutIntervention;
import be.autoservplus.intervention.domain.Intervention;
import be.autoservplus.intervention.domain.LigneIntervention;
import be.autoservplus.intervention.domain.StatutIntervention;
import be.autoservplus.intervention.repository.HistoriqueStatutInterventionRepository;
import be.autoservplus.intervention.repository.InterventionRepository;
import be.autoservplus.intervention.web.dto.DemandeValidationVue;
import be.autoservplus.intervention.web.dto.InterventionVueAdmin;
import be.autoservplus.intervention.web.dto.InterventionVueMembre;
import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.repository.ParametreAtelierRepository;
import be.autoservplus.reservation.service.support.FormatageRdv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Gestion administrative des interventions : creation depuis un RDV honore
 * (idempotente), transitions gardees, edition des lignes et du commentaire
 * client, vues pour le tableau de bord garage.
 *
 * <p>Point d ancrage de la creation automatique : {@link #creerDepuisRdv} est
 * appele inline dans {@code AdminRdvService.marquerHonore}, dans la meme
 * transaction : le passage HONORE et l existence de l intervention sont
 * atomiques.</p>
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasRole('ADMINISTRATEUR')")
public class InterventionService {

    private static final Logger log = LoggerFactory.getLogger(InterventionService.class);

    private final InterventionRepository interventions;
    private final HistoriqueStatutInterventionRepository historiques;
    private final PrestationRepository prestations;
    private final ParametreAtelierRepository parametres;
    private final AuteurCourant auteurCourant;
    private final GenerateurNumeroIntervention numeros;
    private final ServiceCourriel courriel;
    private final ApplicationEventPublisher evenements;
    private final Clock horloge;

    public InterventionService(InterventionRepository interventions,
                               HistoriqueStatutInterventionRepository historiques,
                               PrestationRepository prestations,
                               ParametreAtelierRepository parametres,
                               AuteurCourant auteurCourant,
                               GenerateurNumeroIntervention numeros,
                               ServiceCourriel courriel,
                               ApplicationEventPublisher evenements,
                               Clock horloge) {
        this.interventions = interventions;
        this.historiques = historiques;
        this.prestations = prestations;
        this.parametres = parametres;
        this.auteurCourant = auteurCourant;
        this.numeros = numeros;
        this.courriel = courriel;
        this.evenements = evenements;
        this.horloge = horloge;
    }

    // --- creation depuis un RDV (idempotente) -----------------------------------------

    /**
     * Cree l intervention correspondant au RDV, ou retourne celle qui existe deja.
     * Idempotent : un second appel pour le meme RDV ne cree pas de doublon.
     * Appele par {@code AdminRdvService.marquerHonore} dans la meme transaction.
     */
    @Transactional
    public Intervention creerDepuisRdv(Rdv rdv) {
        return interventions.findByRdvId(rdv.getId())
                .orElseGet(() -> {
                    Intervention creee = ecrire(new Intervention(numeros.prochain(), rdv));
                    // Ligne de naissance de la chronologie (F17) : pas d etat anterieur.
                    // Dans la branche orElseGet uniquement — un second appel idempotent
                    // ne doit pas inventer une seconde naissance.
                    historiser(creee, null, StatutIntervention.PLANIFIEE, null);
                    return creee;
                });
    }

    // --- transitions -----------------------------------------------------------------
    //
    // Chaque transition ecrit sa ligne de chronologie (F17) dans la meme transaction :
    // l etat AVANT est capture avant l appel au domaine, qui peut refuser (auquel cas
    // rien n est historise — l exception sort avant historiser). Un echec ulterieur de
    // l ecriture annule transition ET chronologie ensemble : elles ne divergent jamais.

    @Transactional
    public Intervention demarrer(UUID reference) {
        Intervention it = charger(reference);
        StatutIntervention avant = it.getStatut();
        it.demarrer(horloge.instant());
        historiser(it, avant, it.getStatut(), null);
        return ecrire(it);
    }

    @Transactional
    public Intervention suspendre(UUID reference) {
        Intervention it = charger(reference);
        StatutIntervention avant = it.getStatut();
        it.suspendre();
        historiser(it, avant, it.getStatut(), null);
        return ecrire(it);
    }

    @Transactional
    public Intervention reprendre(UUID reference) {
        Intervention it = charger(reference);
        StatutIntervention avant = it.getStatut();
        it.reprendre();
        historiser(it, avant, it.getStatut(), null);
        return ecrire(it);
    }

    /**
     * Terminaison des travaux. Publie {@link InterventionTermineeEvent} : le courriel
     * « venez recuperer votre vehicule » (F17) part apres commit, via le listener
     * {@code AFTER_COMMIT} — jamais pendant la transaction, ou un rollback annoncerait
     * une cloture qui n a pas eu lieu. Seule cette transition publie : c est la seule
     * qui appelle le membre a se deplacer.
     */
    @Transactional
    public Intervention terminer(UUID reference) {
        Intervention it = charger(reference);
        StatutIntervention avant = it.getStatut();
        it.terminer(horloge.instant());
        historiser(it, avant, it.getStatut(), null);
        Intervention enregistre = ecrire(it);
        evenements.publishEvent(new InterventionTermineeEvent(enregistre.getReference()));
        return enregistre;
    }

    /**
     * Annulation definitive. Depuis PLANIFIEE, EN_COURS, SUSPENDUE ou
     * ATTENTE_VALIDATION_MEMBRE ; refusee si l intervention est deja terminale.
     */
    @Transactional
    public Intervention annuler(UUID reference) {
        Intervention it = charger(reference);
        StatutIntervention avant = it.getStatut();
        it.annuler();
        historiser(it, avant, it.getStatut(), null);
        return ecrire(it);
    }

    // --- edition des lignes et du commentaire ----------------------------------------

    @Transactional
    public Intervention modifierCommentaireAdmin(UUID reference, String texte) {
        Intervention it = charger(reference);
        it.modifierCommentaireAdmin(texte);
        return ecrire(it);
    }

    /**
     * Ajoute une prestation au dossier. Si l ajout porte le total au-dela du devis
     * majore de 10 %, l entite bascule d elle-meme en ATTENTE_VALIDATION_MEMBRE
     * (RM-15) ; le service se contente de constater la bascule et de prevenir le
     * membre. La regle vit dans le domaine, pas ici.
     */
    @Transactional
    public LigneIntervention ajouterLigneMainOeuvre(UUID interventionRef, UUID prestationRef, short quantite) {
        Intervention it = charger(interventionRef);
        Prestation prestation = prestations.findByReference(prestationRef)
                .orElseThrow(() -> new RessourceIntrouvableException("Prestation", prestationRef));
        StatutIntervention avant = it.getStatut();
        LigneIntervention ligne = it.ajouterLigneMainOeuvre(prestation, quantite,
                prestation.getPrixHtva(), prestation.getTauxTva());
        // La bascule RM-15 est une transition comme une autre : si l entite a change
        // d etat d elle-meme, la chronologie (F17) le consigne — sinon l historique
        // aurait des trous (un EN_COURS -> EN_COURS apparent) et perdrait sa coherence.
        if (it.getStatut() != avant) {
            historiser(it, avant, it.getStatut(), null);
        }
        Intervention enregistre = ecrire(it);
        if (avant != StatutIntervention.ATTENTE_VALIDATION_MEMBRE
                && enregistre.getStatut() == StatutIntervention.ATTENTE_VALIDATION_MEMBRE) {
            notifierDepassementSansEchouer(enregistre);
        }
        return ligne;
    }

    @Transactional
    public void retirerLigne(UUID interventionRef, Long ligneId) {
        Intervention it = charger(interventionRef);
        if (!it.retirerLigne(ligneId)) {
            throw new RessourceIntrouvableException("LigneIntervention", ligneId);
        }
        ecrire(it);
    }

    // --- vues ------------------------------------------------------------------------

    /**
     * Interventions ouvertes (non terminales) pour le tableau de bord admin :
     * PLANIFIEE, EN_COURS, SUSPENDUE et ATTENTE_VALIDATION_MEMBRE. Les TERMINEE
     * et ANNULEE sortent de l ecran actif.
     */
    public List<InterventionVueAdmin> interventionsEnCours() {
        ZoneId zone = parametres.courants().zone();
        return interventions.findByStatutIn(EnumSet.of(
                        StatutIntervention.PLANIFIEE,
                        StatutIntervention.EN_COURS,
                        StatutIntervention.SUSPENDUE,
                        StatutIntervention.ATTENTE_VALIDATION_MEMBRE))
                .stream()
                .map(i -> InterventionVueAdmin.de(i, zone))
                .toList();
    }

    public InterventionVueAdmin vueAdmin(UUID reference) {
        ZoneId zone = parametres.courants().zone();
        return InterventionVueAdmin.de(charger(reference), zone);
    }

    /** Prestations actives, pour peupler le formulaire d ajout de ligne. */
    public List<Prestation> prestationsActives() {
        return prestations.findByActifTrueOrderByLibelleAsc();
    }

    // --- vue membre (accessible a tout utilisateur authentifie, ownership verifie) ----

    /**
     * Vue destinee au membre proprietaire du RDV lie a l intervention. Le
     * {@code @PreAuthorize} au niveau methode surcharge celui de la classe
     * (ADMINISTRATEUR) : tout membre authentifie peut appeler, l ownership est
     * verifie par comparaison d email. Une intervention d autrui remonte comme
     * {@link RessourceIntrouvableException} (404, meme code qu une reference
     * inconnue) pour ne pas confirmer l existence de la reference.
     */
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public InterventionVueMembre interventionDuMembre(UUID reference, String email) {
        Intervention it = chargerPourMembre(reference, email);
        // La chronologie (F17) est chargee ici, dans la transaction readOnly, et
        // convertie par le DTO : meme controle d ownership que le reste de la vue.
        return InterventionVueMembre.de(it,
                historiques.findByInterventionOrderByHorodatageAscIdAsc(it),
                parametres.courants().zone());
    }

    // --- RM-15 : validation du depassement par le membre -----------------------------

    /**
     * Detail du depassement soumis au membre. Refuse si aucune reponse n est attendue :
     * on ne presente pas un ecran de decision sur une intervention qui n a rien demande.
     */
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public DemandeValidationVue demandeValidation(UUID reference, String email) {
        Intervention it = chargerPourMembre(reference, email);
        if (it.getStatut() != StatutIntervention.ATTENTE_VALIDATION_MEMBRE) {
            throw new RessourceIntrouvableException("Demande de validation", reference);
        }
        return DemandeValidationVue.de(it);
    }

    @Transactional
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public Intervention validerDepassement(UUID reference, String email) {
        Intervention it = chargerPourMembre(reference, email);
        StatutIntervention avant = it.getStatut();
        it.validerDepassement();
        historiser(it, avant, it.getStatut(), null);
        return ecrire(it);
    }

    @Transactional
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public Intervention refuserDepassement(UUID reference, String email) {
        Intervention it = chargerPourMembre(reference, email);
        StatutIntervention avant = it.getStatut();
        it.refuserDepassement();
        historiser(it, avant, it.getStatut(), null);
        return ecrire(it);
    }

    /**
     * Resolution RDV -> intervention pour le lien depuis la fiche RDV membre.
     * Renvoie la reference de l intervention si elle existe et appartient au membre,
     * sinon {@link RessourceIntrouvableException} (404).
     */
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public UUID referenceParRdvDuMembre(UUID rdvReference, String email) {
        Intervention it = interventions.findByRdvReference(rdvReference)
                .orElseThrow(() -> new RessourceIntrouvableException("Intervention pour RDV", rdvReference));
        if (!it.getRdv().getMembre().getEmail().equalsIgnoreCase(email)) {
            throw new RessourceIntrouvableException("Intervention pour RDV", rdvReference);
        }
        return it.getReference();
    }

    // --- helpers ---------------------------------------------------------------------

    /**
     * Ecrit une ligne de chronologie (F17) : la transition telle qu elle vient de se
     * produire, horodatee par l horloge injectee. L auteur vient de
     * {@link AuteurCourant}, donc du contexte de securite — jamais d un parametre de
     * requete — et vaut {@code null} pour un traitement sans utilisateur authentifie.
     */
    private void historiser(Intervention it, StatutIntervention avant,
                            StatutIntervention apres, String motif) {
        historiques.save(new HistoriqueStatutIntervention(
                it, avant, apres, horloge.instant(), auteurCourant.resoudre(), motif));
    }

    private Intervention charger(UUID reference) {
        return interventions.findByReference(reference)
                .orElseThrow(() -> new RessourceIntrouvableException("Intervention", reference));
    }

    /**
     * Charge une intervention en verifiant qu elle appartient bien au membre. Une
     * intervention d autrui remonte comme {@link RessourceIntrouvableException} (404,
     * meme code qu une reference inconnue) pour ne pas confirmer l existence de la
     * reference a un tiers.
     */
    private Intervention chargerPourMembre(UUID reference, String email) {
        Intervention it = charger(reference);
        if (it.getRdv() == null
                || !it.getRdv().getMembre().getEmail().equalsIgnoreCase(email)) {
            throw new RessourceIntrouvableException("Intervention", reference);
        }
        return it;
    }

    /**
     * Previent le membre qu un accord est attendu (RM-15). Comme
     * {@code AdminRdvService}, l envoi est inline et avale les {@link RuntimeException}
     * du fournisseur : la bascule est deja persistee, un fournisseur mail indisponible
     * ne doit pas la faire disparaitre. Le membre garde l ecran de validation, visible
     * depuis son suivi, comme second canal.
     */
    private void notifierDepassementSansEchouer(Intervention it) {
        if (it.getRdv() == null) {
            // Entree directe au garage (hors V1) : aucun membre a prevenir. La bascule
            // reste valable, le garage traitera l accord hors ligne.
            log.warn("Depassement de devis sur l intervention {} sans RDV lie : aucun membre a notifier.",
                    it.getNumero());
            return;
        }
        ZoneId zone = parametres.courants().zone();
        Rdv rdv = it.getRdv();
        try {
            courriel.envoyerDemandeValidationDepassement(
                    rdv.getMembre(),
                    new DetailsRdvCourriel(rdv.getNumero(),
                            FormatageRdv.jourLisible(rdv.getDebut(), zone),
                            FormatageRdv.heureLisible(rdv.getDebut(), zone)),
                    new DetailsDepassementCourriel(
                            it.getNumero(),
                            FormatageRdv.euros(it.devisReferenceHtva()),
                            FormatageRdv.euros(it.totalProposeHtva()),
                            it.lignesEnAttente().stream()
                                    .map(l -> "- %s x%d : %s"
                                            .formatted(l.getLibelleFige(), l.getQuantite(),
                                                    FormatageRdv.euros(l.totalHtva())))
                                    .toList(),
                            "/mes-interventions/" + it.getReference() + "/validation"));
        } catch (RuntimeException e) {
            log.warn("Envoi de la demande de validation de depassement impossible pour {} : {}",
                    it.getNumero(), e.getMessage());
        }
    }

    private Intervention ecrire(Intervention it) {
        try {
            return interventions.saveAndFlush(it);
        } catch (OptimisticLockingFailureException e) {
            throw new ConflitConcurrenceException(
                    "Cette intervention a été mise à jour par un autre administrateur, rechargez la page.");
        }
    }
}
