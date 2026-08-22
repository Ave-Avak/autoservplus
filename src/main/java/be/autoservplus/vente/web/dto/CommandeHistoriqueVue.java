package be.autoservplus.vente.web.dto;

import be.autoservplus.reservation.service.support.FormatageRdv;
import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.domain.StatutCommande;

import java.time.ZoneId;
import java.util.UUID;

/**
 * Une ligne de l historique des commandes du membre (F32 restreint : la liste, pas
 * encore le detail). Montant et date pre-formates, convention du module.
 *
 * <p>Les champs de facture sont nuls a la sortie du module vente, qui ne connait pas
 * la facturation : c est le controleur qui les complete depuis le module
 * {@code facturation}, via {@link #avecFacture}. Faire dependre la vente de la
 * facturation pour un simple lien de telechargement inverserait la dependance —
 * c est la facture qui nait de la commande, pas l inverse.</p>
 *
 * <p>{@code statut} est expose brut : le libelle affiche est une cle i18n construite
 * dans le gabarit, aucune chaine visible n est fabriquee ici.</p>
 */
public record CommandeHistoriqueVue(
        UUID reference,
        String numero,
        String date,
        String totalTvac,
        StatutCommande statut,
        UUID referenceFacture,
        String numeroFacture) {

    public static CommandeHistoriqueVue de(Commande commande, ZoneId zone) {
        return new CommandeHistoriqueVue(
                commande.getReference(),
                commande.getNumero(),
                FormatageRdv.jourLisible(commande.getDateCommande(), zone),
                FormatageRdv.euros(commande.getMontantTvac()),
                commande.getStatut(),
                null,
                null);
    }

    /** La meme ligne, augmentee du lien vers sa facture. */
    public CommandeHistoriqueVue avecFacture(UUID referenceFacture, String numeroFacture) {
        return new CommandeHistoriqueVue(reference, numero, date, totalTvac, statut,
                referenceFacture, numeroFacture);
    }

    /** Le bouton de telechargement n apparait que si la facture existe. */
    public boolean estFacturee() {
        return referenceFacture != null;
    }
}
