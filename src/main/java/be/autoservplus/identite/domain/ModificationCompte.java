package be.autoservplus.identite.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;

/**
 * Une modification d'un champ du compte, par le titulaire ou par un administrateur.
 *
 * <p>Journal append-only sur le patron de {@code HistoriqueStatutUtilisateur} : une
 * ligne par champ modifié, écrite dans la transaction de la modification. Les valeurs
 * sont des données personnelles : l'export F22 les restitue, l'anonymisation F23 les
 * vide par une mise à jour en masse du dépôt, jamais par cette entité.</p>
 *
 * <p>Un administrateur n'agit qu'à la demande explicite du membre (A12) et doit en
 * donner le motif. La règle est portée ici et par {@code ck_histo_compte_motif} (V57).</p>
 */
@Entity
@Table(name = "historique_modification_compte")
@EntityListeners(AuditingEntityListener.class)
public class ModificationCompte {

    /** Longueur de {@code motif} en base. */
    public static final int MOTIF_MAX = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false, updatable = false)
    private Utilisateur utilisateur;

    @Enumerated(EnumType.STRING)
    @Column(name = "champ", nullable = false, length = 20, updatable = false)
    private ChampCompte champ;

    @Column(name = "valeur_avant", updatable = false)
    private String valeurAvant;

    @Column(name = "valeur_apres", updatable = false)
    private String valeurApres;

    @Column(name = "horodatage", nullable = false, updatable = false)
    private Instant horodatage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auteur_id", updatable = false)
    private Utilisateur auteur;

    @Column(name = "motif", length = MOTIF_MAX, updatable = false)
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

    protected ModificationCompte() {
        // requis par JPA
    }

    /**
     * @param auteur le titulaire lui-même, ou l'administrateur qui agit à sa demande
     * @param motif  obligatoire quand l'auteur n'est pas le titulaire
     */
    public ModificationCompte(Utilisateur utilisateur, ChangementChamp changement,
                              Instant horodatage, Utilisateur auteur, String motif) {
        this.utilisateur = Objects.requireNonNull(utilisateur, "utilisateur");
        Objects.requireNonNull(changement, "changement");
        this.horodatage = Objects.requireNonNull(horodatage, "horodatage");
        this.auteur = Objects.requireNonNull(auteur, "auteur");
        if (!memeCompte(auteur, utilisateur) && (motif == null || motif.isBlank())) {
            throw new IllegalArgumentException(
                    "Une modification faite par un administrateur porte le motif de la demande.");
        }
        this.champ = changement.champ();
        this.valeurAvant = changement.avant();
        this.valeurApres = changement.apres();
        this.motif = motif == null || motif.isBlank() ? null : motif.strip();
    }

    public Long getId() { return id; }
    public Utilisateur getUtilisateur() { return utilisateur; }
    public ChampCompte getChamp() { return champ; }
    public String getValeurAvant() { return valeurAvant; }
    public String getValeurApres() { return valeurApres; }
    public Instant getHorodatage() { return horodatage; }
    public Utilisateur getAuteur() { return auteur; }
    public String getMotif() { return motif; }

    /** Faite par le titulaire du compte, et non par un administrateur. */
    public boolean parTitulaire() {
        return auteur != null && memeCompte(auteur, utilisateur);
    }

    private static boolean memeCompte(Utilisateur a, Utilisateur b) {
        return a == b || (a.getId() != null && a.getId().equals(b.getId()));
    }
}
