package be.autoservplus.reservation.domain;

import be.autoservplus.common.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Creneau reservable, genere a partir des plages d ouverture.
 *
 * <p>Le champ {@code version} porte le verrouillage optimiste de JPA. Si deux membres
 * tentent de reserver le meme creneau au meme instant, le second recoit une
 * {@link jakarta.persistence.OptimisticLockException} plutot que d ecraser le premier.
 * La base garantit par ailleurs l unicite du rendez-vous par creneau : la regle RM-08
 * tient meme en cas de defaillance applicative.</p>
 */
@Entity
@Table(name = "creneau_horaire")
@SQLRestriction("deleted_at IS NULL")
public class CreneauHoraire extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference", nullable = false, updatable = false)
    private UUID reference;

    @NotNull
    @Column(name = "debut", nullable = false)
    private Instant debut;

    @NotNull
    @Column(name = "fin", nullable = false)
    private Instant fin;

    @Column(name = "disponible", nullable = false)
    private boolean disponible = true;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected CreneauHoraire() {
        // requis par JPA
    }

    public CreneauHoraire(Instant debut, Instant fin) {
        this.reference = UUID.randomUUID();
        this.debut = Objects.requireNonNull(debut, "debut");
        this.fin = Objects.requireNonNull(fin, "fin");
        if (!fin.isAfter(debut)) {
            throw new IllegalArgumentException("La fin du creneau doit suivre son debut.");
        }
    }

    /** Marque le creneau comme occupe. */
    public void reserver() {
        if (!disponible) {
            throw new IllegalStateException("Ce creneau est deja reserve.");
        }
        this.disponible = false;
    }

    /** Rend le creneau a nouveau reservable, apres annulation d un rendez-vous. */
    public void liberer() {
        this.disponible = true;
    }

    public boolean estPasse(Instant maintenant) {
        return debut.isBefore(maintenant);
    }

    public boolean estReservable(Instant maintenant) {
        return disponible && !estPasse(maintenant);
    }

    public Long getId() { return id; }
    public UUID getReference() { return reference; }
    public Instant getDebut() { return debut; }
    public Instant getFin() { return fin; }
    public boolean isDisponible() { return disponible; }
    public long getVersion() { return version; }

    @Override
    public boolean equals(Object autre) {
        if (this == autre) return true;
        if (!(autre instanceof CreneauHoraire creneau)) return false;
        return id != null && id.equals(creneau.id);
    }

    @Override
    public int hashCode() {
        return CreneauHoraire.class.hashCode();
    }
}

