package be.autoservplus.vente.web.dto;

import be.autoservplus.reservation.service.support.FormatageRdv;
import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.domain.StatutCommande;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Une commande dans les listes du back-office. La date est rendue dans la langue de
 * l'écran par le gabarit, à l'heure de l'atelier.
 */
public record CommandeAdminLigne(UUID reference, String numero, LocalDateTime date,
                                 String client, String email, StatutCommande statut,
                                 String totalTvac) {

    public static CommandeAdminLigne de(Commande commande, ZoneId zone) {
        return new CommandeAdminLigne(
                commande.getReference(),
                commande.getNumero(),
                LocalDateTime.ofInstant(commande.getDateCommande(), zone),
                commande.getMembre().nomComplet(),
                commande.getMembre().getEmail(),
                commande.getStatut(),
                FormatageRdv.euros(commande.getMontantTvac()));
    }
}
