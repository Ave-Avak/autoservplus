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
 * Note de credit : <b>seul moyen legal de corriger une facture deja emise</b> (F30).
 *
 * <p>La facture ne se rectifie pas et ne s annule pas — le trigger
 * {@code tg_facture_immuable} (V6) le refuse en base, et c est precisement ce refus
 * qui justifie l existence de cette entite. Corriger une facture, c est emettre un
 * second document qui la contre-passe ; l original reste au dossier, les deux se
 * lisent ensemble.</p>
 *
 * <p><b>Contre-passation exacte</b> : les trois montants sont ceux de la facture
 * d origine, recopies tels quels et <b>positifs</b>. Le sens est porte par la nature
 * du document, pas par le signe des montants — le CHECK {@code ck_avoir_montants} du
 * socle exige d ailleurs des montants positifs ou nuls. Un avoir de 48,38 EUR annule
 * une facture de 48,38 EUR ; le stocker en -48,38 obligerait chaque lecteur a savoir
 * quelle convention de signe a ete retenue.</p>
 *
 * <p><b>Pas de suppression logique</b>, comme {@link Facture} : la loi impose sept
 * ans de conservation (Code TVA art. 60) et il n existe aucune correction d une
 * correction. L entite n herite donc pas de {@code BaseEntity}, qui porterait
 * {@code deleted_at}. Elle est immuable apres emission, defendue a deux etages :
 * {@code updatable = false} au mapping, {@code tg_avoir_immuable} (V27) en base.
 * Seul {@code cheminPdf} bouge — le document est archive apres coup, sans que son
 * contenu comptable change.</p>
 *
 * <p><b>Motif</b> : stocke sous une forme stable et non traduite (le nom du motif
 * legal retenu). Le PDF l imprime dans la langue du membre via le
 * {@code MessageSource}. Ranger ici une phrase francaise donnerait une note de
 * credit francaise a un client neerlandophone, alors que la facture qu elle corrige
 * aurait ete emise en neerlandais. Les mots du membre, eux, restent sur sa demande :
 * ils expliquent sa decision, ils ne fondent pas le document comptable.</p>
 */
@Entity
@Table(name = "avoir")
@EntityListeners(AuditingEntityListener.class)
public class Avoir {

    /** Motif legal d un avoir de retractation (CDE art. VI.47). Valeur stable, non traduite. */
    public static final String MOTIF_RETRACTATION = "RETRACTATION_F30";

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

    @Column(name = "date_emission", nullable = false, updatable = false)
    private Instant dateEmission;

    /** Chemin du PDF archive, pose a la premiere generation (a la demande). */
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
     * Emet la note de credit qui contre-passe integralement une facture (perimetre
     * V1 : annulation totale). Les montants sont ceux de la facture, jamais
     * recalcules : c est ce document precis qui est corrige, avec les taux figes qui
     * etaient les siens. Les recalculer depuis le catalogue du jour produirait, sur
     * un prix ou un taux modifie entre-temps, un avoir qui n annulerait pas la
     * facture au centime pres.
     */
    public static Avoir contrePassant(String numero, Facture facture, String motif,
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
        avoir.dateEmission = Objects.requireNonNull(dateEmission, "dateEmission");
        // Meme invariant que sur la facture, defendu aux deux etages : l entite avant
        // la base (ck_avoir_coherence), pour echouer la ou l erreur est comprehensible.
        if (avoir.montantHtva.add(avoir.montantTva).compareTo(avoir.montantTvac) != 0) {
            throw new IllegalArgumentException(
                    "Montants incoherents : HTVA + TVA doit valoir TVAC.");
        }
        return avoir;
    }

    /**
     * Enregistre l emplacement du PDF archive. Seule mutation admise apres emission :
     * elle ne touche aucune donnee comptable, et {@code tg_avoir_immuable} l autorise
     * pour cette raison precise.
     */
    public void archiverPdf(String chemin) {
        this.cheminPdf = Objects.requireNonNull(chemin, "chemin");
    }

    public boolean estArchive() {
        return cheminPdf != null;
    }

    /**
     * Exercice comptable de la note de credit, lu de son numero.
     *
     * <p>Le numero a ete attribue par le compteur de l exercice ; le relire est donc
     * exact par construction, la ou deduire l annee de {@code date_emission}
     * dependrait du fuseau retenu pour la conversion et pourrait basculer d un
     * exercice a l autre dans la nuit du 31 decembre.</p>
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
