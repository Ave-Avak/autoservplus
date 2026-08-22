package be.autoservplus.reservation.service;

import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.repository.PrestationRepository;
import be.autoservplus.common.exception.RegleMetierException;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.reservation.domain.*;
import be.autoservplus.reservation.repository.ParametreAtelierRepository;
import be.autoservplus.reservation.repository.RdvRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.reservation.service.dto.CreneauDisponible;
import be.autoservplus.reservation.service.support.FormatageRdv;
import be.autoservplus.reservation.web.dto.CreneauVue;
import be.autoservplus.reservation.web.dto.RdvVue;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Prise, consultation et annulation des rendez-vous par le membre.
 *
 * <p>La reservation verifie dans l ordre : le proprietaire du vehicule, l activite des
 * prestations, le plafond de demandes en attente, l appartenance de l intervalle aux
 * plages d ouverture, puis l existence d un poste libre. Si deux demandes concurrentes
 * franchissent toutes ces etapes pour le meme intervalle, la contrainte d exclusion
 * PostgreSQL rejette la seconde a l ecriture : elle est traduite en regle metier
 * plutot que remontee comme erreur technique.</p>
 */
@Service
@Transactional(readOnly = true)
public class RdvService {

    private static final Logger log = LoggerFactory.getLogger(RdvService.class);

    private final RdvRepository rdvs;
    private final UtilisateurRepository membres;
    private final PrestationRepository prestations;
    private final ParametreAtelierRepository parametres;
    private final VehiculeService vehicules;
    private final DisponibiliteService disponibilites;
    private final GenerateurNumeroRdv numeros;
    private final Clock horloge;

    public RdvService(RdvRepository rdvs, UtilisateurRepository membres,
                      PrestationRepository prestations, ParametreAtelierRepository parametres,
                      VehiculeService vehicules, DisponibiliteService disponibilites,
                      GenerateurNumeroRdv numeros, Clock horloge) {
        this.rdvs = rdvs;
        this.membres = membres;
        this.prestations = prestations;
        this.parametres = parametres;
        this.vehicules = vehicules;
        this.disponibilites = disponibilites;
        this.numeros = numeros;
        this.horloge = horloge;
    }

    // --- consultation ----------------------------------------------------------------

    public List<Rdv> rdvsDuMembre(String email) {
        return rdvs.findByMembre(email);
    }

    /** Meme exception pour une reference inconnue et pour le rendez-vous d autrui. */
    public Rdv rdvDuMembre(UUID reference, String email) {
        Rdv rdv = rdvs.findByReference(reference)
                .orElseThrow(() -> new RessourceIntrouvableException("Rdv", reference));
        if (!rdv.getMembre().getEmail().equalsIgnoreCase(email)) {
            throw new RessourceIntrouvableException("Rdv", reference);
        }
        return rdv;
    }

    // --- reservation -----------------------------------------------------------------

    @Transactional
    public Rdv reserver(String email, UUID vehiculeReference, List<UUID> prestationReferences,
                        Instant debut, String commentaire) {

        ParametreAtelier p = parametres.courants();

        Utilisateur membre = membres.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur", email));
        Vehicule vehicule = vehicules.vehiculeDuMembre(vehiculeReference, email);
        List<Prestation> choisies = prestationsActives(prestationReferences);

        long enAttente = rdvs.countByMembreEmailAndStatut(email, StatutRdv.EN_ATTENTE);
        if (enAttente >= p.getMaxRdvEnAttenteParMembre()) {
            throw new LimiteDemandesEnAttenteException(enAttente);
        }

        int dureeMinutes = choisies.stream().mapToInt(Prestation::getDureeMinutes).sum();
        Instant fin = debut.plus(Rdv.dureeArrondie(dureeMinutes, p.pas()));

        if (!disponibilites.estReservable(debut, fin)) {
            throw new CreneauIndisponibleException("Ce creneau n est pas ouvert a la reservation.");
        }
        PosteAtelier poste = disponibilites.premierPosteLibre(debut, fin)
                .orElseThrow(() -> new CreneauIndisponibleException(
                        "Ce creneau vient d etre pris. Choisissez-en un autre."));

        Rdv rdv = new Rdv(numeros.prochain(), membre, vehicule, poste, debut, p.pas(), choisies, commentaire);
        if (p.isConfirmationAutomatique()) {
            rdv.confirmer();
        }

        try {
            // saveAndFlush pour que la contrainte d exclusion soit evaluee ici, dans le
            // service, et non a la fin de la transaction ou on ne pourrait plus la traduire.
            Rdv enregistre = rdvs.saveAndFlush(rdv);
            log.info("Rendez-vous {} demande par {} sur {} de {} a {}",
                    enregistre.getNumero(), email, poste.getLibelle(), debut, fin);
            return enregistre;
        } catch (DataIntegrityViolationException e) {
            log.info("Collision de reservation sur {} a {} : {}", poste.getLibelle(), debut, e.getMostSpecificCause().getMessage());
            throw new CreneauIndisponibleException(
                    "Ce creneau vient d etre pris par un autre membre. Choisissez-en un autre.");
        }
    }

