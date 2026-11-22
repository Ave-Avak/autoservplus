package be.autoservplus.vente.web.dto;

import be.autoservplus.reservation.service.support.FormatageRdv;
import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.domain.Paiement;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Un paiement encaissé sur une commande déjà annulée, tel que le back-office le liste.
 * Les dates sont rendues par le gabarit, à l'heure de l'atelier.
 *
 * @param reference         référence interne du paiement, celle du bouton de remboursement
 * @param referenceCommande pour le lien vers le détail de la commande
 * @param moyen             moyen rapporté par le prestataire, ou {@code null}
 * @param rembourseLe       {@code null} tant que le paiement est à rembourser
 * @param remboursePar      administrateur qui a remboursé, ou {@code null}
 */
public record PaiementApresAnnulationVue(UUID reference, UUID referenceCommande,
                                         String numeroCommande, String client, String email,
                                         String montant, String moyen, LocalDateTime recuLe,
                                         LocalDateTime rembourseLe, String remboursePar) {

    public static PaiementApresAnnulationVue de(Paiement paiement, ZoneId zone) {
        Commande commande = paiement.getCommande();
        return new PaiementApresAnnulationVue(
                paiement.getReference(),
                commande.getReference(),
                commande.getNumero(),
                commande.getMembre().nomComplet(),
                commande.getMembre().getEmail(),
                FormatageRdv.eurosEcran(paiement.getMontant()),
                paiement.getMethode(),
                locale(paiement.getRecuApresAnnulationLe(), zone),
                locale(paiement.getRembourseLe(), zone),
                paiement.getRemboursePar());
    }

    private static LocalDateTime locale(Instant instant, ZoneId zone) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, zone);
    }

    public boolean aRembourser() {
        return rembourseLe == null;
    }
}
