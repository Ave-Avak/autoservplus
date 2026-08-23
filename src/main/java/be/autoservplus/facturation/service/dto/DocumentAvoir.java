package be.autoservplus.facturation.service.dto;

import be.autoservplus.facturation.service.VentilationTva;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Tout ce qu il faut imprimer sur une note de credit, et rien d autre (F30).
 *
 * <p>Meme principe que {@link DocumentFacture} : le generateur PDF ne voit aucune
 * entite, il recoit ce document deja constitue et devient testable sans base ni
 * Spring.</p>
 *
 * <p>Le client et les lignes reutilisent les records imbriques de
 * {@code DocumentFacture} plutot que d en cloner deux copies : ce sont exactement les
 * memes donnees, celles de la facture contre-passee, et deux definitions jumelles
 * finiraient par diverger a la premiere evolution de l adresse ou du libelle.</p>
 *
 * <p><b>Ce qu un avoir a en plus d une facture</b> : la reference du document qu il
 * corrige. Le numero et la date de la facture d origine sont des mentions
 * obligatoires du document rectificatif (AR n°1, art. 12) — sans elles, la note de
 * credit ne se rattache a rien et l administration ne peut pas verifier ce qu elle
 * annule. Le motif, lui, est stocke sous forme stable et traduit a l impression.</p>
 *
 * <p>{@code locale} est celle du <b>membre</b>, comme pour la facture : la note de
 * credit est emise dans la langue du client, pas dans celle de la session qui la
 * telecharge — et surtout dans la meme langue que la facture qu elle corrige.</p>
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
        Locale locale) {
}
