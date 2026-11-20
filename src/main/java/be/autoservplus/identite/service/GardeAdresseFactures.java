package be.autoservplus.identite.service;

import be.autoservplus.common.exception.RegleMetierException;
import be.autoservplus.facturation.repository.FactureRepository;
import be.autoservplus.identite.domain.Utilisateur;
import org.springframework.stereotype.Component;

/**
 * Refus d'effacer l'adresse postale d'un membre qui a des factures.
 *
 * <p>Commun au profil et à la modification par l'administrateur : la règle porte sur
 * l'adresse, pas sur celui qui la saisit. Le contrôle ne vise pas les PDF déjà
 * archivés, immuables, mais les factures dont le document n'a pas encore été généré :
 * {@code PdfFactureService} lit l'entité vive au moment de le produire, si bien qu'une
 * adresse effacée aujourd'hui sortirait sur une facture émise il y a des mois, sans sa
 * mention obligatoire (AR n°1, art. 5).</p>
 */
@Component
public class GardeAdresseFactures {

    private final FactureRepository factures;

    public GardeAdresseFactures(FactureRepository factures) {
        this.factures = factures;
    }

    /**
     * @throws RegleMetierException si l'adresse complète deviendrait vide alors qu'une
     *                              facture existe
     */
    public void exigerAdresseConservee(Utilisateur membre, String rue, String codePostal,
                                       String localite) {
        boolean adresseDevientVide = vide(rue) || vide(codePostal) || vide(localite);
        if (adresseDevientVide && membre.adressePostaleComplete()
                && factures.countByMembreEmail(membre.getEmail()) > 0) {
            // Constructeur SANS code : aucune règle du CdC ne porte ce refus, et
            // fabriquer un code RM polluerait une traçabilité qui vaut parce qu'elle
            // renvoie à une exigence réelle. Le message se suffit à lui-même.
            throw new RegleMetierException(
                    "Renseignez l'adresse : elle figure sur les factures du membre.");
        }
    }

    private static boolean vide(String valeur) {
        return valeur == null || valeur.isBlank();
    }
}
