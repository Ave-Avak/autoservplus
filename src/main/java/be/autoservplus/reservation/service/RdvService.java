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
            throw new RegleMetierException("RM-07",
                    "Vous avez deja %d demandes en attente de confirmation.".formatted(enAttente));
        }

        int dureeMinutes = choisies.stream().mapToInt(Prestation::getDureeMinutes).sum();
        Instant fin = debut.plus(Rdv.dureeArrondie(dureeMinutes, p.pas()));

        if (!disponibilites.estReservable(debut, fin)) {
            throw new RegleMetierException("RM-08",
                    "Ce creneau n est pas ouvert a la reservation.");
        }
        PosteAtelier poste = disponibilites.premierPosteLibre(debut, fin)
                .orElseThrow(() -> new RegleMetierException("RM-08",
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
            throw new RegleMetierException("RM-08",
                    "Ce creneau vient d etre pris par un autre membre. Choisissez-en un autre.");
        }
    }

    private List<Prestation> prestationsActives(List<UUID> references) {
        if (references == null || references.isEmpty()) {
            throw new RegleMetierException("RM-07", "Choisissez au moins une prestation.");
        }
        List<Prestation> resultat = new ArrayList<>();
        for (UUID reference : references.stream().distinct().toList()) {
            Prestation prestation = prestations.findByReference(reference)
                    .orElseThrow(() -> new RessourceIntrouvableException("Prestation", reference));
            if (!prestation.isActif()) {
                throw new RegleMetierException("RM-28",
                        "La prestation %s n est plus proposee.".formatted(prestation.getLibelle()));
            }
            resultat.add(prestation);
        }
        return resultat;
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