package be.autoservplus.journal.service.dto;

/**
 * Une ligne du journal d audit (BL-7).
 *
 * <p>Forme commune aux deux sources historisees du projet — modifications de catalogue
 * (V25) et transitions d intervention (V23) — qui ne partagent aucune colonne au-dela
 * de l horodatage et de l auteur. Les rapprocher dans un DTO unique est ce qui permet
 * un ecran unique, trie chronologiquement.</p>
 *
 * <p>{@code acteur} est un nom lisible, jamais une entite ni une adresse : le journal
 * dit qui a agi, il n est pas un annuaire. Il vaut {@code null} quand l auteur n est
 * pas identifiable — compte anonymise depuis (F23) ou ecriture systeme.</p>
 *
 * @param type   source de la ligne, sert de cle i18n et de valeur de filtre
 * @param cible  objet touche, sous une forme reconnaissable par le garage
 * @param detail ce qui a change, deja compose
 */
public record EntreeJournal(
        String date,
        String type,
        String acteur,
        String cible,
        String detail) {

    /** Type des modifications de catalogue (table {@code historique_modification_catalogue}). */
    public static final String TYPE_CATALOGUE = "CATALOGUE";

    /** Type des transitions d intervention (table {@code historique_statut_intervention}). */
    public static final String TYPE_INTERVENTION = "INTERVENTION";

    public boolean acteurConnu() {
        return acteur != null && !acteur.isBlank();
    }
}
