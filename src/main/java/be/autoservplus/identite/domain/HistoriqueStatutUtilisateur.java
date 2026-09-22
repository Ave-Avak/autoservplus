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
import jakarta.validation.constraints.NotNull;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;

/**
 * Une suspension ou une reactivation de compte, telle qu elle s est produite
 * (CdC 5.2.3).
 *
 * <p>Journal append-only, sur le patron de {@code HistoriqueStatutIntervention} : une
 * ligne par transition, ecrite dans la transaction de la transition, jamais modifiee
 * ensuite. Aucun {@code deleted_at} — rien ne se supprime dans un journal.</p>
 *
 * <p><b>Pourquoi cette table alors que {@code utilisateur} porte deja
 * {@code updated_by}.</b> Les colonnes d audit gardent le DERNIER geste, jamais la
 * suite : suspendre, reactiver, resuspendre n y laisse qu une ligne. Or la question
 * qu on pose a un journal — qui a suspendu ce compte, quand, et pourquoi — porte
 * precisement sur ce que l audit ecrase.</p>
 *
 * <p>{@code horodatage} est l instant <b>metier</b>, fourni par l horloge injectee du
 * service, distinct de {@code createdAt} que la base pose. {@code auteur} est
 * nullable et sa cle etrangere est en {@code ON DELETE SET NULL} : la trace doit
 * survivre a la disparition de son auteur, faute de quoi supprimer un compte
 * effacerait l historique des decisions qu il a prises sur les autres.</p>
 */
@Entity
@Table(name = "historique_statut_utilisateur")
@EntityListeners(AuditingEntityListener.class)
public class HistoriqueStatutUtilisateur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false, updatable = false)
    private Utilisateur utilisateur;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "statut_avant", nullable = false, length = 30, updatable = false)
    private StatutUtilisateur statutAvant;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "statut_apres", nullable = false, length = 30, updatable = false)
    private StatutUtilisateur statutApres;

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

    protected HistoriqueStatutUtilisateur() {
        // requis par JPA
    }

    /**
     * @param motif obligatoire a la suspension, sans objet a la reactivation —
     *              l obligation est portee par le service, la ou la distinction entre
     *              les deux gestes existe, et non par la base qui sert les deux
     */
    public HistoriqueStatutUtilisateur(Utilisateur utilisateur, StatutUtilisateur statutAvant,
                                       StatutUtilisateur statutApres, Instant horodatage,
                                       Utilisateur auteur, String motif) {
        this.utilisateur = Objects.requireNonNull(utilisateur, "utilisateur");
        this.statutAvant = Objects.requireNonNull(statutAvant, "statutAvant");
        this.statutApres = Objects.requireNonNull(statutApres, "statutApres");
        this.horodatage = Objects.requireNonNull(horodatage, "horodatage");
        if (statutAvant == statutApres) {
            throw new IllegalArgumentException(
                    "Une transition change le statut : " + statutAvant + " inchange.");
        }
        this.auteur = auteur;
        this.motif = motif;
    }

    public Long getId() { return id; }
    public Utilisateur getUtilisateur() { return utilisateur; }
    public StatutUtilisateur getStatutAvant() { return statutAvant; }
    public StatutUtilisateur getStatutApres() { return statutApres; }
    public Instant getHorodatage() { return horodatage; }
    public Utilisateur getAuteur() { return auteur; }
    public String getMotif() { return motif; }
}
