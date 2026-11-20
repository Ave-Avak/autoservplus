package be.autoservplus.vente.web.dto;

import be.autoservplus.reservation.service.support.FormatageRdv;
import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.domain.LignePanier;
import be.autoservplus.vente.domain.MotifAnnulationCommande;
import be.autoservplus.vente.domain.Paiement;
import be.autoservplus.vente.domain.StatutCommande;
import be.autoservplus.vente.domain.StatutPaiement;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Détail d'une commande pour le back-office : toutes ses lignes, pièces et prestations,
 * le membre, le paiement abouti et l'annulation éventuelle.
 *
 * <p>Contrairement au détail du membre, les lignes de prestation y figurent : le garage
 * doit voir tout ce qui a été vendu. Les prix et totaux sont ceux figés à la commande
 * (RM-30), jamais ceux du catalogue courant.</p>
 */
public record CommandeAdminDetail(
        UUID reference,
        String numero,
        LocalDateTime date,
        StatutCommande statut,
        UUID referenceMembre,
        String client,
        String email,
        List<CommandeDetailVue.LigneVue> lignes,
        boolean contientDesPieces,
        String totalHtva,
        String totalTva,
        String totalTvac,
        String methodePaiement,
        LocalDateTime datePaiement,
        StatutPaiement statutPaiement,
        MotifAnnulationCommande motifAnnulation,
        LocalDateTime dateAnnulation,
        boolean renonciationVi53) {

    public static CommandeAdminDetail de(Commande commande, List<LignePanier> pieces,
                                         List<LignePanier> prestations, Paiement paiement,
                                         ZoneId zone) {
        List<CommandeDetailVue.LigneVue> lignes = new ArrayList<>();
        pieces.forEach(ligne -> lignes.add(CommandeDetailVue.LigneVue.de(ligne)));
        prestations.forEach(ligne -> lignes.add(CommandeDetailVue.LigneVue.de(ligne)));
        return new CommandeAdminDetail(
                commande.getReference(),
                commande.getNumero(),
                local(commande.getDateCommande(), zone),
                commande.getStatut(),
                commande.getMembre().getReference(),
                commande.getMembre().nomComplet(),
                commande.getMembre().getEmail(),
                List.copyOf(lignes),
                !pieces.isEmpty(),
                FormatageRdv.euros(commande.getMontantHtva()),
                FormatageRdv.euros(commande.getMontantTva()),
                FormatageRdv.euros(commande.getMontantTvac()),
                paiement == null ? null : paiement.getMethode(),
                paiement == null ? null : local(paiement.getDateFinalisation(), zone),
                paiement == null ? null : paiement.getStatut(),
                commande.getMotifAnnulation(),
                local(commande.getDateAnnulation(), zone),
                commande.isRenonciationVi53());
    }

    private static LocalDateTime local(Instant instant, ZoneId zone) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, zone);
    }
}
