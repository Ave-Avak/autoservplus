package be.autoservplus.catalogue.service.dto;

import be.autoservplus.catalogue.domain.Traductions;
import be.autoservplus.catalogue.domain.UnitePrestation;

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
 * @param unite       unité choisie à la création, ou {@code null} : forfait pour une
 *                    création, unité en place pour une modification. Une unité différente
 *                    de celle en place est refusée (V72)
 * @param poseSeule   prix quand le client apporte ses pièces, ou {@code null} pour laisser
 *                    celui en place (V76)
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
        Traductions traductions,
        UnitePrestation unite,
        PrixPoseSeule poseSeule) {

    /** Sans prix pose seule : celui en place reste, aucun à la création. */
    public DonneesPrestation(String codeCategorie, String code, String libelle, String description,
                             BigDecimal prixHtva, BigDecimal tauxTva, int dureeMinutes, boolean actif,
                             Traductions traductions, UnitePrestation unite) {
        this(codeCategorie, code, libelle, description, prixHtva, tauxTva, dureeMinutes, actif,
                traductions, unite, null);
    }

    /** Sans unité : forfait à la création, unité en place à la modification. */
    public DonneesPrestation(String codeCategorie, String code, String libelle, String description,
                             BigDecimal prixHtva, BigDecimal tauxTva, int dureeMinutes, boolean actif,
                             Traductions traductions) {
        this(codeCategorie, code, libelle, description, prixHtva, tauxTva, dureeMinutes, actif,
                traductions, null);
    }

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
