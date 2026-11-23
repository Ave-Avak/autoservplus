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
 * Tentative de paiement d'une commande auprès du prestataire (F14).
 *
 * <p>La machine à états est portée par l'entité ({@link StatutPaiement#peutPasserA}).
 * {@code @Version} (V24) protège la course entre le webhook et le job d'expiration.
 * La clé d'idempotence, générée à la création et unique en base, accompagne l'appel
 * au prestataire pour qu'une requête rejouée ne débite pas deux fois.</p>
 *
 * <p>Un échec ou une expiration est terminal : re-essayer, c'est créer un NOUVEAU
 * paiement pour la même commande. Pas de soft delete en base.</p>
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

    // commande_id est nullable en base (un paiement pourra couvrir une réservation
    // de parking). Dans ce bloc, le constructeur la pose toujours.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commande_id")
    private Commande commande;

    @Column(name = "reference_mollie", length = 64)
    private String referenceMollie;

    /**
     * Identifiant du Refund chez le prestataire (V27), contrepartie de
     * {@link #referenceMollie}. Seul point de rapprochement avec l'extrait du
     * prestataire si le membre conteste n'avoir jamais été remboursé.
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
     * Moyen effectivement employé (Bancontact, carte, virement...), tel que le
     * prestataire le rapporte. Affiché au détail d'une commande (F32, CdC P384).
     *
     * <p><b>Écrit à la relecture, jamais à la création</b> : le moyen n'est pas choisi
     * par AutoServ+ mais constaté chez le prestataire, qui ne le connaît qu'une fois le
     * client passé par sa page. La colonne reste donc nulle à l'insertion et se
     * renseigne quand la relecture du statut le rapporte.</p>
     *
     * <p><b>Correction d'une promesse non tenue.</b> Le champ était mappé
     * {@code insertable = false, updatable = false} en annonçant qu'il « se remplirait
     * de lui-même le jour où MollieGateway serait câblé ». C'était faux : ainsi mappé,
     * Hibernate ne l'écrit jamais, et l'écran aurait continué d'afficher « moyen non
     * communiqué » avec un prestataire réel branché. Un commentaire faux coûte plus
     * cher qu'un commentaire absent, car il dispense de vérifier.</p>
     *
     * <p>Un prestataire bouchonné n'en rapporte aucun : la valeur reste nulle et
     * l'écran le dit, plutôt que d'inventer un moyen.</p>
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

    /**
     * Instant où l'encaissement a été constaté sur une commande déjà annulée (V64), nul
     * pour un paiement ordinaire. Un tel paiement n'a ni commande à honorer ni facture :
     * il attend qu'un administrateur le rembourse.
     */
    @Column(name = "recu_apres_annulation_le")
    private Instant recuApresAnnulationLe;

    /** Date du remboursement d'un paiement reçu après annulation (V64). */
    @Column(name = "rembourse_le")
    private Instant rembourseLe;

    /** Administrateur qui a remboursé un paiement reçu après annulation (V64). */
    @Column(name = "rembourse_par", length = 120)
    private String remboursePar;

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
     * Référence attribuée par le prestataire, posée UNE fois à l'initiation.
     * Elle identifie le paiement dans les notifications entrantes. La réécrire
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

    /** Paiement encaissé : irréversible, le remboursement sera un Refund distinct. */
    public void confirmer(Instant maintenant) {
        transitionVers(StatutPaiement.REUSSI);
        this.dateFinalisation = Objects.requireNonNull(maintenant, "maintenant");
    }

    /**
     * Le prestataire rapporte un encaissement alors que la commande est déjà annulée.
     *
     * <p>Seule transition qui rouvre une tentative close : ECHOUE et EXPIRE sont
     * terminaux partout ailleurs. Une tentative que le job RM-21 a expirée de notre
     * côté, avant de lire le statut chez le prestataire, a pu aboutir chez lui. Refuser
     * la transition laisserait un encaissement réel sans aucune trace en base. La
     * commande, elle, ne ressuscite pas.</p>
     */
    public void constaterEncaissementApresAnnulation(Instant maintenant) {
        if (statut == StatutPaiement.REUSSI || statut == StatutPaiement.REMBOURSE) {
            throw new IllegalStateException(
                    "Encaissement déjà constaté pour le paiement %s.".formatted(reference));
        }
        this.statut = StatutPaiement.REUSSI;
        this.dateFinalisation = Objects.requireNonNull(maintenant, "maintenant");
        this.recuApresAnnulationLe = maintenant;
    }

    /**
     * Un administrateur rembourse un paiement reçu après annulation. Sans facture, donc
     * sans note de crédit : l'auteur et la date posés ici sont la trace du geste.
     */
    public void rembourserApresAnnulation(String referencePrestataire, Instant maintenant,
                                          String auteur) {
        if (recuApresAnnulationLe == null) {
            throw new IllegalStateException(
                    "Le paiement %s n'a pas été reçu après une annulation.".formatted(reference));
        }
        rembourser(referencePrestataire);
        this.rembourseLe = Objects.requireNonNull(maintenant, "maintenant");
        this.remboursePar = Objects.requireNonNull(auteur, "auteur");
    }

    public boolean estRecuApresAnnulation() {
        return recuApresAnnulationLe != null;
    }

    /** Reçu après annulation et pas encore remboursé. */
    public boolean estARembourser() {
        return recuApresAnnulationLe != null && statut == StatutPaiement.REUSSI;
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
     * Le prestataire a accepté le Refund (F30) : l'encaissement est contre-passé.
     *
     * <p>{@code dateFinalisation} n'est PAS réécrite : elle date l'encaissement, et
     * c'est cette date-là que porte la facture immuable. La date du remboursement
     * vit sur la demande d'annulation qui l'a déclenché ({@code decide_le}) et sur
     * la note de crédit ({@code date_emission}) : trois événements distincts, trois
     * horodatages distincts, aucun écrasé par le suivant.</p>
     *
     * @param referencePrestataire identifiant du Refund, pour le rapprochement
     */
    public void rembourser(String referencePrestataire) {
        transitionVers(StatutPaiement.REMBOURSE);
        this.referenceRemboursement =
                Objects.requireNonNull(referencePrestataire, "referencePrestataire");
    }

    /**
     * Enregistre le moyen rapporté par le prestataire, une seule fois.
     *
     * <p>Ne réécrit jamais une valeur déjà posée, et ignore une valeur vide : la
     * relecture du statut est rejouée à chaque notification et à chaque retour du
     * membre. Sans cette garde, un prestataire qui cesserait de rapporter le moyen
     * effacerait une donnée que la facture émise a peut-être déjà opposée.</p>
     */
    public void enregistrerMethode(String methodeRapportee) {
        if (this.methode == null && methodeRapportee != null && !methodeRapportee.isBlank()) {
            this.methode = methodeRapportee;
        }
    }

    /**
     * Clé d'idempotence du remboursement, <b>dérivée</b> de la référence du paiement
     * et donc stable : deux appels pour le même paiement portent la même clé, et le
     * prestataire ne rembourse qu'une fois même si la requête est rejouée. Une clé
     * tirée au hasard à chaque appel offrirait exactement la garantie inverse.
     * Distincte de {@link #cleIdempotence}, qui couvre l'encaissement : ce sont deux
     * opérations différentes chez le prestataire.
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
    public Instant getRecuApresAnnulationLe() { return recuApresAnnulationLe; }
    public Instant getRembourseLe() { return rembourseLe; }
    public String getRemboursePar() { return remboursePar; }

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
