package be.autoservplus.vente.domain;

import be.autoservplus.common.entity.BaseEntity;
import be.autoservplus.identite.domain.Utilisateur;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Commande d un membre, nee de la conversion de son panier (F14, RM-19).
 *
 * <p>Les montants HTVA / TVA / TVAC sont <b>figes a la creation</b>, calcules des
 * valeurs figees des lignes (jamais du catalogue courant) et sommes ligne a ligne
 * (RM-30). L entite verifie l identite comptable HTVA + TVA = TVAC que la base
 * redouble par le CHECK {@code ck_commande_coherence}.</p>
 *
 * <p>Les lignes ne sont pas recopiees : la meme ligne passe de {@code panier_id}
 * a {@code commande_id} via {@link #reprendreLignes} (reaffectation prevue au
 * dictionnaire, CHECK {@code ck_ligne_rattachement_unique}).</p>
 *
 * <p>Machine a etats ({@link StatutCommande#peutPasserA}) : PAYEE par le webhook
 * confirme, ANNULEE par le timeout RM-21, REMBOURSEE par la retractation (F30).
 * Une PAYEE ne redevient jamais ANNULEE et inversement — c est la garde qui
 * tranche la course entre le job d expiration et un webhook tardif. Pas de
 * collection de lignes cote commande : la facture la mappera le moment venu.</p>
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
     * Regle (a) du paiement : le stock est devenu insuffisant entre la conversion et
     * le paiement confirme. La commande est payee malgre tout (on n annule pas un
     * paiement encaisse), le garage honore la rupture hors ligne — le detail des
     * lignes concernees est journalise a la detection.
     */
    @Column(name = "rupture_a_honorer", nullable = false)
    private boolean ruptureAHonorer;

    /**
     * Le client a renonce a son droit de retractation pour execution immediate du
     * service (art. VI.53 CDE, F12).
     *
     * <p><b>C est l ETAT sur lequel F30 decide</b>, jamais la preuve : celle-ci est
     * une ligne append-only de {@code consentement}, type
     * {@code RENONCIATION_RETRACTATION}, ecrite dans la meme transaction. La table
     * {@code consentement} n a aucune FK vers la commande — rapprocher les deux par
     * horodatage serait faux des qu un membre commande deux fois dans la minute.</p>
     *
     * <p><b>Pose a la creation, jamais modifie ensuite</b> ({@code updatable = false}) :
     * une renonciation est un fait daté, et la revoir apres coup changerait
     * retroactivement les droits du client sur une commande deja conclue.</p>
     *
     * <p>{@code false} pour toute commande de pieces et toute commande anterieure a
     * F12 — le droit de retractation s applique alors pleinement, defaut le plus
     * protecteur et le seul qui se lise sans ambiguite.</p>
     */
    @Column(name = "renonciation_vi53", nullable = false, updatable = false)
    private boolean renonciationVi53;

    protected Commande() {
        // requis par JPA
    }

    /**
     * Cree la commande EN_ATTENTE_PAIEMENT (RM-19). Les trois montants arrivent
     * deja sommes ligne a ligne par le panier ; l entite refuse un triplet
     * incoherent plutot que d attendre le CHECK de la base — le meme invariant,
     * defendu aux deux etages.
     */
    /**
     * Commande de pieces, ou commande anterieure a F12 : aucune renonciation.
     * Surcharge de commodite qui evite de repeter {@code false} partout.
     */
    public Commande(String numero, Utilisateur membre,
                    BigDecimal montantHtva, BigDecimal montantTva, BigDecimal montantTvac,
                    Instant dateCommande) {
        this(numero, membre, montantHtva, montantTva, montantTvac, dateCommande, false);
    }

    /**
     * @param renonciationVi53 le client a renoncé a son droit de retractation pour
     *                         execution immediate du service (F12). Pose ICI et
     *                         jamais ensuite : une renonciation est un fait date.
     */
    public Commande(String numero, Utilisateur membre,
                    BigDecimal montantHtva, BigDecimal montantTva, BigDecimal montantTvac,
                    Instant dateCommande, boolean renonciationVi53) {
        this.renonciationVi53 = renonciationVi53;
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
     * Deplace les lignes du panier vers cette commande : meme ligne, memes valeurs
     * figees, seul le rattachement change ({@code panier_id} vers {@code commande_id}).
     * La commande doit deja etre persistee — une ligne ne peut pas pointer un id
     * inexistant.
     */
    public void reprendreLignes(List<LignePanier> lignes) {
        Objects.requireNonNull(lignes, "lignes");
        for (LignePanier ligne : lignes) {
            ligne.rattacherA(this);
        }
    }

    // --- transitions -----------------------------------------------------------------

    /** Le prestataire a confirme l encaissement : la commande est PAYEE (RM-22 suivra). */
    public void confirmerPaiement(Instant maintenant) {
        transitionVers(StatutCommande.PAYEE);
        this.datePaiement = Objects.requireNonNull(maintenant, "maintenant");
    }

    /** Annulation avec motif obligatoire (CHECK ck_commande_annulation en base). */
    public void annuler(MotifAnnulationCommande motif, Instant maintenant) {
        transitionVers(StatutCommande.ANNULEE);
        this.motifAnnulation = Objects.requireNonNull(motif, "motif");
        this.dateAnnulation = Objects.requireNonNull(maintenant, "maintenant");
    }

    /**
     * L administrateur a valide la retractation : la commande est REMBOURSEE (F30).
     *
     * <p>REMBOURSEE et non ANNULEE, alors que le resultat pour le membre se ressemble :
     * une commande annulee ne l a jamais ete que faute de paiement (RM-21), une
     * commande remboursee a ete encaissee, facturee, puis contre-passee par une note
     * de credit. Les confondre effacerait cette difference dans les livres comme dans
     * les statistiques du garage.</p>
     *
     * <p>Motif et date d annulation sont renseignes bien que le CHECK
     * {@code ck_commande_annulation} ne les exige que pour ANNULEE : la colonne existe,
     * le motif est connu, et une commande remboursee sans trace de la raison serait
     * illisible six mois plus tard. V24 l avait explicitement prevu.</p>
     */
    public void rembourser(Instant maintenant) {
        transitionVers(StatutCommande.REMBOURSEE);
        this.motifAnnulation = MotifAnnulationCommande.RETRACTATION_F30;
        this.dateAnnulation = Objects.requireNonNull(maintenant, "maintenant");
    }

    /** Regle (a) : rupture constatee au paiement, a honorer hors ligne par le garage. */
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
