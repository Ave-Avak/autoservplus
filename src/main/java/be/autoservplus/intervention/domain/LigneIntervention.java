package be.autoservplus.intervention.domain;

import be.autoservplus.catalogue.domain.Piece;
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
 * Article facturable d une intervention : main d oeuvre (prestation du catalogue)
 * ou piece detachee. Le libelle et les prix sont figes a la creation, pour que
 * l historique reste stable meme si le catalogue evolue (RM-30, meme regle que
 * {@link be.autoservplus.reservation.domain.LigneRdv}).
 *
 * <p>Pas de suppression logique : la ligne n existe que par son intervention,
 * un retrait passe par {@code orphanRemoval} de la relation parente.</p>
 *
 * <p><b>Marqueurs RM-15</b> : {@code ajouteeEnCours} distingue les lignes issues
 * du rendez-vous (le devis initial, accepte par le membre a la reservation) de
 * celles ajoutees par le garage pendant le travail. {@code validee} dit si la
 * ligne entre dans le total facturable, {@code refusee} si le membre l a ecartee.
 * Une ligne refusee reste en base — elle documente le defaut constate — mais
 * sort du total et n est pas executee. Les deux drapeaux sont exclusifs
 * (CHECK {@code ck_ligne_interv_validation}).</p>
 */
@Entity
@Table(name = "ligne_intervention")
@EntityListeners(AuditingEntityListener.class)
public class LigneIntervention {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "intervention_id", nullable = false)
    private Intervention intervention;

    // Exactement un des deux est non-null (CHECK ck_ligne_interv_article).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    private Prestation prestation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "piece_id")
    private Piece piece;

    @NotNull
    @Column(name = "libelle_fige", nullable = false, length = 150)
    private String libelleFige;

    @Column(name = "quantite", nullable = false)
    private short quantite;

    @Column(name = "prix_unitaire_htva", nullable = false, precision = 10, scale = 2)
    private BigDecimal prixUnitaireHtva;

    @Column(name = "taux_tva", nullable = false, precision = 5, scale = 2)
    private BigDecimal tauxTva;

    @Column(name = "ajoutee_en_cours", nullable = false)
    private boolean ajouteeEnCours;

    @Column(name = "validee", nullable = false)
    private boolean validee = true;

    @Column(name = "refusee", nullable = false)
    private boolean refusee;

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

    protected LigneIntervention() {
        // requis par JPA
    }

    /**
     * Ligne de main d oeuvre creee depuis une prestation du catalogue.
     *
     * @param ajouteeEnCours {@code false} pour une ligne issue du RDV (devis initial),
     *                       {@code true} pour un ajout du garage pendant l intervention.
     */
    LigneIntervention(Intervention intervention, Prestation prestation, short quantite,
                      BigDecimal prixUnitaireHtva, BigDecimal tauxTva, boolean ajouteeEnCours) {
        this.intervention = Objects.requireNonNull(intervention, "intervention");
        this.prestation = Objects.requireNonNull(prestation, "prestation");
        exigerQuantitePositive(quantite);
        this.libelleFige = prestation.getLibelle();
        this.quantite = quantite;
        this.prixUnitaireHtva = Objects.requireNonNull(prixUnitaireHtva, "prixUnitaireHtva");
        this.tauxTva = Objects.requireNonNull(tauxTva, "tauxTva");
        this.ajouteeEnCours = ajouteeEnCours;
    }

    /** Ligne de piece detachee prelevee du catalogue. Les prix figent ceux du catalogue. */
    public LigneIntervention(Intervention intervention, Piece piece, short quantite,
                             boolean ajouteeEnCours) {
        this.intervention = Objects.requireNonNull(intervention, "intervention");
        this.piece = Objects.requireNonNull(piece, "piece");
        exigerQuantitePositive(quantite);
        this.libelleFige = piece.getLibelle();
        this.quantite = quantite;
        this.prixUnitaireHtva = piece.getPrixHtva();
        this.tauxTva = piece.getTauxTva();
        this.ajouteeEnCours = ajouteeEnCours;
    }

    private static void exigerQuantitePositive(short quantite) {
        if (quantite < 1) {
            throw new IllegalArgumentException("La quantite doit valoir au moins 1.");
        }
    }

    /** Type deduit du champ non-null. */
    public TypeLigneIntervention getType() {
        return prestation != null ? TypeLigneIntervention.MAIN_OEUVRE : TypeLigneIntervention.PIECE;
    }

    public BigDecimal totalHtva() {
        return prixUnitaireHtva.multiply(BigDecimal.valueOf(quantite))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** Total TVAC, taux fige a la creation. */
    public BigDecimal totalTvac() {
        BigDecimal coefficient = BigDecimal.ONE.add(
                tauxTva.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));
        return totalHtva().multiply(coefficient).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * La ligne sort du total facturable en attendant l accord du membre (RM-15).
     * Elle reste ni validee ni refusee : le troisieme etat, celui de la question
     * posee au client.
     */
    void mettreEnAttente() {
        this.validee = false;
        this.refusee = false;
    }

    /** Le membre accepte cette ligne : elle rejoint le total facturable. */
    void valider() {
        this.validee = true;
        this.refusee = false;
    }

    /**
     * Le membre ecarte cette ligne. Elle reste dans le dossier — elle documente le
     * defaut constate par le garage — mais sort definitivement du total facturable
     * et ne sera pas executee. {@code validee} retombe a false pour respecter
     * l exclusion mutuelle imposee par {@code ck_ligne_interv_validation}.
     */
    void refuser() {
        this.validee = false;
        this.refusee = true;
    }

    /**
     * La ligne entre-t-elle dans le total facturable ? Vrai uniquement si elle est
     * validee et non refusee. Une ligne en attente d accord du membre et une ligne
     * refusee sont toutes deux exclues, pour des raisons differentes : la premiere
     * n est pas encore acquise, la seconde ne le sera jamais.
     */
    public boolean estFacturable() {
        return validee && !refusee;
    }

    /** La ligne attend une reponse du membre : ni acquise, ni ecartee. */
    public boolean estEnAttente() {
        return !validee && !refusee;
    }

    public Long getId() { return id; }
    public Intervention getIntervention() { return intervention; }
    public Prestation getPrestation() { return prestation; }
    public Piece getPiece() { return piece; }
    public String getLibelleFige() { return libelleFige; }
    public short getQuantite() { return quantite; }
    public BigDecimal getPrixUnitaireHtva() { return prixUnitaireHtva; }
    public BigDecimal getTauxTva() { return tauxTva; }
    public boolean isAjouteeEnCours() { return ajouteeEnCours; }
    public boolean isValidee() { return validee; }
    public boolean isRefusee() { return refusee; }

    @Override
    public boolean equals(Object autre) {
        if (this == autre) return true;
        if (!(autre instanceof LigneIntervention ligne)) return false;
        return id != null && id.equals(ligne.id);
    }

    @Override
    public int hashCode() {
        return LigneIntervention.class.hashCode();
    }
}
