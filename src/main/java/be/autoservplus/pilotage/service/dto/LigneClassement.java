package be.autoservplus.pilotage.service.dto;

import java.math.BigDecimal;

/**
 * Une ligne de classement des ventes (BL-1).
 *
 * <p>Groupe sur le <b>libelle fige</b> et non sur l article : un article renomme ou
 * retire du catalogue garde ses ventes passees sous le nom qu il portait au moment de
 * la vente, ce qui est aussi la regle comptable appliquee aux lignes (RM-30).</p>
 *
 * @param quantite   somme des quantites vendues, {@code Long} car COUNT/SUM JPQL
 * @param montantHtva chiffre d affaires hors TVA genere par cet article
 */
public record LigneClassement(String libelle, Long quantite, BigDecimal montantHtva) {
}
