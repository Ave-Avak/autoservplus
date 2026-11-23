package be.autoservplus.pilotage.service.dto;

import java.math.BigDecimal;

/**
 * Une ligne de classement des ventes (BL-1).
 *
 * <p>Un rang par article du catalogue, nommé par son libellé courant dans la langue de
 * l'administrateur. Le libellé figé d'une ligne ne sert plus de clé : il est écrit dans
 * la langue du document de chaque vente, et un même article vendu en français et en
 * néerlandais occuperait deux rangs. Un article renommé ou retiré garde son rang et ses
 * ventes passées, sous son nom du jour.</p>
 *
 * @param libelle     libellé courant de l'article, en français quand la traduction manque
 * @param quantite    somme des quantités vendues
 * @param montantHtva chiffre d'affaires hors TVA généré par cet article
 */
public record LigneClassement(String libelle, Long quantite, BigDecimal montantHtva) {
}
