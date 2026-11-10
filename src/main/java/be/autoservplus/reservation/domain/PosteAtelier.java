package be.autoservplus.reservation.domain;

import be.autoservplus.common.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.SQLRestriction;

import java.util.Objects;
import java.util.UUID;

/**
 * Ressource planifiable de l atelier : pont, baie de diagnostic, poste de montage.
 *
 * <p>La capacite de l atelier est le nombre de postes actifs. Un rendez-vous occupe un
 * poste sur un intervalle. Le modele accueille sans modification structurelle le
 * rattachement ulterieur d un mecanicien ou de competences.</p>
 */
@Entity
@Table(name = "poste_atelier")
@SQLRestriction("deleted_at IS NULL")
public class PosteAtelier extends BaseEntity {

    /** Longueur de {@code poste_atelier.libelle}. */
    public static final int LONGUEUR_LIBELLE = 80;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference", nullable = false, updatable = false)
    private UUID reference;

    @NotBlank
    @Size(max = 80)
    @Column(name = "libelle", nullable = false, length = 80)
    private String libelle;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "ordre", nullable = false)
    private short ordre;

    @Column(name = "actif", nullable = false)
    private boolean actif = true;

    protected PosteAtelier() {
        // requis par JPA
    }

    public PosteAtelier(String libelle) {
        this.reference = UUID.randomUUID();
        this.libelle = libelleValide(libelle);
    }

    public void renommer(String libelle) { this.libelle = libelleValide(libelle); }

    /**
     * Modification depuis le back-office (A24). L'unicité du libellé parmi les postes
     * non supprimés dépend des autres lignes : {@code uq_poste_libelle_actif} en juge.
     */
    public void modifier(String libelle, String description, int ordre) {
        if (ordre < 0 || ordre > Short.MAX_VALUE) {
            throw new IllegalArgumentException("L'ordre d'affichage doit être positif.");
        }
        this.libelle = libelleValide(libelle);
        this.description = description == null || description.isBlank() ? null : description.trim();
        this.ordre = (short) ordre;
    }

    private static String libelleValide(String libelle) {
        Objects.requireNonNull(libelle, "libelle");
        String nettoye = libelle.trim();
        if (nettoye.isEmpty() || nettoye.length() > LONGUEUR_LIBELLE) {
            throw new IllegalArgumentException(
                    "Le libellé d'un poste compte de 1 à %d caractères.".formatted(LONGUEUR_LIBELLE));
        }
        return nettoye;
    }
    public void activer() { this.actif = true; }
    public void desactiver() { this.actif = false; }

    public Long getId() { return id; }
    public UUID getReference() { return reference; }
    public String getLibelle() { return libelle; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public short getOrdre() { return ordre; }
    public void setOrdre(short ordre) { this.ordre = ordre; }
    public boolean isActif() { return actif; }

    @Override
    public boolean equals(Object autre) {
        if (this == autre) return true;
        if (!(autre instanceof PosteAtelier poste)) return false;
        return id != null && id.equals(poste.id);
    }

    @Override
    public int hashCode() {
        return PosteAtelier.class.hashCode();
    }
}