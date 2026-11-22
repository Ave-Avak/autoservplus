package be.autoservplus.catalogue.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.Size;

import java.util.Objects;

/**
 * Traductions du libellé et de la description d'un article du catalogue, en néerlandais
 * et en anglais. Le français vit dans les colonnes d'origine de l'article.
 *
 * <p>Chaque traduction est facultative. Une saisie vide est rangée {@code null} et non
 * en chaîne vide : la base ne connaît ainsi qu'une façon de dire « non traduit », et la
 * recherche comme le repli n'ont qu'un cas à traiter.</p>
 */
@Embeddable
public class Traductions {

    @Size(max = 150)
    @Column(name = "libelle_nl", length = 150)
    private String libelleNl;

    @Size(max = 150)
    @Column(name = "libelle_en", length = 150)
    private String libelleEn;

    @Column(name = "description_nl", columnDefinition = "text")
    private String descriptionNl;

    @Column(name = "description_en", columnDefinition = "text")
    private String descriptionEn;

    protected Traductions() {
        // requis par JPA
    }

    public Traductions(String libelleNl, String libelleEn, String descriptionNl, String descriptionEn) {
        this.libelleNl = nettoyer(libelleNl);
        this.libelleEn = nettoyer(libelleEn);
        this.descriptionNl = nettoyer(descriptionNl);
        this.descriptionEn = nettoyer(descriptionEn);
    }

    /** Aucune traduction : l'article ne s'affiche qu'en français. */
    public static Traductions aucune() {
        return new Traductions(null, null, null, null);
    }

    /** Vrai quand le libellé existe dans les deux autres langues du site. */
    public boolean libelleComplet() {
        return libelleNl != null && libelleEn != null;
    }

    public String getLibelleNl() { return libelleNl; }
    public String getLibelleEn() { return libelleEn; }
    public String getDescriptionNl() { return descriptionNl; }
    public String getDescriptionEn() { return descriptionEn; }

    private static String nettoyer(String saisie) {
        return TexteLocalise.present(saisie) ? saisie.trim() : null;
    }

    @Override
    public boolean equals(Object autre) {
        if (this == autre) return true;
        if (!(autre instanceof Traductions traductions)) return false;
        return Objects.equals(libelleNl, traductions.libelleNl)
                && Objects.equals(libelleEn, traductions.libelleEn)
                && Objects.equals(descriptionNl, traductions.descriptionNl)
                && Objects.equals(descriptionEn, traductions.descriptionEn);
    }

    @Override
    public int hashCode() {
        return Objects.hash(libelleNl, libelleEn, descriptionNl, descriptionEn);
    }
}
