package be.autoservplus.catalogue.service.dto;

import java.math.BigDecimal;

/**
 * Donnees saisies par l administrateur pour creer (A1) ou modifier (A2) une prestation.
 *
 * <p>Le meme objet sert aux deux operations : les champs sont identiques, seule
 * l interpretation du {@code code} differe — exige a la creation, ignore a la
 * modification (le code est l identite technique de la prestation, immuable).</p>
 */
public record DonneesPrestation(
        String codeCategorie,
        String code,
        String libelle,
        String description,
        BigDecimal prixHtva,
        BigDecimal tauxTva,
        int dureeMinutes,
        boolean actif) {
}
