package be.autoservplus.catalogue.service.dto;

import be.autoservplus.catalogue.domain.Traductions;

import java.math.BigDecimal;

/**
 * Donnees saisies par l administrateur pour creer (A1) ou modifier (A2) une prestation.
 *
 * <p>Le meme objet sert aux deux operations : les champs sont identiques, seule
 * l interpretation du {@code code} differe, exige a la creation, ignore a la
 * modification (le code est l identite technique de la prestation, immuable).</p>
 *
 * @param traductions libellé et description en néerlandais et en anglais, ou {@code null}
 *                    pour laisser les traductions existantes en l'état
 */
public record DonneesPrestation(
        String codeCategorie,
        String code,
        String libelle,
        String description,
        BigDecimal prixHtva,
        BigDecimal tauxTva,
        int dureeMinutes,
        boolean actif,
        Traductions traductions) {

    /**
     * Sans traductions : celles de la prestation restent ce qu'elles sont. C'est le cas
     * d'un appelant qui ne les connaît pas, l'import par exemple, et qui les effacerait
     * s'il devait en fournir.
     */
    public DonneesPrestation(String codeCategorie, String code, String libelle, String description,
                             BigDecimal prixHtva, BigDecimal tauxTva, int dureeMinutes, boolean actif) {
        this(codeCategorie, code, libelle, description, prixHtva, tauxTva, dureeMinutes, actif, null);
    }
}
