package be.autoservplus.identite.service;

import be.autoservplus.common.exception.RegleMetierException;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.vente.domain.StatutCommande;
import be.autoservplus.vente.repository.CommandeRepository;
import org.springframework.stereotype.Component;

/**
 * Refus d'effacer l'adresse postale d'un membre dont une commande attend son paiement.
 *
 * <p>Commun au profil et à la modification par l'administrateur. Depuis l'instantané
 * client (V61), une facture émise garde l'adresse qu'elle portait : effacer l'adresse
 * ne touche plus les factures existantes. Reste la commande validée mais pas encore
 * payée, dont la facture sera émise au paiement avec l'adresse du moment. Une adresse
 * effacée entre le récapitulatif, qui l'exige, et le paiement donnerait une facture sans
 * adresse.</p>
 */
@Component
public class GardeAdresseCommande {

    private final CommandeRepository commandes;

    public GardeAdresseCommande(CommandeRepository commandes) {
        this.commandes = commandes;
    }

    /**
     * @throws RegleMetierException si l'adresse complète deviendrait vide alors qu'une
     *                              commande du membre attend son paiement
     */
    public void exigerAdresseConservee(Utilisateur membre, String rue, String codePostal,
                                       String localite) {
        boolean adresseDevientIncomplete = !Utilisateur.adresseComplete(rue, codePostal, localite);
        if (adresseDevientIncomplete && membre.adressePostaleComplete()
                && commandes.existsByMembreIdAndStatut(membre.getId(), StatutCommande.EN_ATTENTE_PAIEMENT)) {
            // Constructeur SANS code : aucune règle du CdC ne porte ce refus, et
            // fabriquer un code RM polluerait une traçabilité qui vaut parce qu'elle
            // renvoie à une exigence réelle. Le message se suffit à lui-même.
            throw new RegleMetierException(
                    "Renseignez l'adresse : une commande attend son paiement et sa facture la portera.");
        }
    }
}
