package be.autoservplus.reservation.domain;

import be.autoservplus.catalogue.domain.Prestation;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

/**
 * Prestation retenue dans un rendez-vous, au prix en vigueur lors de la reservation.
 *
 * <p>Le prix unitaire et le taux de taxe sont recopies depuis le catalogue au moment de
 * la demande. Une hausse tarifaire ulterieure ne modifie donc pas un rendez-vous deja
 * pris : le client paie le prix qui lui a ete annonce, conformement au Livre VI du Code
 * de droit economique. Sans cette recopie, la facture serait recalculee au tarif du jour
 * de l intervention.</p>
 *
 * <p>La table ne comporte pas de colonnes de suppression logique : une ligne n existe
 * que par son rendez-vous, dont l annulation est portee par le statut.</p>
 */
@Entity
@Table(name = "rdv_service")
@EntityListeners(AuditingEntityListener.class)
public class LigneRdv {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rdv_id", nullable = false)
    private Rdv rdv;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private Prestation prestation;

    @Column(name = "quantite", nullable = false)
    private short quantite = 1;

    @Column(name = "prix_unitaire_htva", nullable = false, precision = 10, scale = 2)
    private BigDecimal prixUnitaireHtva;

    @Column(name = "taux_tva", nullable = false, precision = 5, scale = 2)
    private BigDecimal tauxTva;

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

    protected LigneRdv() {
        // requis par JPA
    }

    LigneRdv(Rdv rdv, Prestation prestation, short quantite) {
        this.rdv = Objects.requireNonNull(rdv, "rdv");
        this.prestation = Objects.requireNonNull(prestation, "prestation");
        if (quantite < 1) {
            throw new IllegalArgumentException("La quantite doit valoir au moins 1.");
        }
        this.quantite = quantite;
        this.prixUnitaireHtva = prestation.getPrixHtva();
        this.tauxTva = prestation.getTauxTva();
    }

    public BigDecimal totalHtva() {
        return prixUnitaireHtva.multiply(BigDecimal.valueOf(quantite)).setScale(2, RoundingMode.HALF_UP);
    }

    /** Total taxe comprise, calcule sur le taux fige a la reservation (RM-30). */
    public BigDecimal totalTvac() {
        BigDecimal coefficient = BigDecimal.ONE.add(
                tauxTva.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));
        return totalHtva().multiply(coefficient).setScale(2, RoundingMode.HALF_UP);
    }

    public int dureeMinutes() {
        return prestation.getDureeMinutes() * quantite;
    }

    public Long getId() { return id; }
    public Rdv getRdv() { return rdv; }
    public Prestation getPrestation() { return prestation; }
    public short getQuantite() { return quantite; }
    public BigDecimal getPrixUnitaireHtva() { return prixUnitaireHtva; }
    public BigDecimal getTauxTva() { return tauxTva; }

    @Override
    public boolean equals(Object autre) {
        if (this == autre) return true;
        if (!(autre instanceof LigneRdv ligne)) return false;
        return id != null && id.equals(ligne.id);
    }

    @Override
    public int hashCode() {
        return LigneRdv.class.hashCode();
    }
}