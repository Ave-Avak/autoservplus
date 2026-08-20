package be.autoservplus.reservation.domain;

import be.autoservplus.common.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.SQLRestriction;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Plage horaire d ouverture du garage pour un jour de la semaine.
 *
 * <p>Une journee peut comporter plusieurs plages : matin et apres-midi, separes par la
 * pause de midi. Les creneaux reservables sont generes a partir de ces plages.</p>
 */
@Entity
@Table(name = "plage_ouverture")
@SQLRestriction("deleted_at IS NULL")
public class PlageOuverture extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 1 = lundi ... 7 = dimanche, norme ISO 8601. */
    @Column(name = "jour_semaine", nullable = false)
    private short jourSemaine;

    @NotNull
    @Column(name = "heure_debut", nullable = false)
    private LocalTime heureDebut;

    @NotNull
    @Column(name = "heure_fin", nullable = false)
    private LocalTime heureFin;

    @Column(name = "actif", nullable = false)
    private boolean actif = true;

    protected PlageOuverture() {
        // requis par JPA
    }

    public PlageOuverture(DayOfWeek jour, LocalTime heureDebut, LocalTime heureFin) {
        Objects.requireNonNull(jour, "jour");
        this.heureDebut = Objects.requireNonNull(heureDebut, "heureDebut");
        this.heureFin = Objects.requireNonNull(heureFin, "heureFin");
        if (!heureFin.isAfter(heureDebut)) {
            throw new IllegalArgumentException("L heure de fin doit suivre l heure de debut.");
        }
        this.jourSemaine = (short) jour.getValue();
    }

    public DayOfWeek jour() {
        return DayOfWeek.of(jourSemaine);
    }

    /** Nombre de creneaux de la duree indiquee que la plage peut contenir. */
    public int nombreDeCreneaux(int dureeMinutes) {
        long minutes = java.time.Duration.between(heureDebut, heureFin).toMinutes();
        return (int) (minutes / dureeMinutes);
    }

    public Long getId() { return id; }
    public short getJourSemaine() { return jourSemaine; }
    public LocalTime getHeureDebut() { return heureDebut; }
    public LocalTime getHeureFin() { return heureFin; }
    public boolean isActif() { return actif; }
    public void desactiver() { this.actif = false; }
    public void activer() { this.actif = true; }

    @Override
    public boolean equals(Object autre) {
        if (this == autre) return true;
        if (!(autre instanceof PlageOuverture plage)) return false;
        return id != null && id.equals(plage.id);
    }

    @Override
    public int hashCode() {
        return PlageOuverture.class.hashCode();
    }
}