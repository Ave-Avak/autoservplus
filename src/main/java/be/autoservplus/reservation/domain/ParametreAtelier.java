package be.autoservplus.reservation.domain;

import jakarta.persistence.*;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;

/**
 * Regles de reservation propres au garage.
 *
 * <p>Ligne unique en V1 mono-tenant, identifiant fixe a 1. Chaque valeur est bornee
 * par une contrainte CHECK en base : une saisie aberrante est refusee par le moteur,
 * pas seulement par le formulaire. L enumeration des bornes est repetee ici pour que
 * la validation cote serveur produise un message lisible avant d atteindre la base.</p>
 */
@Entity
@Table(name = "parametre_atelier")
@EntityListeners(AuditingEntityListener.class)
public class ParametreAtelier {

    public static final short IDENTIFIANT_UNIQUE = 1;
    public static final Set<Integer> PAS_ADMIS = Set.of(15, 30, 45, 60);

    @Id
    @Column(name = "id", nullable = false)
    private short id = IDENTIFIANT_UNIQUE;

    @Column(name = "fuseau_horaire", nullable = false, length = 60)
    private String fuseauHoraire = "Europe/Brussels";

    @Column(name = "pas_minutes", nullable = false)
    private short pasMinutes = 30;

    @Column(name = "tampon_minutes", nullable = false)
    private short tamponMinutes = 10;

    @Column(name = "delai_minimal_heures", nullable = false)
    private short delaiMinimalHeures = 24;

    @Column(name = "horizon_jours", nullable = false)
    private short horizonJours = 60;

    @Column(name = "delai_annulation_heures", nullable = false)
    private short delaiAnnulationHeures = 24;

    @Column(name = "confirmation_automatique", nullable = false)
    private boolean confirmationAutomatique;

    @Column(name = "max_rdv_en_attente_par_membre", nullable = false)
    private short maxRdvEnAttenteParMembre = 3;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by", length = 120)
    private String updatedBy;

    protected ParametreAtelier() {
        // requis par JPA ; la ligne est creee par la migration V13
    }

    public void modifier(String fuseauHoraire, int pasMinutes, int tamponMinutes,
                         int delaiMinimalHeures, int horizonJours, int delaiAnnulationHeures,
                         boolean confirmationAutomatique, int maxRdvEnAttenteParMembre) {
        ZoneId.of(fuseauHoraire); // leve une exception si le fuseau est inconnu
        exiger(PAS_ADMIS.contains(pasMinutes), "Le pas doit valoir 15, 30, 45 ou 60 minutes.");
        exiger(tamponMinutes >= 0 && tamponMinutes <= 120, "Le tampon doit etre compris entre 0 et 120 minutes.");
        exiger(delaiMinimalHeures >= 0 && delaiMinimalHeures <= 168, "Le delai minimal doit etre compris entre 0 et 168 heures.");
        exiger(horizonJours >= 1 && horizonJours <= 365, "L horizon doit etre compris entre 1 et 365 jours.");
        exiger(delaiAnnulationHeures >= 0 && delaiAnnulationHeures <= 168, "Le delai d annulation doit etre compris entre 0 et 168 heures.");
        exiger(maxRdvEnAttenteParMembre >= 1 && maxRdvEnAttenteParMembre <= 20, "Le maximum de rendez-vous en attente doit etre compris entre 1 et 20.");

        this.fuseauHoraire = fuseauHoraire;
        this.pasMinutes = (short) pasMinutes;
        this.tamponMinutes = (short) tamponMinutes;
        this.delaiMinimalHeures = (short) delaiMinimalHeures;
        this.horizonJours = (short) horizonJours;
        this.delaiAnnulationHeures = (short) delaiAnnulationHeures;
        this.confirmationAutomatique = confirmationAutomatique;
        this.maxRdvEnAttenteParMembre = (short) maxRdvEnAttenteParMembre;
    }

    private static void exiger(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    // --- vues typees, pour que les services ne manipulent pas des entiers nus ------

    public ZoneId zone() { return ZoneId.of(fuseauHoraire); }
    public Duration pas() { return Duration.ofMinutes(pasMinutes); }
    public Duration tampon() { return Duration.ofMinutes(tamponMinutes); }
    public Duration delaiMinimal() { return Duration.ofHours(delaiMinimalHeures); }
    public Duration horizon() { return Duration.ofDays(horizonJours); }
    public Duration delaiAnnulation() { return Duration.ofHours(delaiAnnulationHeures); }

    public String getFuseauHoraire() { return fuseauHoraire; }
    public short getPasMinutes() { return pasMinutes; }
    public short getTamponMinutes() { return tamponMinutes; }
    public short getDelaiMinimalHeures() { return delaiMinimalHeures; }
    public short getHorizonJours() { return horizonJours; }
    public short getDelaiAnnulationHeures() { return delaiAnnulationHeures; }
    public boolean isConfirmationAutomatique() { return confirmationAutomatique; }
    public short getMaxRdvEnAttenteParMembre() { return maxRdvEnAttenteParMembre; }
    public Instant getUpdatedAt() { return updatedAt; }
}