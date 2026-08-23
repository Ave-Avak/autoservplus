package be.autoservplus.avis.web.dto;

import be.autoservplus.avis.domain.Avis;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Saisie d un avis par le membre (BL-4).
 *
 * <p>Les bornes de la note reprennent celles du CHECK {@code ck_avis_note} : la
 * validation serveur, l entite et la base disent la meme chose, et la constante est
 * partagee plutot que recopiee — un elargissement futur du CHECK ne laisserait pas
 * trois bornes divergentes.</p>
 *
 * <p>Le commentaire est facultatif : noter sans commenter est un avis valable, et
 * l imposer pousserait au remplissage de complaisance.</p>
 */
public class FormulaireAvis {

    /** Garde-fou de saisie ; la colonne est un {@code text} sans limite en base. */
    public static final int LONGUEUR_MAXIMALE_COMMENTAIRE = 2000;

    @NotNull
    @Min(Avis.NOTE_MINIMALE)
    @Max(Avis.NOTE_MAXIMALE)
    private Short note;

    @Size(max = LONGUEUR_MAXIMALE_COMMENTAIRE)
    private String commentaire;

    public Short getNote() { return note; }
    public void setNote(Short note) { this.note = note; }

    public String getCommentaire() { return commentaire; }
    public void setCommentaire(String commentaire) { this.commentaire = commentaire; }
}
