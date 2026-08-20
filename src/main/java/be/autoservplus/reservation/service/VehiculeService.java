package be.autoservplus.reservation.service;

import be.autoservplus.common.exception.RegleMetierException;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.reservation.domain.Motorisation;
import be.autoservplus.reservation.domain.Vehicule;
import be.autoservplus.reservation.repository.VehiculeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Gestion du parc de vehicules d un membre.
 *
 * <p>Couvre les fonctionnalites F9 a F11. Toutes les operations portant sur un vehicule
 * existant verifient au prealable que le demandeur en est le proprietaire : sans ce
 * controle, connaitre une reference suffirait a consulter ou modifier le vehicule
 * d autrui.</p>
 */
@Service
@Transactional(readOnly = true)
public class VehiculeService {

    /** Limite volontairement large : elle vise l abus, pas l usage normal. */
    private static final int VEHICULES_MAXIMUM = 20;

    private final VehiculeRepository vehicules;
    private final UtilisateurRepository membres;

    public VehiculeService(VehiculeRepository vehicules, UtilisateurRepository membres) {
        this.vehicules = vehicules;
        this.membres = membres;
    }

    public List<Vehicule> vehiculesDuMembre(String email) {
        return vehicules.findByMembre(email);
    }

    /**
     * Retrouve un vehicule apres verification du proprietaire.
     *
     * @throws RessourceIntrouvableException si la reference est inconnue ou si le
     *                                       demandeur n est pas le proprietaire
     */
    public Vehicule vehiculeDuMembre(UUID reference, String email) {
        Vehicule vehicule = vehicules.findByReference(reference)
                .orElseThrow(() -> new RessourceIntrouvableException("Vehicule", reference));

        // Meme exception que pour une reference inconnue : repondre « acces refuse »
        // confirmerait l existence du vehicule et permettrait de sonder les references.
        if (!vehicule.getMembre().getEmail().equalsIgnoreCase(email)) {
            throw new RessourceIntrouvableException("Vehicule", reference);
        }
        return vehicule;
    }

    /**
     * Enregistre un vehicule au nom du membre.
     *
     * @throws RegleMetierException si la plaque est deja enregistree ou si le membre a
     *                              atteint le nombre maximal de vehicules
     */
    @Transactional
    public Vehicule ajouter(String email, String plaque, String marque, String modele,
                            Motorisation motorisation, Short annee, Integer kilometrage) {

        String plaqueNormalisee = Vehicule.normaliserPlaque(plaque);

        if (vehicules.existsByPlaque(plaqueNormalisee)) {
            throw new RegleMetierException("RM-12",
                    "Le vehicule immatricule %s est deja enregistre.".formatted(plaqueNormalisee));
        }
        if (vehicules.countByMembreEmailAndActifTrue(email) >= VEHICULES_MAXIMUM) {
            throw new RegleMetierException("RM-13",
                    "Vous avez atteint la limite de %d vehicules.".formatted(VEHICULES_MAXIMUM));
        }

        Utilisateur membre = membres.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur", email));

        Vehicule vehicule = new Vehicule(membre, plaqueNormalisee, marque, modele, motorisation);
        vehicule.setAnnee(annee);
        if (kilometrage != null) {
            vehicule.mettreAJourKilometrage(kilometrage);
        }
        return vehicules.save(vehicule);
    }

    /**
     * Modifie les caracteristiques d un vehicule.
     *
     * <p>La plaque n est pas modifiable : elle identifie le vehicule et sert de cle a
     * son historique d entretien. Un changement de plaque correspond a un autre
     * vehicule, donc a un nouvel enregistrement.</p>
     */
    @Transactional
    public Vehicule modifier(UUID reference, String email, String marque, String modele,
                             Motorisation motorisation, Short annee, String numeroChassis) {
        Vehicule vehicule = vehiculeDuMembre(reference, email);
        vehicule.modifier(marque, modele, motorisation, annee, numeroChassis);
        return vehicule;
    }

    @Transactional
    public Vehicule releverKilometrage(UUID reference, String email, int kilometrage) {
        Vehicule vehicule = vehiculeDuMembre(reference, email);
        vehicule.mettreAJourKilometrage(kilometrage);
        return vehicule;
    }

    /**
     * Retire un vehicule du parc par suppression logique.
     *
     * <p>L historique des interventions le referencant reste intact : effacer le
     * vehicule reviendrait a effacer des factures deja emises.</p>
     */
    @Transactional
    public void supprimer(UUID reference, String email) {
        vehiculeDuMembre(reference, email).marquerSupprime(email);
    }
}