    private List<Prestation> prestationsActives(List<UUID> references) {
        if (references == null || references.isEmpty()) {
            throw new AucunePrestationChoisieException();
        }
        List<Prestation> resultat = new ArrayList<>();
        for (UUID reference : references.stream().distinct().toList()) {
            Prestation prestation = prestations.findByReference(reference)
                    .orElseThrow(() -> new RessourceIntrouvableException("Prestation", reference));
            if (!prestation.isActif()) {
                throw new PrestationIndisponibleException(prestation.getLibelle());
            }
            resultat.add(prestation);
        }
        return resultat;
    }

    // --- vues pour l interface -------------------------------------------------------

    /** Prestations reservables, groupees par categorie dans l ordre du catalogue. */
    public Map<Categorie, List<Prestation>> prestationsProposees() {
        Map<Categorie, List<Prestation>> parCategorie = new LinkedHashMap<>();
        for (Prestation p : prestations.findByActifTrueOrderByLibelleAsc()) {
            parCategorie.computeIfAbsent(p.getCategorie(), c -> new ArrayList<>()).add(p);
        }
        return parCategorie;
    }

    /** Heures de depart un jour donne pour les prestations choisies. */
    public List<CreneauVue> creneauxPour(LocalDate jour, List<UUID> prestationReferences) {
        if (jour == null || prestationReferences == null || prestationReferences.isEmpty()) {
            return List.of();
        }
        int duree = prestationsActives(prestationReferences).stream().mapToInt(Prestation::getDureeMinutes).sum();
        ZoneId zone = parametres.courants().zone();
        return disponibilites.creneauxDuJour(jour, duree).stream()
                .map(c -> new CreneauVue(
                        c.debut().toString(),
                        FormatageRdv.heureLisible(c.debut(), zone) + " – " + FormatageRdv.heureLisible(c.fin(), zone),
                        c.postesLibres()))
                .toList();
    }

    public LocalDate premierJourReservable() {
        ParametreAtelier p = parametres.courants();
        return horloge.instant().plus(p.delaiMinimal()).atZone(p.zone()).toLocalDate();
    }

    public LocalDate dernierJourReservable() {
        ParametreAtelier p = parametres.courants();
        return horloge.instant().plus(p.horizon()).atZone(p.zone()).toLocalDate();
    }

    public List<RdvVue> vuesDuMembre(String email) {
        return rdvsDuMembre(email).stream().map(this::versVue).toList();
    }

    public RdvVue vueDuMembre(UUID reference, String email) {
        return versVue(rdvDuMembre(reference, email));
    }

    private RdvVue versVue(Rdv rdv) {
        ZoneId zone = parametres.courants().zone();
        return new RdvVue(
                rdv.getReference(),
                rdv.getNumero(),
                rdv.getStatut().name(),
                FormatageRdv.statutLisible(rdv.getStatut()),
                FormatageRdv.jourLisible(rdv.getDebut(), zone),
                FormatageRdv.heureLisible(rdv.getDebut(), zone),
                FormatageRdv.heureLisible(rdv.getFin(), zone),
                rdv.getVehicule().getMarque() + " " + rdv.getVehicule().getModele() + " (" + rdv.getVehicule().getPlaque() + ")",
                rdv.getLignes().stream().map(l -> l.getPrestation().getLibelle()).toList(),
                FormatageRdv.euros(rdv.montantTvac()),
                rdv.getCommentaire(),
                rdv.getMotifRefus(),
                peutEtreAnnule(rdv));
    }

    // --- annulation par le membre ----------------------------------------------------

    @Transactional
    public Rdv annuler(UUID reference, String email) {
        Rdv rdv = rdvDuMembre(reference, email);
        Duration delai = parametres.courants().delaiAnnulation();
        try {
            rdv.annulerParLeMembre(horloge.instant(), delai);
        } catch (IllegalStateException e) {
            throw new RegleMetierException("RM-11", e.getMessage());
        }
        log.info("Rendez-vous {} annule par le membre", rdv.getNumero());
        return rdv;
    }

    public boolean peutEtreAnnule(Rdv rdv) {
        return rdv.peutEtreAnnuleParLeMembre(horloge.instant(), parametres.courants().delaiAnnulation());
    }
}