package be.autoservplus.facturation.service.dto;

import be.autoservplus.facturation.service.VentilationTva;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Tout ce qu'il faut imprimer sur une note de crédit, et rien d'autre (F30).
 *
 * <p>Même principe que {@link DocumentFacture} : le générateur PDF ne voit aucune
 * entité, il reçoit ce document déjà constitué et devient testable sans base ni
 * Spring.</p>
 *
 * <p>Le client et les lignes réutilisent les records imbriqués de
 * {@code DocumentFacture} plutôt que d'en cloner deux copies : ce sont exactement les
 * mêmes données, celles de la facture contre-passée, et deux définitions jumelles
 * finiraient par diverger à la première évolution de l'adresse ou du libellé.</p>
 *
 * <p><b>Ce qu'un avoir a en plus d'une facture</b> : la référence du document qu'il
 * corrige. Le numéro et la date de la facture d'origine sont des mentions
 * obligatoires du document rectificatif (AR n°1, art. 12) : sans elles, la note de
 * crédit ne se rattache à rien et l'administration ne peut pas vérifier ce qu'elle
 * annule. Le motif, lui, est stocké sous forme stable et traduit à l'impression.</p>
 *
 * <p>{@code locale} est celle du <b>membre</b>, comme pour la facture : la note de
 * crédit est émise dans la langue du client, pas dans celle de la session qui la
 * télécharge, et surtout dans la même langue que la facture qu'elle corrige.</p>
 *
 * <p>Une note de crédit d'atelier renvoie à l'intervention et non à une commande, et porte
 * la précision de l'administrateur, imprimée telle que saisie (Q-H-50).</p>
 */
public record DocumentAvoir(
        String numero,
        Instant dateEmission,
        String numeroFactureOrigine,
        Instant dateFactureOrigine,
        String numeroCommande,
        String cleMotif,
        DocumentFacture.ClientFacture client,
        List<DocumentFacture.LigneFacture> lignes,
        VentilationTva ventilation,
        BigDecimal totalHtva,
        BigDecimal totalTva,
        BigDecimal totalTvac,
        Locale locale,
        String numeroIntervention,
        String precision) {

    /** Note de crédit d'une facture de commande (F30) : ni intervention ni précision. */
    public DocumentAvoir(String numero, Instant dateEmission, String numeroFactureOrigine,
                         Instant dateFactureOrigine, String numeroCommande, String cleMotif,
                         DocumentFacture.ClientFacture client, List<DocumentFacture.LigneFacture> lignes,
                         VentilationTva ventilation, BigDecimal totalHtva, BigDecimal totalTva,
                         BigDecimal totalTvac, Locale locale) {
        this(numero, dateEmission, numeroFactureOrigine, dateFactureOrigine, numeroCommande, cleMotif,
                client, lignes, ventilation, totalHtva, totalTva, totalTvac, locale, null, null);
    }

    public boolean estDAtelier() {
        return numeroIntervention != null;
    }
}
