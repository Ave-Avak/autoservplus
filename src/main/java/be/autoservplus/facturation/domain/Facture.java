package be.autoservplus.facturation.domain;

import be.autoservplus.identite.domain.Langue;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.intervention.domain.Intervention;
import be.autoservplus.vente.domain.Commande;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.springframework.context.MessageSource;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Facture émise : document comptable légal, immuable dès sa création (F31).
 *
 * <p><b>Pas de suppression logique</b>, contrairement aux entités métier du socle :
 * une facture ne se supprime pas, même logiquement. La loi impose sa conservation
 * sept ans et sa seule correction légale est la note de crédit (table {@code avoir}).
 * Elle n'hérite donc pas de {@code BaseEntity}, qui porterait {@code deleted_at}.
 * Même précédent que les journaux append-only du projet.</p>
 *
 * <p><b>Immuabilité</b> défendue en base par le trigger {@code tg_facture_immuable}
 * (V6), qui refuse toute modification du numéro, des montants, du taux et de la date
 * d'émission. Le mapping le redouble par {@code updatable = false} : la garde de
 * l'entité évite l'aller-retour vers la base, celle de la base survit à n'importe
 * quel appelant. Seul {@code cheminPdf} reste modifiable. Le document est archivé
 * après coup, sans que son contenu comptable bouge.</p>
 *
 * <p><b>Source</b> : une commande OU une intervention, jamais les deux (CHECK
 * {@code ck_facture_origine_unique}). La facture d'une commande suit son paiement en
 * ligne, celle d'une intervention naît à sa clôture (A15, RM-17), à régler à l'atelier.</p>
 *
 * <p><b>Montants figés</b> : ceux de la commande, ou la ventilation des lignes facturées
 * de l'intervention, figées elles aussi. Jamais relus du catalogue : une modification de
 * prix postérieure ne réécrit donc aucune facture.</p>
 */
@Entity
@Table(name = "facture")
@EntityListeners(AuditingEntityListener.class)
public class Facture {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference", nullable = false, updatable = false)
    private UUID reference;

    @Column(name = "numero", nullable = false, updatable = false, length = 20)
    private String numero;

    @Column(name = "exercice", nullable = false, updatable = false)
    private short exercice;

