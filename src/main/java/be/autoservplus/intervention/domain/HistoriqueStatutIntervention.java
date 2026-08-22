package be.autoservplus.intervention.domain;

import be.autoservplus.identite.domain.Utilisateur;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;

/**
 * Une transition de statut d une intervention, telle qu elle s est produite (F17).
 *
 * <p>Journal append-only : une ligne est ecrite a chaque transition, dans la meme
 * transaction que la transition elle-meme, et n est jamais modifiee ensuite. Pas de
 * suppression logique — rien ne se « supprime » dans un journal (meme precedent que
 * {@link LigneIntervention}, qui ne porte pas non plus de {@code deleted_at}).</p>
 *
 * <p>{@code statutAvant} est {@code null} pour la ligne de creation : le dossier n a
 * pas d etat anterieur a sa naissance. {@code horodatage} est l instant <b>metier</b>
 * de la transition, fourni par l horloge injectee du service (deterministe en test),
 * distinct de l audit technique {@code createdAt} pose par la base.</p>
 *
 * <p>{@code auteur} est nullable : une transition peut venir d un traitement systeme,
 * et la trace doit survivre a la disparition du compte (FK {@code ON DELETE SET NULL}).
 * Cote membre, l auteur n est jamais expose — il ne sert qu au dossier interne.</p>
 */
@Entity
@Table(name = "historique_statut_intervention")
@EntityListeners(AuditingEntityListener.class)
public class HistoriqueStatutIntervention {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "intervention_id", nullable = false, updatable = false)
    private Intervention intervention;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut_avant", length = 30, updatable = false)
    private StatutIntervention statutAvant;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "statut_apres", nullable = false, length = 30, updatable = false)
    private StatutIntervention statutApres;

    @Column(name = "horodatage", nullable = false, updatable = false)
    private Instant horodatage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auteur_id", updatable = false)
    private Utilisateur auteur;

    @Column(name = "motif", length = 500, updatable = false)
    private String motif;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @CreatedBy
    @Column(name = "created_by", length = 120, updatable = false)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by", length = 120)
    private String updatedBy;

    protected HistoriqueStatutIntervention() {
        // requis par JPA
    }

    public HistoriqueStatutIntervention(Intervention intervention,
                                        StatutIntervention statutAvant,
                                        StatutIntervention statutApres,
                                        Instant horodatage,
                                        Utilisateur auteur,
                                        String motif) {
        this.intervention = Objects.requireNonNull(intervention, "intervention");
        this.statutAvant = statutAvant;
        this.statutApres = Objects.requireNonNull(statutApres, "statutApres");
        this.horodatage = Objects.requireNonNull(horodatage, "horodatage");
        this.auteur = auteur;
        this.motif = motif;
    }

    public Long getId() { return id; }
    public Intervention getIntervention() { return intervention; }
    public StatutIntervention getStatutAvant() { return statutAvant; }
    public StatutIntervention getStatutApres() { return statutApres; }
    public Instant getHorodatage() { return horodatage; }
    public Utilisateur getAuteur() { return auteur; }
    public String getMotif() { return motif; }

    @Override
    public boolean equals(Object autre) {
        if (this == autre) return true;
        if (!(autre instanceof HistoriqueStatutIntervention historique)) return false;
        return id != null && id.equals(historique.id);
    }

    @Override
    public int hashCode() {
        return HistoriqueStatutIntervention.class.hashCode();
    }
}
