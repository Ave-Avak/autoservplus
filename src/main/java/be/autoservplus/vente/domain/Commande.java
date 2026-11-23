package be.autoservplus.vente.domain;

import be.autoservplus.common.entity.BaseEntity;
import be.autoservplus.common.exception.RefusTraduit;
import be.autoservplus.identite.domain.Langue;
import be.autoservplus.identite.domain.Utilisateur;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Commande d'un membre, née de la conversion de son panier (F14, RM-19).
 *
 * <p>Les montants HTVA / TVA / TVAC sont <b>figés à la création</b>, calculés des
 * valeurs figées des lignes (jamais du catalogue courant) et sommés ligne à ligne
 * (RM-30). L'entité vérifie l'identité comptable HTVA + TVA = TVAC que la base
 * redouble par le CHECK {@code ck_commande_coherence}.</p>
 *
 * <p>Les lignes ne sont pas recopiées : la même ligne passe de {@code panier_id}
 * à {@code commande_id} via {@link #reprendreLignes} (réaffectation prévue au
 * dictionnaire, CHECK {@code ck_ligne_rattachement_unique}).</p>
 *
 * <p>Machine à états ({@link StatutCommande#peutPasserA}) : PAYEE par le webhook
 * confirmé, ANNULEE par le timeout RM-21, REMBOURSEE par la rétractation (F30).
 * Une PAYEE ne redevient jamais ANNULEE et inversement : c'est la garde qui
 * tranche la course entre le job d'expiration et un webhook tardif. Pas de
 * collection de lignes côté commande : la facture la mappera le moment venu.</p>
 */
@Entity
@Table(name = "commande")
@SQLRestriction("deleted_at IS NULL")
public class Commande extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference", nullable = false, updatable = false)
    private UUID reference;

    @Column(name = "numero", nullable = false, updatable = false, length = 20)
    private String numero;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "membre_id", nullable = false)
    private Utilisateur membre;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 25)
    private StatutCommande statut = StatutCommande.EN_ATTENTE_PAIEMENT;

    @Column(name = "montant_htva", nullable = false, precision = 10, scale = 2)
    private BigDecimal montantHtva;

    @Column(name = "montant_tva", nullable = false, precision = 10, scale = 2)
    private BigDecimal montantTva;

    @Column(name = "montant_tvac", nullable = false, precision = 10, scale = 2)
    private BigDecimal montantTvac;

    @Column(name = "date_commande", nullable = false, updatable = false)
    private Instant dateCommande;

    @Column(name = "date_paiement")
    private Instant datePaiement;

    @Enumerated(EnumType.STRING)
    @Column(name = "motif_annulation", length = 30)
    private MotifAnnulationCommande motifAnnulation;

    @Column(name = "date_annulation")
    private Instant dateAnnulation;

    /**
     * Règle (a) du paiement : le stock est devenu insuffisant entre la conversion et
     * le paiement confirmé. La commande est payée malgré tout (on n'annule pas un
     * paiement encaissé), le garage honore la rupture hors ligne : le détail des
     * lignes concernées est journalisé à la détection.
     */
    @Column(name = "rupture_a_honorer", nullable = false)
    private boolean ruptureAHonorer;

    /**
     * Le client a renoncé à son droit de rétractation pour exécution immédiate du
     * service (art. VI.53 CDE, F12).
     *
     * <p><b>C'est l'ETAT sur lequel F30 décide</b>, jamais la preuve : celle-ci est
     * une ligne append-only de {@code consentement}, type
     * {@code RENONCIATION_RETRACTATION}, écrite dans la même transaction. La table
     * {@code consentement} n'a aucune FK vers la commande, rapprocher les deux par
     * horodatage serait faux dès qu'un membre commande deux fois dans la minute.</p>
     *
     * <p><b>Posé à la création, jamais modifié ensuite</b> ({@code updatable = false}) :
     * une renonciation est un fait daté, et la revoir après coup changerait
     * rétroactivement les droits du client sur une commande déjà conclue.</p>
     *
     * <p>{@code false} pour toute commande de pièces et toute commande antérieure à
     * F12. Le droit de rétractation s'applique alors pleinement, défaut le plus
     * protecteur et le seul qui se lise sans ambiguïté.</p>
     */
    @Column(name = "renonciation_vi53", nullable = false, updatable = false)
    private boolean renonciationVi53;

    /** Jour de remise des pièces au membre, point de départ du délai de rétractation (V59). */
    @Column(name = "date_remise")
    private LocalDate dateRemise;

    /** Langue des documents de la commande et des libellés de ses lignes, fixée à sa création. */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "langue_document", nullable = false, updatable = false, length = 2)
    private Langue langueDocument = Langue.fr;

    protected Commande() {
        // requis par JPA
    }

    /**
     * Crée la commande EN_ATTENTE_PAIEMENT (RM-19). Les trois montants arrivent
     * déjà sommés ligne à ligne par le panier. L'entité refuse un triplet
     * incohérent plutôt que d'attendre le CHECK de la base : le même invariant,
     * défendu aux deux étages.
     */
    /**
     * Commande de pièces, ou commande antérieure à F12 : aucune renonciation.
     * Surcharge de commodité qui évite de répéter {@code false} partout.
     */
    public Commande(String numero, Utilisateur membre,
                    BigDecimal montantHtva, BigDecimal montantTva, BigDecimal montantTvac,
                    Instant dateCommande) {
        this(numero, membre, montantHtva, montantTva, montantTvac, dateCommande, false);
    }

    /**
     * @param renonciationVi53 le client a renoncé à son droit de rétractation pour
     *                         exécution immédiate du service (F12). Posé ICI et
     *                         jamais ensuite : une renonciation est un fait daté.
     */
    public Commande(String numero, Utilisateur membre,
                    BigDecimal montantHtva, BigDecimal montantTva, BigDecimal montantTvac,
                    Instant dateCommande, boolean renonciationVi53) {
        this(numero, membre, montantHtva, montantTva, montantTvac, dateCommande, renonciationVi53, Langue.fr);
    }

    /**
     * @param langueDocument langue de la facture et de l'avoir de cette commande, et des
     *                       libellés figés sur ses lignes. Décidée ici, une fois, par
     *                       l'appelant qui connaît le siège du garage
     *                       ({@code IdentiteGarage.langueDuDocument}) : les lignes d'une
     *                       facture doivent être dans la langue de la facture, et la
     *                       facture ne naît qu'au paiement.
     */
    public Commande(String numero, Utilisateur membre,
                    BigDecimal montantHtva, BigDecimal montantTva, BigDecimal montantTvac,
                    Instant dateCommande, boolean renonciationVi53, Langue langueDocument) {
        this.renonciationVi53 = renonciationVi53;
        this.langueDocument = Objects.requireNonNull(langueDocument, "langueDocument");
        this.reference = UUID.randomUUID();
        this.numero = Objects.requireNonNull(numero, "numero");
        this.membre = Objects.requireNonNull(membre, "membre");
        this.montantHtva = Objects.requireNonNull(montantHtva, "montantHtva");
        this.montantTva = Objects.requireNonNull(montantTva, "montantTva");
        this.montantTvac = Objects.requireNonNull(montantTvac, "montantTvac");
        this.dateCommande = Objects.requireNonNull(dateCommande, "dateCommande");
        if (montantHtva.add(montantTva).compareTo(montantTvac) != 0) {
            throw new IllegalArgumentException(
                    "Montants incoherents : HTVA + TVA doit valoir TVAC (RM-30).");
        }
    }

    /**
     * Déplace les lignes du panier vers cette commande : même ligne, mêmes valeurs
     * figées, seul le rattachement change ({@code panier_id} vers {@code commande_id}).
     * La commande doit déjà être persistée : une ligne ne peut pas pointer un id
     * inexistant.
     */
    public void reprendreLignes(List<LignePanier> lignes) {
        // Chaque ligne refige son libellé dans la langue de cette commande : voir rattacherA.
        Objects.requireNonNull(lignes, "lignes");
        for (LignePanier ligne : lignes) {
            ligne.rattacherA(this);
        }
    }

    // --- transitions -----------------------------------------------------------------

    /** Le prestataire a confirmé l'encaissement : la commande est PAYEE (RM-22 suivra). */
    public void confirmerPaiement(Instant maintenant) {
        transitionVers(StatutCommande.PAYEE);
        this.datePaiement = Objects.requireNonNull(maintenant, "maintenant");
    }

    /**
     * L'échéance de paiement est passée (RM-21). La commande peut encore attendre le
     * statut définitif d'une tentative ouverte, mais plus en recevoir une nouvelle.
     */
    public boolean delaiDePaiementDepasse(Instant maintenant, java.time.Duration delai) {
        return !maintenant.isBefore(dateCommande.plus(delai));
    }

    /** Annulation avec motif obligatoire (CHECK ck_commande_annulation en base). */
    public void annuler(MotifAnnulationCommande motif, Instant maintenant) {
        transitionVers(StatutCommande.ANNULEE);
        this.motifAnnulation = Objects.requireNonNull(motif, "motif");
        this.dateAnnulation = Objects.requireNonNull(maintenant, "maintenant");
    }

    /**
     * L'administrateur a validé la rétractation : la commande est REMBOURSEE (F30).
     *
     * <p>REMBOURSEE et non ANNULEE, alors que le résultat pour le membre se ressemble :
     * une commande annulée ne l'a jamais été que faute de paiement (RM-21), une
     * commande remboursée a été encaissée, facturée, puis contre-passée par une note
     * de crédit. Les confondre effacerait cette différence dans les livres comme dans
     * les statistiques du garage.</p>
     *
     * <p>Motif et date d'annulation sont renseignés bien que le CHECK
     * {@code ck_commande_annulation} ne les exige que pour ANNULEE : la colonne existe,
     * le motif est connu, et une commande remboursée sans trace de la raison serait
     * illisible six mois plus tard. V24 l'avait explicitement prévu.</p>
     */
    public void rembourser(Instant maintenant) {
        transitionVers(StatutCommande.REMBOURSEE);
        this.motifAnnulation = MotifAnnulationCommande.RETRACTATION_F30;
        this.dateAnnulation = Objects.requireNonNull(maintenant, "maintenant");
    }

    /** Règle (a) : rupture constatée au paiement, à honorer hors ligne par le garage. */
    public void signalerRupture() {
        this.ruptureAHonorer = true;
    }

    private void transitionVers(StatutCommande cible) {
        if (!statut.peutPasserA(cible)) {
            throw new IllegalStateException(
                    "Transition de commande interdite : %s vers %s.".formatted(statut, cible));
        }
        this.statut = cible;
    }

    public Long getId() { return id; }
    public UUID getReference() { return reference; }
    public String getNumero() { return numero; }
    public Utilisateur getMembre() { return membre; }
    public StatutCommande getStatut() { return statut; }
    public BigDecimal getMontantHtva() { return montantHtva; }
    public BigDecimal getMontantTva() { return montantTva; }
    public BigDecimal getMontantTvac() { return montantTvac; }
    public Instant getDateCommande() { return dateCommande; }
    public Instant getDatePaiement() { return datePaiement; }
    public MotifAnnulationCommande getMotifAnnulation() { return motifAnnulation; }
    public Instant getDateAnnulation() { return dateAnnulation; }
    /** Voir le champ : ETAT lu par F30, la preuve est dans consentement. */
    public boolean isRenonciationVi53() { return renonciationVi53; }
    public Langue getLangueDocument() { return langueDocument; }
    public LocalDate getDateRemise() { return dateRemise; }

    /**
     * Enregistre le jour où le membre a reçu ses pièces (CDE, art. VI.47, § 2, 2°).
     *
     * <p>La date se corrige tant que la commande est payée : une erreur de saisie se
     * rectifie, l'auteur et l'instant de la dernière saisie restent dans les colonnes
     * d'audit. Elle ne peut ni précéder le jour du paiement, la pièce n'étant remise
     * qu'une fois réglée, ni dépasser aujourd'hui.</p>
     *
     * @param aujourdhui  le jour courant de l'atelier
     * @param jourPaiement le jour du paiement, à l'heure de l'atelier
     * @throws RefusTraduit commande non payée, date future ou antérieure au paiement
     */
    public void enregistrerRemise(LocalDate jour, LocalDate aujourdhui, LocalDate jourPaiement) {
        Objects.requireNonNull(jour, "jour");
        if (statut != StatutCommande.PAYEE || datePaiement == null) {
            throw new RefusTraduit("admin.ventes.remise.erreur.non-payee",
                    "Seule une commande payée reçoit une date de remise.");
        }
        if (jour.isAfter(aujourdhui)) {
            throw new RefusTraduit("admin.ventes.remise.erreur.future",
                    "La date de remise ne peut pas être future.");
        }
        if (jour.isBefore(jourPaiement)) {
            throw new RefusTraduit("admin.ventes.remise.erreur.avant-paiement",
                    "La date de remise ne peut pas précéder le paiement.");
        }
        this.dateRemise = jour;
    }

    public boolean isRuptureAHonorer() { return ruptureAHonorer; }

    @Override
    public boolean equals(Object autre) {
        if (this == autre) return true;
        if (!(autre instanceof Commande commande)) return false;
        return id != null && id.equals(commande.id);
    }

    @Override
    public int hashCode() {
        return Commande.class.hashCode();
    }
}
