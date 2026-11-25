package be.autoservplus.facturation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Note de crédit : <b>seul moyen légal de corriger une facture déjà émise</b> (F30).
 *
 * <p>La facture ne se rectifie pas et ne s'annule pas : le trigger
 * {@code tg_facture_immuable} (V6) le refuse en base, et c'est précisément ce refus
 * qui justifie l'existence de cette entité. Corriger une facture, c'est émettre un
 * second document qui la contre-passe. L'original reste au dossier, les deux se
 * lisent ensemble.</p>
 *
 * <p><b>Contre-passation exacte</b> : les trois montants sont ceux de la facture
 * d'origine, recopiés tels quels et <b>positifs</b>. Le sens est porté par la nature
 * du document, pas par le signe des montants : le CHECK {@code ck_avoir_montants} du
 * socle exige d'ailleurs des montants positifs ou nuls. Un avoir de 48,38 EUR annule
 * une facture de 48,38 EUR. Le stocker en -48,38 obligerait chaque lecteur à savoir
 * quelle convention de signe a été retenue.</p>
 *
 * <p><b>Pas de suppression logique</b>, comme {@link Facture} : la loi impose sept
 * ans de conservation (Code TVA art. 60) et il n'existe aucune correction d'une
 * correction. L'entité n'hérite donc pas de {@code BaseEntity}, qui porterait
 * {@code deleted_at}. Elle est immuable après émission, défendue à deux étages :
 * {@code updatable = false} au mapping, {@code tg_avoir_immuable} (V27) en base.
 * Seul {@code cheminPdf} bouge. Le document est archivé après coup, sans que son
 * contenu comptable change.</p>
 *
 * <p><b>Motif</b> : stocké sous une forme stable et non traduite (le nom du motif
 * légal retenu). Le PDF l'imprime dans la langue du membre via le
 * {@code MessageSource}. Ranger ici une phrase française donnerait une note de
 * crédit française à un client néerlandophone, alors que la facture qu'elle corrige
 * aurait été émise en néerlandais. Les mots du membre, eux, restent sur sa demande :
 * ils expliquent sa décision, ils ne fondent pas le document comptable.</p>
 */
@Entity
@Table(name = "avoir")
@EntityListeners(AuditingEntityListener.class)
public class Avoir {

    /** Motif légal d'un avoir de rétractation (CDE art. VI.47). Valeur stable, non traduite. */
    public static final String MOTIF_RETRACTATION = "RETRACTATION_F30";

    /** Annulation d'une facture d'atelier par l'administrateur (Q-H-14, Q-H-50). Valeur stable. */
    public static final String MOTIF_ANNULATION_ATELIER = "ANNULATION_ATELIER";

    /** Longueur maximale de la précision, celle de la colonne (V74). */
    public static final int PRECISION_MAX = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference", nullable = false, updatable = false)
    private UUID reference;

