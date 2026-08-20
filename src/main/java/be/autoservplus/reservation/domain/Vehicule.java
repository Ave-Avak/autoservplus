package be.autoservplus.reservation.domain;

import be.autoservplus.common.entity.BaseEntity;
import be.autoservplus.identite.domain.Utilisateur;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.hibernate.annotations.SQLRestriction;

import java.util.Objects;
import java.util.UUID;

/**
 * Vehicule appartenant a un membre.
 *
 * <p>La plaque d immatriculation est unique parmi les vehicules non supprimes : une
 * plaque peut etre reattribuee par la DIV apres radiation, donc l unicite ne peut pas
 * porter sur l ensemble de l historique.</p>
 */
@Entity
@Table(name = "vehicule")
@SQLRestriction("deleted_at IS NULL")
public class Vehicule extends BaseEntity {

    /** Format belge courant : 1-ABC-123, ou anciens formats sur demande. */
    private static final String MOTIF_PLAQUE = "^[0-9]-[A-Z]{3}-[0-9]{3}$|^[A-Z0-9-]{1,15}$";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference", nullable = false, updatable = false)
    private UUID reference;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "membre_id", nullable = false)
    private Utilisateur membre;

    @NotBlank
    @Size(max = 15)
    @Pattern(regexp = MOTIF_PLAQUE, message = "{validation.plaque.format}")
    @Column(name = "plaque", nullable = false, length = 15)
    private String plaque;

    @NotBlank
    @Size(max = 60)
    @Column(name = "marque", nullable = false, length = 60)
    private String marque;

    @NotBlank
    @Size(max = 80)
    @Column(name = "modele", nullable = false, length = 80)
    private String modele;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "motorisation", nullable = false, length = 20)
    private Motorisation motorisation;

    @Min(1900)
    @Max(2100)
    @Column(name = "annee")
    private Short annee;

    @PositiveOrZero
    @Column(name = "kilometrage")
    private Integer kilometrage;

    @Size(max = 20)
    @Column(name = "numero_chassis", length = 20)
    private String numeroChassis;

    @Column(name = "actif", nullable = false)
    private boolean actif = true;

    protected Vehicule() {
        // requis par JPA
    }

    public Vehicule(Utilisateur membre, String plaque, String marque, String modele,
                    Motorisation motorisation) {
        this.reference = UUID.randomUUID();
        this.membre = Objects.requireNonNull(membre, "membre");
        this.plaque = normaliserPlaque(plaque);
        this.marque = Objects.requireNonNull(marque, "marque").trim();
        this.modele = Objects.requireNonNull(modele, "modele").trim();
        this.motorisation = Objects.requireNonNull(motorisation, "motorisation");
    }

    /** Met la plaque en majuscules et retire les espaces : 1-abc-123 devient 1-ABC-123. */
    public static String normaliserPlaque(String saisie) {
        Objects.requireNonNull(saisie, "plaque");
        return saisie.trim().toUpperCase().replace(" ", "");
    }

    /** Verifie que le membre indique est bien le proprietaire du vehicule. */
    public boolean appartientA(Utilisateur candidat) {
        return membre != null && membre.equals(candidat);
    }

    public String designation() {
        return "%s %s (%s)".formatted(marque, modele, plaque);
    }

    /**
     * Met a jour le kilometrage.
     *
     * <p>Un compteur ne recule pas : une valeur inferieure a la precedente signale une
     * erreur de saisie ou une manipulation, et doit etre refusee.</p>
     */
    public void mettreAJourKilometrage(int nouveauKilometrage) {
        if (nouveauKilometrage < 0) {
            throw new IllegalArgumentException("Le kilometrage ne peut pas etre negatif.");
        }
        if (kilometrage != null && nouveauKilometrage < kilometrage) {
            throw new IllegalArgumentException(
                    "Le kilometrage ne peut pas diminuer : %d releve, %d enregistre."
                            .formatted(nouveauKilometrage, kilometrage));
        }
        this.kilometrage = nouveauKilometrage;
    }

    public void modifier(String marque, String modele, Motorisation motorisation,
                         Short annee, String numeroChassis) {
        this.marque = Objects.requireNonNull(marque, "marque").trim();
        this.modele = Objects.requireNonNull(modele, "modele").trim();
        this.motorisation = Objects.requireNonNull(motorisation, "motorisation");
        this.annee = annee;
        this.numeroChassis = numeroChassis;
    }

    public Long getId() { return id; }
    public UUID getReference() { return reference; }
    public Utilisateur getMembre() { return membre; }
    public String getPlaque() { return plaque; }
    public String getMarque() { return marque; }
    public String getModele() { return modele; }
    public Motorisation getMotorisation() { return motorisation; }
    public Short getAnnee() { return annee; }
    public void setAnnee(Short annee) { this.annee = annee; }
    public Integer getKilometrage() { return kilometrage; }
    public String getNumeroChassis() { return numeroChassis; }
    public void setNumeroChassis(String numeroChassis) { this.numeroChassis = numeroChassis; }
    public boolean isActif() { return actif; }

    @Override
    public boolean equals(Object autre) {
        if (this == autre) return true;
        if (!(autre instanceof Vehicule vehicule)) return false;
        return id != null && id.equals(vehicule.id);
    }

    @Override
    public int hashCode() {
        return Vehicule.class.hashCode();
    }
}