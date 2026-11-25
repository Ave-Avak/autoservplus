package be.autoservplus.facturation.domain;

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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Règlement d'une facture d'atelier au comptoir (RM-20 révisée, Q-H-43 à Q-H-45).
 *
 * <p><b>Un encaissement règle la facture entière</b>, par un seul moyen : ni paiement
 * partiel ni paiement mixte en V1. Le montant est donc le total TVAC de la facture, et le
 * domaine le vérifie plutôt que de le recopier : un appelant qui transmettrait un autre
 * montant est refusé ici, pas corrigé en silence.</p>
 *
 * <p><b>Espèces plafonnées</b> : au-delà de 3000 € TVAC, une facture ne se règle pas en
 * espèces (art. 67 de la loi du 18 septembre 2017, le total de la facture faisant foi). Le
 * plafond vient de la configuration, le domaine le reçoit.</p>
 *
 * <p><b>Pas de suppression</b>, comme la facture et la note de crédit : une erreur de
 * saisie s'annule, avec un motif, et l'encaissement juste s'enregistre ensuite. Seules les
 * colonnes de l'annulation bougent, une fois ({@code tg_encaissement_immuable}, V75).</p>
 */
@Entity
@Table(name = "encaissement")
@EntityListeners(AuditingEntityListener.class)
public class Encaissement {

    /** Longueur maximale du motif d'annulation, celle de la colonne (V75). */
    public static final int MOTIF_MAX = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference", nullable = false, updatable = false)
    private UUID reference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "facture_id", nullable = false, updatable = false)
    private Facture facture;

    @Enumerated(EnumType.STRING)
    @Column(name = "moyen", nullable = false, updatable = false, length = 20)
    private MoyenEncaissement moyen;

    @Column(name = "montant", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal montant;

    @Column(name = "date_encaissement", nullable = false, updatable = false)
    private LocalDate dateEncaissement;

    @Column(name = "annule_le")
    private Instant annuleLe;

    @Column(name = "annule_par", length = 120)
    private String annulePar;

    @Column(name = "motif_annulation", length = MOTIF_MAX)
    private String motifAnnulation;

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

    protected Encaissement() {
        // requis par JPA
    }

    /**
     * Enregistre le règlement d'une facture d'atelier.
     *
     * @param emission       jour d'émission de la facture, au fuseau du garage
     * @param aujourdHui     jour courant, au fuseau du garage
     * @param plafondEspeces total TVAC au-delà duquel les espèces sont refusées
     * @throws EncaissementRefuseException facture à 0 €, montant différent du total, date
     *         future ou antérieure à l'émission, espèces au-delà du plafond
     */
    public static Encaissement enregistrer(Facture facture, MoyenEncaissement moyen, BigDecimal montant,
                                           LocalDate date, LocalDate emission, LocalDate aujourdHui,
                                           BigDecimal plafondEspeces) {
        Objects.requireNonNull(facture, "facture");
        Objects.requireNonNull(moyen, "moyen");
        Objects.requireNonNull(date, "date");
        BigDecimal total = facture.getMontantTvac();
        if (total.signum() == 0) {
            throw EncaissementRefuseException.rienARegler();
        }
        if (montant == null || montant.compareTo(total) != 0) {
            throw EncaissementRefuseException.montantDifferent();
        }
        if (date.isAfter(aujourdHui)) {
            throw EncaissementRefuseException.dateFuture();
        }
        if (date.isBefore(emission)) {
            throw EncaissementRefuseException.dateAvantEmission();
        }
        if (moyen == MoyenEncaissement.ESPECES && total.compareTo(plafondEspeces) > 0) {
            throw EncaissementRefuseException.especesAuDelaDuPlafond(plafondEspeces);
        }
        Encaissement encaissement = new Encaissement();
        encaissement.reference = UUID.randomUUID();
        encaissement.facture = facture;
        encaissement.moyen = moyen;
        encaissement.montant = total;
        encaissement.dateEncaissement = date;
        return encaissement;
    }

    /**
     * Annule l'encaissement, une fois, avec un motif obligatoire. La facture redevient à
     * régler, et un nouvel encaissement peut être enregistré.
     */
    public void annuler(String motif, String auteur, Instant instant) {
        if (estAnnule()) {
            throw EncaissementRefuseException.dejaAnnule();
        }
        String texte = motif == null ? "" : motif.strip();
        if (texte.isEmpty()) {
            throw EncaissementRefuseException.motifObligatoire();
        }
        if (texte.length() > MOTIF_MAX) {
            throw EncaissementRefuseException.motifTropLong();
        }
        this.motifAnnulation = texte;
        this.annulePar = Objects.requireNonNull(auteur, "auteur");
        this.annuleLe = Objects.requireNonNull(instant, "instant");
    }

    public boolean estAnnule() {
        return annuleLe != null;
    }

    public Long getId() { return id; }
    public UUID getReference() { return reference; }
    public Facture getFacture() { return facture; }
    public MoyenEncaissement getMoyen() { return moyen; }
    public BigDecimal getMontant() { return montant; }
    public LocalDate getDateEncaissement() { return dateEncaissement; }
    public Instant getAnnuleLe() { return annuleLe; }
    public String getAnnulePar() { return annulePar; }
    public String getMotifAnnulation() { return motifAnnulation; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }

    @Override
    public boolean equals(Object autre) {
        if (this == autre) return true;
        if (!(autre instanceof Encaissement e)) return false;
        return reference != null && reference.equals(e.reference);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(reference);
    }
}
