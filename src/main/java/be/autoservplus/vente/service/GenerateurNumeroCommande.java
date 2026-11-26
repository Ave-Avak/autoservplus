package be.autoservplus.vente.service;

import be.autoservplus.common.numerotation.CompteurAnnuel;
import be.autoservplus.common.numerotation.TypeCompteur;
import org.springframework.stereotype.Component;

/**
 * Attribue le numéro fonctionnel d'une commande, format {@code CMD-AAAA-NNNN}, la suite
 * repartant à 1 chaque année au garage.
 *
 * <p>Séquence globale écartée (V90) : préfixée de l'année mais jamais remise à 1, elle
 * donnait {@code CMD-2027-0412} au premier document d'une année. Le numéro est tiré dans
 * la transaction de l'appelant, qui doit en ouvrir une.</p>
 */
@Component
public class GenerateurNumeroCommande {

    private final CompteurAnnuel compteur;

    public GenerateurNumeroCommande(CompteurAnnuel compteur) {
        this.compteur = compteur;
    }

    public String prochain() {
        return compteur.prochainNumero(TypeCompteur.COMMANDE);
    }
}
