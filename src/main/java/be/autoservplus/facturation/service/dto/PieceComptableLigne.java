package be.autoservplus.facturation.service.dto;

import be.autoservplus.facturation.domain.Avoir;
import be.autoservplus.facturation.domain.Facture;
import be.autoservplus.reservation.service.support.FormatageRdv;
import be.autoservplus.vente.domain.Commande;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Une facture ou un avoir dans les listes du back-office.
 *
 * <p>Une seule forme pour les deux pièces : le garage y cherche les mêmes choses, le
 * numéro, la date, le client, la commande et le montant. {@code numeroFacture} n'est
 * renseigné que pour un avoir, qui renvoie à la facture qu'il corrige, et {@code motif}
 * aussi, sous sa forme stable, avec {@code precision} pour une annulation d'atelier. Une pièce
 * d'atelier renvoie à son intervention, une pièce de vente à sa commande.</p>
 */
public record PieceComptableLigne(UUID reference, String numero, LocalDateTime dateEmission,
                                  String client, String email, UUID referenceCommande,
                                  String numeroCommande, String totalTvac,
                                  String numeroFacture, String motif,
                                  UUID referenceIntervention, String numeroIntervention,
                                  String precision) {

    public static PieceComptableLigne deFacture(Facture facture, ZoneId zone) {
        Commande commande = facture.getCommande();
        return new PieceComptableLigne(
                facture.getReference(),
                facture.getNumero(),
                LocalDateTime.ofInstant(facture.getDateEmission(), zone),
                facture.getMembre().nomComplet(),
                facture.getMembre().getEmail(),
                commande == null ? null : commande.getReference(),
                commande == null ? null : commande.getNumero(),
                FormatageRdv.euros(facture.getMontantTvac()),
                null,
                null,
                facture.getIntervention() == null ? null : facture.getIntervention().getReference(),
                facture.getIntervention() == null ? null : facture.getIntervention().getNumero(),
                null);
    }

    public static PieceComptableLigne deAvoir(Avoir avoir, ZoneId zone) {
        Facture facture = avoir.getFacture();
        Commande commande = facture.getCommande();
        return new PieceComptableLigne(
                avoir.getReference(),
                avoir.getNumero(),
                LocalDateTime.ofInstant(avoir.getDateEmission(), zone),
                facture.getMembre().nomComplet(),
                facture.getMembre().getEmail(),
                commande == null ? null : commande.getReference(),
                commande == null ? null : commande.getNumero(),
                FormatageRdv.euros(avoir.getMontantTvac()),
                facture.getNumero(),
                avoir.getMotif(),
                facture.getIntervention() == null ? null : facture.getIntervention().getReference(),
                facture.getIntervention() == null ? null : facture.getIntervention().getNumero(),
                avoir.getPrecision());
    }
}
