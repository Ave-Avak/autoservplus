package be.autoservplus.facturation.domain;

import be.autoservplus.identite.domain.Langue;
import be.autoservplus.identite.domain.Utilisateur;
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
 * Facture emise : document comptable legal, immuable des sa creation (F31).
 *
 * <p><b>Pas de suppression logique</b>, contrairement aux entites metier du socle :
 * une facture ne se supprime pas, meme logiquement. La loi impose sa conservation
 * dix ans et sa seule correction legale est la note de credit (table {@code avoir}).
 * Elle n herite donc pas de {@code BaseEntity}, qui porterait {@code deleted_at}.
 * Meme precedent que les journaux append-only du projet.</p>
 *
 * <p><b>Immuabilite</b> defendue en base par le trigger {@code tg_facture_immuable}
 * (V6), qui refuse toute modification du numero, des montants, du taux et de la date
 * d emission. Le mapping le redouble par {@code updatable = false} : la garde de
 * l entite evite l aller-retour vers la base, celle de la base survit a n importe
 * quel appelant. Seul {@code cheminPdf} reste modifiable. Le document est archive
 * apres coup, sans que son contenu comptable bouge.</p>
 *
 * <p><b>Source</b> : une commande OU une intervention, jamais les deux (CHECK
 * {@code ck_facture_origine_unique}). Ce bloc ne livre que la facture de commande.
 * {@code interventionId} est mappe en identifiant brut plutot qu en association pour
 * que la colonne existe des maintenant sans faire dependre la facturation du module
 * intervention. Le bloc RM-17 futur promouvra l association s il en a besoin.</p>
 *
 * <p><b>Montants figes</b> : recopies de la commande a l emission, jamais relus du
 * catalogue. Une modification de prix posterieure ne reecrit donc aucune facture,
 * comme les lignes figees ne reecrivent aucune commande.</p>
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

    @Column(name = "intervention_id", updatable = false)
    private Long interventionId;

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

    /** Taux unique de la facture, {@code null} si elle en melange plusieurs (V26). */
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

    protected Facture() {
        // requis par JPA
    }

    /**
     * Emet la facture d une commande payee. Les montants sont ceux de la commande,
     * recopies tels quels : l identite HTVA + TVA = TVAC, deja garantie par la
     * commande, est reverifiee ici plutot qu attendue du CHECK
     * {@code ck_facture_coherence}, le meme invariant, defendu aux deux etages.
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
        // Aucune echeance : la facture suit un encaissement deja realise, il n y a
        // rien a reclamer. La colonne sert aux ventes a terme, hors V1.
        facture.dateEcheance = null;
        if (facture.montantHtva.add(facture.montantTva).compareTo(facture.montantTvac) != 0) {
            throw new IllegalArgumentException(
                    "Montants incoherents : HTVA + TVA doit valoir TVAC.");
        }
        return facture;
    }

    /**
     * Enregistre l emplacement du PDF archive. Seule mutation admise apres emission :
     * elle ne touche aucune donnee comptable, et le trigger de la base l autorise
     * pour cette raison precise.
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
    public Long getInterventionId() { return interventionId; }
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
