package be.autoservplus.intervention.domain;

import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.identite.domain.Utilisateur;
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
 * Un changement des pièces incluses d'une intervention (V77), cinquième source du journal
 * d'audit. Append-only, comme {@link HistoriqueStatutIntervention} : écrit dans la transaction
 * du changement, jamais modifié.
 *
 * <p>La copie initiale y figure aussi : la trace d'un dossier se lit sans relire l'historique
 * du catalogue, qui ne dit pas quelle composition valait le jour de la création. La ligne est
 * nulle après son retrait ({@code ON DELETE SET NULL}), l'intervention reste.</p>
 */
@Entity
@Table(name = "historique_piece_incluse")
@EntityListeners(AuditingEntityListener.class)
public class HistoriquePieceIncluse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "intervention_id", nullable = false, updatable = false)
    private Intervention intervention;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ligne_intervention_id", updatable = false)
    private LigneIntervention ligne;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "piece_id", nullable = false, updatable = false)
    private Piece piece;

    @Enumerated(EnumType.STRING)
    @Column(name = "evenement", nullable = false, length = 12, updatable = false)
    private EvenementPieceIncluse evenement;

    @Column(name = "quantite_avant", updatable = false)
    private Integer quantiteAvant;

    @Column(name = "quantite_apres", updatable = false)
    private Integer quantiteApres;

    @Column(name = "horodatage", nullable = false, updatable = false)
    private Instant horodatage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auteur_id", updatable = false)
    private Utilisateur auteur;

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

    protected HistoriquePieceIncluse() {
        // requis par JPA
    }

    /**
     * @param auteur {@code null} pour une écriture sans administrateur identifié. La trace
     *               survit à la disparition du compte ({@code ON DELETE SET NULL}).
     */
    public HistoriquePieceIncluse(Intervention intervention, ChangementPieceIncluse changement,
                                  Instant horodatage, Utilisateur auteur) {
        this.intervention = Objects.requireNonNull(intervention, "intervention");
        Objects.requireNonNull(changement, "changement");
        this.ligne = changement.ligne();
        this.piece = changement.piece();
        this.evenement = changement.evenement();
        this.quantiteAvant = changement.avant();
        this.quantiteApres = changement.apres();
        this.horodatage = Objects.requireNonNull(horodatage, "horodatage");
        this.auteur = auteur;
    }

    public Long getId() { return id; }
    public Intervention getIntervention() { return intervention; }
    public LigneIntervention getLigne() { return ligne; }
    public Piece getPiece() { return piece; }
    public EvenementPieceIncluse getEvenement() { return evenement; }
    public Integer getQuantiteAvant() { return quantiteAvant; }
    public Integer getQuantiteApres() { return quantiteApres; }
    public Instant getHorodatage() { return horodatage; }
    public Utilisateur getAuteur() { return auteur; }

    @Override
    public boolean equals(Object autre) {
        if (this == autre) return true;
        if (!(autre instanceof HistoriquePieceIncluse historique)) return false;
        return id != null && id.equals(historique.id);
    }

    @Override
    public int hashCode() {
        return HistoriquePieceIncluse.class.hashCode();
    }
}