    @Column(name = "numero", nullable = false, updatable = false, length = 20)
    private String numero;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "facture_id", nullable = false, updatable = false)
    private Facture facture;

    @Column(name = "montant_htva", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal montantHtva;

    @Column(name = "montant_tva", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal montantTva;

    @Column(name = "montant_tvac", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal montantTvac;

    @Column(name = "motif", nullable = false, updatable = false, columnDefinition = "text")
    private String motif;

    /**
     * Texte libre de l'administrateur sur une annulation d'atelier, imprimé tel que saisi,
     * dans sa langue. Nul pour une rétractation ({@code ck_avoir_precision}, V74).
     */
    @Column(name = "precision", updatable = false, length = PRECISION_MAX)
    private String precision;

    @Column(name = "date_emission", nullable = false, updatable = false)
    private Instant dateEmission;

    /** Chemin du PDF archivé, posé à la première génération (à la demande). */
    @Column(name = "chemin_pdf", length = 255)
    private String cheminPdf;

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

    protected Avoir() {
        // requis par JPA
    }

    /**
     * Émet la note de crédit qui contre-passe intégralement une facture (périmètre
     * V1 : annulation totale). Les montants sont ceux de la facture, jamais
     * recalculés : c'est ce document précis qui est corrigé, avec les taux figés qui
     * étaient les siens. Les recalculer depuis le catalogue du jour produirait, sur
     * un prix ou un taux modifié entre-temps, un avoir qui n'annulerait pas la
     * facture au centime près.
     */
    public static Avoir contrePassant(String numero, Facture facture, String motif,
                                      Instant dateEmission) {
        if (MOTIF_ANNULATION_ATELIER.equals(motif)) {
            throw new IllegalArgumentException("Une annulation d'atelier passe par annulantFactureAtelier.");
        }
        return emis(numero, facture, motif, null, dateEmission);
    }

    /**
     * Note de crédit totale d'une facture d'atelier, émise par l'administrateur avec une
     * précision obligatoire (Q-H-14, Q-H-50). Elle n'annule que cette facture : celle de la
     * commande dont l'intervention peut naître se corrige par la rétractation (Q-H-52).
     */
    public static Avoir annulantFactureAtelier(String numero, Facture facture, String precision,
                                               Instant dateEmission) {
        Objects.requireNonNull(facture, "facture");
        if (!facture.estDAtelier()) {
            throw new IllegalArgumentException("La facture " + facture.getNumero() + " n'est pas une facture d'atelier.");
        }
        String texte = precision == null ? "" : precision.strip();
        if (texte.isEmpty() || texte.length() > PRECISION_MAX) {
            throw new IllegalArgumentException("Précision obligatoire, de 500 caractères au plus.");
        }
        return emis(numero, facture, MOTIF_ANNULATION_ATELIER, texte, dateEmission);
    }

    private static Avoir emis(String numero, Facture facture, String motif, String precision,
                              Instant dateEmission) {
        Objects.requireNonNull(facture, "facture");
        Avoir avoir = new Avoir();
        avoir.reference = UUID.randomUUID();
        avoir.numero = Objects.requireNonNull(numero, "numero");
        avoir.facture = facture;
        avoir.montantHtva = Objects.requireNonNull(facture.getMontantHtva(), "montantHtva");
        avoir.montantTva = Objects.requireNonNull(facture.getMontantTva(), "montantTva");
        avoir.montantTvac = Objects.requireNonNull(facture.getMontantTvac(), "montantTvac");
        avoir.motif = Objects.requireNonNull(motif, "motif");
        avoir.precision = precision;
        avoir.dateEmission = Objects.requireNonNull(dateEmission, "dateEmission");
        // Même invariant que sur la facture, défendu aux deux étages : l'entité avant
        // la base (ck_avoir_coherence), pour échouer là où l'erreur est compréhensible.
        if (avoir.montantHtva.add(avoir.montantTva).compareTo(avoir.montantTvac) != 0) {
            throw new IllegalArgumentException(
                    "Montants incoherents : HTVA + TVA doit valoir TVAC.");
        }
        return avoir;
    }

    /**
     * Enregistre l'emplacement du PDF archivé. Seule mutation admise après émission :
     * elle ne touche aucune donnée comptable, et {@code tg_avoir_immuable} l'autorise
     * pour cette raison précise.
     */
    public void archiverPdf(String chemin) {
        this.cheminPdf = Objects.requireNonNull(chemin, "chemin");
    }

    public boolean estArchive() {
        return cheminPdf != null;
    }

    /**
     * Exercice comptable de la note de crédit, lu de son numéro.
     *
     * <p>Le numéro a été attribué par le compteur de l'exercice. Le relire est donc
     * exact par construction, là où déduire l'année de {@code date_emission}
     * dépendrait du fuseau retenu pour la conversion et pourrait basculer d'un
     * exercice à l'autre dans la nuit du 31 décembre.</p>
     */
    public short exercice() {
        return Short.parseShort(numero.substring(numero.indexOf('-') + 1, numero.lastIndexOf('-')));
    }

    public Long getId() { return id; }
    public UUID getReference() { return reference; }
    public String getNumero() { return numero; }
    public Facture getFacture() { return facture; }
    public BigDecimal getMontantHtva() { return montantHtva; }
    public BigDecimal getMontantTva() { return montantTva; }
    public BigDecimal getMontantTvac() { return montantTvac; }
    public String getMotif() { return motif; }
    public String getPrecision() { return precision; }
    public Instant getDateEmission() { return dateEmission; }
    public String getCheminPdf() { return cheminPdf; }

    @Override
    public boolean equals(Object autre) {
        if (this == autre) return true;
        if (!(autre instanceof Avoir avoir)) return false;
        return id != null && id.equals(avoir.id);
    }

    @Override
    public int hashCode() {
        return Avoir.class.hashCode();
    }
}
