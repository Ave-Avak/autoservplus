package be.autoservplus.pilotage.service.dto;

import java.math.BigDecimal;

/**
 * Chiffre d'affaires facturé d'une catégorie du catalogue sur la période, hors TVA (A25).
 *
 * <p>Somme des lignes des commandes facturées, rangées par la catégorie de leur article.
 * Le total des catégories peut s'écarter de quelques centimes du chiffre d'affaires
 * facturé : la facture arrondit son total, pas chaque ligne.</p>
 */
public record MontantCategorie(String categorie, BigDecimal htva) {
}
