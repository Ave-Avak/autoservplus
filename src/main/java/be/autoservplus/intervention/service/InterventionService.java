package be.autoservplus.intervention.service;

import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.repository.PrestationRepository;
import be.autoservplus.common.exception.ConflitConcurrenceException;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.intervention.domain.Intervention;
import be.autoservplus.intervention.domain.LigneIntervention;
import be.autoservplus.intervention.domain.StatutIntervention;
import be.autoservplus.intervention.repository.InterventionRepository;
import be.autoservplus.intervention.web.dto.InterventionVueAdmin;
import be.autoservplus.intervention.web.dto.InterventionVueMembre;
import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.repository.ParametreAtelierRepository;
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

    private final InterventionRepository interventions;
    private final PrestationRepository prestations;
    private final ParametreAtelierRepository parametres;
    private final GenerateurNumeroIntervention numeros;
    private final Clock horloge;

    public InterventionService(InterventionRepository interventions,
                               PrestationRepository prestations,
                               ParametreAtelierRepository parametres,
                               GenerateurNumeroIntervention numeros,
                               Clock horloge) {
        this.interventions = interventions;
        this.prestations = prestations;
        this.parametres = parametres;
        this.numeros = numeros;
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
                .orElseGet(() -> ecrire(new Intervention(numeros.prochain(), rdv)));
    }

    // --- transitions -----------------------------------------------------------------

    @Transactional
    public Intervention demarrer(UUID reference) {
        Intervention it = charger(reference);
        it.demarrer(horloge.instant());
        return ecrire(it);
    }

    @Transactional
    public Intervention mettreEnPause(UUID reference) {
        Intervention it = charger(reference);
        it.mettreEnPause();
        return ecrire(it);
    }

    @Transactional
    public Intervention reprendre(UUID reference) {
        Intervention it = charger(reference);
        it.reprendre();
        return ecrire(it);
    }

    @Transactional
    public Intervention terminer(UUID reference) {
        Intervention it = charger(reference);
        it.terminer(horloge.instant());
        return ecrire(it);
    }

    // --- edition des lignes et du commentaire ----------------------------------------

    @Transactional
    public Intervention modifierCommentaireAdmin(UUID reference, String texte) {
        Intervention it = charger(reference);
        it.modifierCommentaireAdmin(texte);
        return ecrire(it);
    }

    @Transactional
    public LigneIntervention ajouterLigneMainOeuvre(UUID interventionRef, UUID prestationRef, short quantite) {
        Intervention it = charger(interventionRef);
        Prestation prestation = prestations.findByReference(prestationRef)
                .orElseThrow(() -> new RessourceIntrouvableException("Prestation", prestationRef));
        LigneIntervention ligne = it.ajouterLigneMainOeuvre(prestation, quantite,
                prestation.getPrixHtva(), prestation.getTauxTva());
        ecrire(it);
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

    /** Interventions ouvertes (PLANIFIEE, EN_COURS, EN_PAUSE) pour le tableau de bord. */
    public List<InterventionVueAdmin> interventionsEnCours() {
        ZoneId zone = parametres.courants().zone();
        return interventions.findByStatutIn(EnumSet.of(
                        StatutIntervention.PLANIFIEE,
                        StatutIntervention.EN_COURS,
                        StatutIntervention.EN_PAUSE))
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
        Intervention it = interventions.findByReference(reference)
                .orElseThrow(() -> new RessourceIntrouvableException("Intervention", reference));
        if (it.getRdv() == null
                || !it.getRdv().getMembre().getEmail().equalsIgnoreCase(email)) {
            throw new RessourceIntrouvableException("Intervention", reference);
        }
        return InterventionVueMembre.de(it, parametres.courants().zone());
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

    private Intervention charger(UUID reference) {
        return interventions.findByReference(reference)
                .orElseThrow(() -> new RessourceIntrouvableException("Intervention", reference));
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
