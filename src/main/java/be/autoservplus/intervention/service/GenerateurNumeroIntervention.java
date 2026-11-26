package be.autoservplus.intervention.service;

import be.autoservplus.common.numerotation.CompteurAnnuel;
import be.autoservplus.common.numerotation.TypeCompteur;
import org.springframework.stereotype.Component;

/**
 * Attribue le numéro fonctionnel d'une intervention, format {@code INT-AAAA-NNNN}, la
 * suite repartant à 1 chaque année au garage.
 *
 * <p>Séquence globale écartée (V90) : préfixée de l'année mais jamais remise à 1, elle
 * donnait {@code INT-2027-0388} au premier document d'une année. Le numéro est tiré dans
 * la transaction de l'appelant, qui doit en ouvrir une.</p>
 */
@Component
public class GenerateurNumeroIntervention {

    private final CompteurAnnuel compteur;

    public GenerateurNumeroIntervention(CompteurAnnuel compteur) {
        this.compteur = compteur;
    }

    public String prochain() {
        return compteur.prochainNumero(TypeCompteur.INTERVENTION);
    }
}
