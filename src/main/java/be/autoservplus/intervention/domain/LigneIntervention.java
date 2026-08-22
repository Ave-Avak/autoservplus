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
 * <p><b>Marqueurs RM-15</b> : l etat d une ligne se lit sur le <b>couple</b>
 * {@code (ajouteeEnCours, accordMembre)}, aligne sur le dictionnaire de donnees
 * (Livrable 09) :</p>
 * <table>
 *   <caption>Encodage des quatre etats</caption>
 *   <tr><td>{@code (false, null)}</td><td>ligne du RDV : le devis initial, hors RM-15</td></tr>
 *   <tr><td>{@code (true,  null)}</td><td>ajout du garage, en attente de reponse</td></tr>
 *   <tr><td>{@code (true,  true)}</td><td>ajout accepte : facturable</td></tr>
 *   <tr><td>{@code (true, false)}</td><td>ajout refuse : conserve, hors total, non execute</td></tr>
 * </table>
 *
 * <p>Un champ a trois valeurs plutot que deux booleens : l etat absurde
 * « acceptee ET refusee » devient <b>inexprimable</b> au lieu d etre interdit par
 * un CHECK. Le seul CHECK restant, {@code ck_ligne_interv_accord}, verrouille la
 * premiere ligne du tableau — une ligne du devis initial ne porte aucun accord.</p>
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

    /**
     * Reponse du membre sur une ligne ajoutee en cours (RM-15). {@code null} vaut
     * « pas de reponse » : soit la ligne n en attend aucune (devis initial), soit
     * elle attend celle du membre. C est le couple avec {@link #ajouteeEnCours} qui
     * tranche — voir les predicats derives plus bas.
     *
     * <p>{@link Boolean} et non {@code boolean} : le troisieme etat est porteur de
     * sens metier, pas un trou de donnee. Aucun DEFAULT en base pour la meme raison.</p>
     */
    @Column(name = "accord_membre")
    private Boolean accordMembre;

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
     * La ligne sort du total facturable en attendant l accord du membre (RM-15) :
     * la question est posee, aucune reponse n est encore donnee.
     */
    void mettreEnAttente() {
        this.accordMembre = null;
    }

    /** Le membre accepte cette ligne : elle rejoint le total facturable. */
    void accepter() {
        this.accordMembre = Boolean.TRUE;
    }

    /**
     * Le membre ecarte cette ligne. Elle reste dans le dossier — elle documente le
     * defaut constate par le garage — mais sort definitivement du total facturable
     * et ne sera pas executee.
     */
    void refuser() {
        this.accordMembre = Boolean.FALSE;
    }

    // --- etats derives du couple (ajouteeEnCours, accordMembre) ---------------------
    //
    // Aucun de ces quatre etats n est stocke : ils se lisent tous du couple, ce qui
    // rend impossible la divergence entre un drapeau et la realite. Ils sont exclusifs
    // et exhaustifs — toute ligne est dans exactement un des quatre.

    /**
     * Ligne issue du rendez-vous : elle compose le devis initial, accepte par le
     * membre a la reservation. Hors dispositif RM-15 — on ne redemande pas un accord
     * sur ce qui a deja ete commande. Le CHECK {@code ck_ligne_interv_accord}
     * garantit qu une telle ligne ne porte jamais d accord.
     */
    public boolean estDuDevisInitial() {
        return !ajouteeEnCours;
    }

    /** Ajout du garage soumis au membre, sans reponse a ce jour. */
    public boolean estEnAttenteValidation() {
        return ajouteeEnCours && accordMembre == null;
    }

    /** Ajout du garage accepte par le membre. */
    public boolean estAcceptee() {
        return ajouteeEnCours && Boolean.TRUE.equals(accordMembre);
    }

    /** Ajout du garage refuse par le membre : conserve au dossier, hors total. */
    public boolean estRefusee() {
        return ajouteeEnCours && Boolean.FALSE.equals(accordMembre);
    }

    /**
     * La ligne entre-t-elle dans le total facturable ? Le devis initial y entre
     * toujours ; un ajout n y entre qu une fois accepte.
     *
     * <p>Sont donc exclues les lignes <b>en attente</b> et les lignes <b>refusees</b>,
     * pour des raisons differentes : la premiere n est pas encore acquise, la seconde
     * ne le sera jamais.</p>
     */
    public boolean estFacturable() {
        return estDuDevisInitial() || estAcceptee();
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
    public Boolean getAccordMembre() { return accordMembre; }

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
