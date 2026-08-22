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
 * <p>Colonnes non mappees dans ce bloc : {@code date_paiement} (nullable, bloc
 * paiement a venir). {@code motif_annulation} et {@code date_annulation} du
 * dictionnaire n existent pas en base — ecart consigne, a lever avec le bloc
 * paiement/annulation. Pas de collection de lignes cote commande : aucun ecran
 * n en a besoin ici, la facture la mappera le moment venu.</p>
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

    protected Commande() {
        // requis par JPA
    }

    /**
     * Cree la commande EN_ATTENTE_PAIEMENT (RM-19). Les trois montants arrivent
     * deja sommes ligne a ligne par le panier ; l entite refuse un triplet
     * incoherent plutot que d attendre le CHECK de la base — le meme invariant,
     * defendu aux deux etages.
     */
    public Commande(String numero, Utilisateur membre,
                    BigDecimal montantHtva, BigDecimal montantTva, BigDecimal montantTvac,
                    Instant dateCommande) {
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

    public Long getId() { return id; }
    public UUID getReference() { return reference; }
    public String getNumero() { return numero; }
    public Utilisateur getMembre() { return membre; }
    public StatutCommande getStatut() { return statut; }
    public BigDecimal getMontantHtva() { return montantHtva; }
    public BigDecimal getMontantTva() { return montantTva; }
    public BigDecimal getMontantTvac() { return montantTvac; }
    public Instant getDateCommande() { return dateCommande; }

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
