package be.autoservplus.vente.domain;

import jakarta.persistence.*;
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
 * Tentative de paiement d une commande aupres du prestataire (F14).
 *
 * <p>La machine a etats est portee par l entite ({@link StatutPaiement#peutPasserA}) ;
 * {@code @Version} (V24) protege la course entre le webhook et le job d expiration.
 * La cle d idempotence, generee a la creation et unique en base, accompagne l appel
 * au prestataire pour qu une requete rejouee ne debite pas deux fois.</p>
 *
 * <p>Un echec ou une expiration est terminal : re-essayer, c est creer un NOUVEAU
 * paiement pour la meme commande. Pas de soft delete en base.</p>
 */
@Entity
@Table(name = "paiement")
@EntityListeners(AuditingEntityListener.class)
public class Paiement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference", nullable = false, updatable = false)
    private UUID reference;

    // commande_id est nullable en base (un paiement pourra couvrir une reservation
    // de parking) ; dans ce bloc, le constructeur la pose toujours.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commande_id")
    private Commande commande;

    @Column(name = "reference_mollie", length = 64)
    private String referenceMollie;

    /**
     * Identifiant du Refund chez le prestataire (V27), contrepartie de
     * {@link #referenceMollie}. Seul point de rapprochement avec l extrait du
     * prestataire si le membre conteste n avoir jamais ete rembourse.
     */
    @Column(name = "reference_remboursement", length = 64)
    private String referenceRemboursement;

    @NotNull
    @Column(name = "cle_idempotence", nullable = false, updatable = false, length = 64)
    private String cleIdempotence;

    @NotNull
    @Column(name = "montant", nullable = false, precision = 10, scale = 2)
    private BigDecimal montant;

    @NotNull
    @Column(name = "devise", nullable = false, length = 3)
    private String devise = "EUR";

    /**
     * Moyen effectivement employe (Bancontact, carte, virement…), tel que le
     * prestataire le rapporte. Affiche au detail d une commande (F32, CdC P384).
     *
     * <p><b>Ecrit a la relecture, jamais a la creation</b> : le moyen n est pas choisi
     * par AutoServ+ mais constate chez le prestataire, qui ne le connait qu une fois le
     * client passe par sa page. La colonne reste donc nulle a l insertion et se
     * renseigne quand la relecture du statut le rapporte.</p>
     *
     * <p><b>Correction d une promesse non tenue.</b> Le champ etait mappe
     * {@code insertable = false, updatable = false} en annoncant qu il « se remplirait
     * de lui-meme le jour ou MollieGateway serait cable ». C etait faux : ainsi mappe,
     * Hibernate ne l ecrit jamais, et l ecran aurait continue d afficher « moyen non
     * communique » avec un prestataire reel branche. Un commentaire faux coute plus
     * cher qu un commentaire absent — il dispense de verifier.</p>
     *
     * <p>Un prestataire bouchonne n en rapporte aucun : la valeur reste nulle et
     * l ecran le dit, plutot que d inventer un moyen.</p>
     */
    @Column(name = "methode", length = 30)
    private String methode;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 25)
    private StatutPaiement statut = StatutPaiement.INITIE;

    @Column(name = "date_initiation", nullable = false, updatable = false)
    private Instant dateInitiation;

    @Column(name = "date_finalisation")
    private Instant dateFinalisation;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

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

    protected Paiement() {
        // requis par JPA
    }

    public Paiement(Commande commande, BigDecimal montant, Instant dateInitiation) {
        this.reference = UUID.randomUUID();
        this.commande = Objects.requireNonNull(commande, "commande");
        this.montant = Objects.requireNonNull(montant, "montant");
        this.dateInitiation = Objects.requireNonNull(dateInitiation, "dateInitiation");
        this.cleIdempotence = UUID.randomUUID().toString();
    }

    /**
     * Reference attribuee par le prestataire, posee UNE fois a l initiation.
     * Elle identifie le paiement dans les notifications entrantes ; la reecrire
     * casserait ce lien.
     */
    public void enregistrerReferencePrestataire(String referencePrestataire) {
        if (this.referenceMollie != null) {
            throw new IllegalStateException(
                    "La reference prestataire est deja posee : " + this.referenceMollie);
        }
        this.referenceMollie = Objects.requireNonNull(referencePrestataire, "referencePrestataire");
    }

    // --- transitions -----------------------------------------------------------------

    public void mettreEnCours() {
        transitionVers(StatutPaiement.EN_COURS);
    }

    /** Paiement encaisse : irreversible, le remboursement sera un Refund distinct. */
    public void confirmer(Instant maintenant) {
        transitionVers(StatutPaiement.REUSSI);
        this.dateFinalisation = Objects.requireNonNull(maintenant, "maintenant");
    }

    public void echouer(Instant maintenant) {
        transitionVers(StatutPaiement.ECHOUE);
        this.dateFinalisation = Objects.requireNonNull(maintenant, "maintenant");
    }

    public void expirer(Instant maintenant) {
        transitionVers(StatutPaiement.EXPIRE);
        this.dateFinalisation = Objects.requireNonNull(maintenant, "maintenant");
    }

    /**
     * Le prestataire a accepte le Refund (F30) : l encaissement est contre-passe.
     *
     * <p>{@code dateFinalisation} n est PAS reecrite : elle date l encaissement, et
     * c est cette date-la que porte la facture immuable. La date du remboursement
     * vit sur la demande d annulation qui l a declenche ({@code decide_le}) et sur
     * la note de credit ({@code date_emission}) — trois evenements distincts, trois
     * horodatages distincts, aucun ecrase par le suivant.</p>
     *
     * @param referencePrestataire identifiant du Refund, pour le rapprochement
     */
    public void rembourser(String referencePrestataire) {
        transitionVers(StatutPaiement.REMBOURSE);
        this.referenceRemboursement =
                Objects.requireNonNull(referencePrestataire, "referencePrestataire");
    }

    /**
     * Enregistre le moyen rapporte par le prestataire, une seule fois.
     *
     * <p>Ne reecrit jamais une valeur deja posee, et ignore une valeur vide : la
     * relecture du statut est rejouee a chaque notification et a chaque retour du
     * membre. Sans cette garde, un prestataire qui cesserait de rapporter le moyen
     * effacerait une donnee que la facture emise a peut-etre deja opposee.</p>
     */
    public void enregistrerMethode(String methodeRapportee) {
        if (this.methode == null && methodeRapportee != null && !methodeRapportee.isBlank()) {
            this.methode = methodeRapportee;
        }
    }

    /**
     * Cle d idempotence du remboursement, <b>derivee</b> de la reference du paiement
     * et donc stable : deux appels pour le meme paiement portent la meme cle, et le
     * prestataire ne rembourse qu une fois meme si la requete est rejouee. Une cle
     * tiree au hasard a chaque appel offrirait exactement la garantie inverse.
     * Distincte de {@link #cleIdempotence}, qui couvre l encaissement : ce sont deux
     * operations differentes chez le prestataire.
     */
    public String cleIdempotenceRemboursement() {
        return "remboursement-" + reference;
    }

    private void transitionVers(StatutPaiement cible) {
        if (!statut.peutPasserA(cible)) {
            throw new IllegalStateException(
                    "Transition de paiement interdite : %s vers %s.".formatted(statut, cible));
        }
        this.statut = cible;
    }

    public boolean estTermine() {
        return statut == StatutPaiement.REUSSI || statut == StatutPaiement.ECHOUE
                || statut == StatutPaiement.EXPIRE || statut == StatutPaiement.REMBOURSE;
    }

    public Long getId() { return id; }
    public UUID getReference() { return reference; }
    public Commande getCommande() { return commande; }
    public String getReferenceMollie() { return referenceMollie; }
    public String getReferenceRemboursement() { return referenceRemboursement; }
    public String getCleIdempotence() { return cleIdempotence; }
    public BigDecimal getMontant() { return montant; }
    public String getDevise() { return devise; }
    public String getMethode() { return methode; }
    public StatutPaiement getStatut() { return statut; }
    public Instant getDateInitiation() { return dateInitiation; }
    public Instant getDateFinalisation() { return dateFinalisation; }
    public long getVersion() { return version; }

    @Override
    public boolean equals(Object autre) {
        if (this == autre) return true;
        if (!(autre instanceof Paiement paiement)) return false;
        return id != null && id.equals(paiement.id);
    }

    @Override
    public int hashCode() {
        return Paiement.class.hashCode();
    }
}
