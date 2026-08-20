package be.autoservplus.reservation.domain;

import be.autoservplus.common.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Periode pendant laquelle on ne peut pas reserver.
 *
 * <p>Sans poste, l indisponibilite concerne tout l atelier : jour ferie, conges,
 * formation. Avec un poste, elle ne bloque que celui-ci : pont en panne, rendez-vous
 * fournisseur. Une seule notion couvre les trois cas du cahier des charges (A24).</p>
 */
@Entity
@Table(name = "indisponibilite")
@SQLRestriction("deleted_at IS NULL")
public class Indisponibilite extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference", nullable = false, updatable = false)
    private UUID reference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "poste_id")
    private PosteAtelier poste;

    @NotNull
    @Column(name = "debut", nullable = false)
    private Instant debut;

    @NotNull
    @Column(name = "fin", nullable = false)
    private Instant fin;

    @NotBlank
    @Size(max = 200)
    @Column(name = "motif", nullable = false, length = 200)
    private String motif;

    protected Indisponibilite() {
        // requis par JPA
    }

    /** @param poste null pour une fermeture de tout l atelier */
    public Indisponibilite(PosteAtelier poste, Instant debut, Instant fin, String motif) {
        this.reference = UUID.randomUUID();
        this.poste = poste;
        this.debut = Objects.requireNonNull(debut, "debut");
        this.fin = Objects.requireNonNull(fin, "fin");
        if (!fin.isAfter(debut)) {
            throw new IllegalArgumentException("La fin doit suivre le debut.");
        }
        this.motif = Objects.requireNonNull(motif, "motif").trim();
    }

    public boolean concerneToutLAtelier() {
        return poste == null;
    }

    /** Vrai si l intervalle [debut, fin) donne chevauche cette indisponibilite. */
    public boolean chevauche(Instant autreDebut, Instant autreFin) {
        return autreDebut.isBefore(fin) && autreFin.isAfter(debut);
    }

    public Long getId() { return id; }
    public UUID getReference() { return reference; }
    public PosteAtelier getPoste() { return poste; }
    public Instant getDebut() { return debut; }
    public Instant getFin() { return fin; }
    public String getMotif() { return motif; }

    @Override
    public boolean equals(Object autre) {
        if (this == autre) return true;
        if (!(autre instanceof Indisponibilite indispo)) return false;
        return id != null && id.equals(indispo.id);
    }

    @Override
    public int hashCode() {
        return Indisponibilite.class.hashCode();
    }
}