    @Column(name = "sequence_annuelle", nullable = false, updatable = false)
    private int sequenceAnnuelle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commande_id", updatable = false)
    private Commande commande;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "intervention_id", updatable = false)
    private Intervention intervention;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "membre_id", nullable = false, updatable = false)
    private Utilisateur membre;

    @Column(name = "montant_htva", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal montantHtva;

    @Column(name = "montant_tva", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal montantTva;

    @Column(name = "montant_tvac", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal montantTvac;

    /** Taux unique de la facture, {@code null} si elle en mélange plusieurs (V26). */
    @Column(name = "taux_tva_applique", updatable = false, precision = 5, scale = 2)
    private BigDecimal tauxTvaApplique;

    @Column(name = "date_emission", nullable = false, updatable = false)
    private Instant dateEmission;

    @Column(name = "date_echeance", updatable = false)
    private LocalDate dateEcheance;

    // Instantané du client, figé à l'émission (V61) : nom, adresse et langue du
    // document. Une adresse changée ensuite, ou un compte anonymisé, ne réécrit plus la
    // facture. L'adresse peut manquer : voir l'émission dans FactureService.
    @Column(name = "client_prenom", nullable = false, updatable = false, length = 80)
    private String clientPrenom;

    @Column(name = "client_nom", nullable = false, updatable = false, length = 80)
    private String clientNom;

    @Column(name = "client_rue", updatable = false, length = 150)
    private String clientRue;

    @Column(name = "client_numero_rue", updatable = false, length = 15)
    private String clientNumeroRue;

    @Column(name = "client_code_postal", updatable = false, length = 10)
    private String clientCodePostal;

    @Column(name = "client_localite", updatable = false, length = 100)
    private String clientLocalite;

    @Column(name = "client_pays", nullable = false, updatable = false, length = 60)
    private String clientPays;

    @Enumerated(EnumType.STRING)
    @Column(name = "langue_document", nullable = false, updatable = false, length = 2)
    private Langue langueDocument;

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

    protected Facture() {
        // requis par JPA
    }

    /**
     * Émet la facture d'une commande payée. Les montants sont ceux de la commande,
     * recopiés tels quels : l'identité HTVA + TVA = TVAC, déjà garantie par la
     * commande, est revérifiée ici plutôt qu'attendue du CHECK
     * {@code ck_facture_coherence}, le même invariant, défendu aux deux étages.
     *
     * @param tauxTvaApplique le taux unique, ou {@code null} si la facture est multi-taux
     */
    public static Facture pourCommande(String numero, short exercice, int sequenceAnnuelle,
                                       Commande commande, BigDecimal tauxTvaApplique,
                                       Instant dateEmission) {
        Objects.requireNonNull(commande, "commande");
        Facture facture = new Facture();
        facture.reference = UUID.randomUUID();
        facture.numero = Objects.requireNonNull(numero, "numero");
        facture.exercice = exercice;
        facture.sequenceAnnuelle = sequenceAnnuelle;
        facture.commande = commande;
        facture.membre = Objects.requireNonNull(commande.getMembre(), "commande.membre");
        Utilisateur client = facture.membre;
        facture.clientPrenom = client.getPrenom();
        facture.clientNom = client.getNom();
        facture.clientRue = client.getRue();
        facture.clientNumeroRue = client.getNumeroRue();
        facture.clientCodePostal = client.getCodePostal();
        facture.clientLocalite = client.getLocalite();
        facture.clientPays = client.getPays();
        // Langue décidée à la conclusion de la commande, celle de ses lignes : celle du
        // membre si le siège du garage l'admet, sinon la langue par défaut du siège.
        facture.langueDocument = commande.getLangueDocument();
        facture.montantHtva = Objects.requireNonNull(commande.getMontantHtva(), "montantHtva");
        facture.montantTva = Objects.requireNonNull(commande.getMontantTva(), "montantTva");
        facture.montantTvac = Objects.requireNonNull(commande.getMontantTvac(), "montantTvac");
        facture.tauxTvaApplique = tauxTvaApplique;
        facture.dateEmission = Objects.requireNonNull(dateEmission, "dateEmission");
        // Aucune échéance : la facture suit un encaissement déjà réalisé, il n'y a
        // rien à réclamer. La colonne sert aux ventes à terme, hors V1.
        facture.dateEcheance = null;
        if (facture.montantHtva.add(facture.montantTva).compareTo(facture.montantTvac) != 0) {
            throw new IllegalArgumentException(
                    "Montants incoherents : HTVA + TVA doit valoir TVAC.");
        }
        return facture;
    }

    /**
     * Émet la facture d'atelier d'une intervention close (A15, RM-17). Le client est son
     * titulaire, la langue celle de l'intervention (V66). À régler à l'atelier : l'échéance
     * est le jour d'émission au garage, une facture d'atelier ne se vend pas à terme.
     *
     * @param montantHtva     total des bases de la ventilation des lignes facturées
     * @param tauxTvaApplique le taux unique, ou {@code null} si la facture est multi-taux
     */
    public static Facture pourIntervention(String numero, short exercice, int sequenceAnnuelle,
                                           Intervention intervention, Utilisateur titulaire,
                                           BigDecimal montantHtva, BigDecimal montantTva, BigDecimal montantTvac,
                                           BigDecimal tauxTvaApplique, Instant dateEmission,
                                           LocalDate dateEcheance) {
        Facture facture = new Facture();
        facture.reference = UUID.randomUUID();
        facture.numero = Objects.requireNonNull(numero, "numero");
        facture.exercice = exercice;
        facture.sequenceAnnuelle = sequenceAnnuelle;
        facture.intervention = Objects.requireNonNull(intervention, "intervention");
        facture.membre = Objects.requireNonNull(titulaire, "titulaire");
        facture.clientPrenom = titulaire.getPrenom();
        facture.clientNom = titulaire.getNom();
        facture.clientRue = titulaire.getRue();
        facture.clientNumeroRue = titulaire.getNumeroRue();
        facture.clientCodePostal = titulaire.getCodePostal();
        facture.clientLocalite = titulaire.getLocalite();
        facture.clientPays = titulaire.getPays();
        facture.langueDocument = intervention.getLangueDocument();
        facture.montantHtva = Objects.requireNonNull(montantHtva, "montantHtva");
        facture.montantTva = Objects.requireNonNull(montantTva, "montantTva");
        facture.montantTvac = Objects.requireNonNull(montantTvac, "montantTvac");
        facture.tauxTvaApplique = tauxTvaApplique;
        facture.dateEmission = Objects.requireNonNull(dateEmission, "dateEmission");
        facture.dateEcheance = Objects.requireNonNull(dateEcheance, "dateEcheance");
        if (facture.montantHtva.add(facture.montantTva).compareTo(facture.montantTvac) != 0) {
            throw new IllegalArgumentException(
                    "Montants incoherents : HTVA + TVA doit valoir TVAC.");
        }
        return facture;
    }

    /** Facture d'atelier : elle se règle à l'atelier, contrairement à celle d'une commande déjà payée. */
    public boolean estDAtelier() {
        return intervention != null;
    }

    /**
     * Enregistre l'emplacement du PDF archivé. Seule mutation admise après émission :
     * elle ne touche aucune donnée comptable, et le trigger de la base l'autorise
     * pour cette raison précise.
     */
    public void archiverPdf(String chemin) {
        this.cheminPdf = Objects.requireNonNull(chemin, "chemin");
    }

    public boolean estArchivee() {
        return cheminPdf != null;
    }

    /**
     * Nom du client tel qu'il figure sur la facture. Une facture reprise par V61 d'un
     * compte déjà anonymisé porte le marqueur, traduit dans la langue du document.
     */
    public String clientNomComplet(MessageSource messages, Locale langue) {
        if (Utilisateur.PRENOM_ANONYME.equals(clientPrenom) && Utilisateur.NOM_ANONYME.equals(clientNom)) {
            return messages.getMessage(Utilisateur.CLE_MARQUEUR_ANONYME, null, langue);
        }
        return clientPrenom + " " + clientNom;
    }

    public String getClientPrenom() { return clientPrenom; }
    public String getClientNom() { return clientNom; }
    public String getClientRue() { return clientRue; }
    public String getClientNumeroRue() { return clientNumeroRue; }
    public String getClientCodePostal() { return clientCodePostal; }
    public String getClientLocalite() { return clientLocalite; }
    public String getClientPays() { return clientPays; }
    public Langue getLangueDocument() { return langueDocument; }

    public Long getId() { return id; }
    public UUID getReference() { return reference; }
    public String getNumero() { return numero; }
    public short getExercice() { return exercice; }
    public int getSequenceAnnuelle() { return sequenceAnnuelle; }
    public Commande getCommande() { return commande; }
    public Intervention getIntervention() { return intervention; }
    public Utilisateur getMembre() { return membre; }
    public BigDecimal getMontantHtva() { return montantHtva; }
    public BigDecimal getMontantTva() { return montantTva; }
    public BigDecimal getMontantTvac() { return montantTvac; }
    public BigDecimal getTauxTvaApplique() { return tauxTvaApplique; }
    public Instant getDateEmission() { return dateEmission; }
    public LocalDate getDateEcheance() { return dateEcheance; }
    public String getCheminPdf() { return cheminPdf; }

    @Override
    public boolean equals(Object autre) {
        if (this == autre) return true;
        if (!(autre instanceof Facture facture)) return false;
        return id != null && id.equals(facture.id);
    }

    @Override
    public int hashCode() {
        return Facture.class.hashCode();
    }
}
