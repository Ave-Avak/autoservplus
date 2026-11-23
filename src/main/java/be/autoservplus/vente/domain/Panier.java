package be.autoservplus.vente.domain;

import be.autoservplus.common.montant.CalculTva;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.common.entity.BaseEntity;
import be.autoservplus.vente.service.PanierDeNatureMixteException;
import be.autoservplus.identite.domain.Utilisateur;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Panier d'achat d'un membre (F13). Un membre a au plus un panier en cours
 * (<b>RM-19</b>) : l'unicité est garantie en base par l'index partiel
 * {@code uq_panier_membre_actif} sur {@code membre_id}, le service se contente
 * de trouver-ou-créer.
 *
 * <p>Les prix, libellés et taux TVA des lignes sont <b>figés à l'ajout</b>, copiés
 * depuis la pièce : une évolution du catalogue ne réécrit jamais un panier existant.
 * Invariant de conception partagé avec {@code LigneRdv} et {@code LigneIntervention} :
 * la recopie à l'ajout préserve l'intégrité historique, sans numéro de règle
 * au CdC.</p>
 *
 * <p>L'entité porte les calculs (RM-30 : totaux ligne à ligne, jamais sur un total
 * global d'abord) et la mécanique des lignes (fusion des doublons, quantités
 * positives). Les règles qui dépendent de l'état du <i>catalogue</i> (pièce active
 * et stock disponible, contraintes de F13) vivent dans le service : le panier n'a
 * pas à interroger le stock, il reçoit des ajouts déjà arbitrés.</p>
 *
 * <p>Colonnes non mappées en V1 : {@code date_expiration} appartient à la
 * réservation virtuelle de stock (RM-21, documentée V2).</p>
 */
@Entity
@Table(name = "panier")
@SQLRestriction("deleted_at IS NULL")
public class Panier extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference", nullable = false, updatable = false)
    private UUID reference;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "membre_id", nullable = false)
    private Utilisateur membre;

    @OneToMany(mappedBy = "panier", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<LignePanier> lignes = new ArrayList<>();

    protected Panier() {
        // requis par JPA
    }

    public Panier(Utilisateur membre) {
        this.reference = UUID.randomUUID();
        this.membre = Objects.requireNonNull(membre, "membre");
    }

    // --- lignes ----------------------------------------------------------------------

    /**
     * Ajoute une pièce au panier, ou <b>incrémente la ligne existante</b> si la pièce
     * y figure déjà : jamais deux lignes pour la même pièce. En cas de fusion, les
     * valeurs figées à la première entrée (prix, libellé, taux) sont conservées :
     * c'est la date d'entrée au panier qui fait foi, pas le catalogue du jour.
     */
    public LignePanier ajouterPiece(Piece piece, int quantite) {
        Objects.requireNonNull(piece, "piece");
        exigerNatureCompatible(false);
        LignePanier existante = lignePour(piece).orElse(null);
        if (existante != null) {
            existante.augmenterQuantite(quantite);
            return existante;
        }
        LignePanier ligne = new LignePanier(this, piece, quantite);
        this.lignes.add(ligne);
        return ligne;
    }

    /**
     * Ajoute une prestation au panier, ou incrémente la ligne existante (F12), selon
     * le même patron que {@link #ajouterPiece} : valeurs figées à la première entrée.
     *
     * @throws PanierDeNatureMixteException si le panier contient déjà des pièces
     */
    public LignePanier ajouterService(Prestation prestation, int quantite) {
        Objects.requireNonNull(prestation, "prestation");
        exigerNatureCompatible(true);
        LignePanier existante = lignePour(prestation).orElse(null);
        if (existante != null) {
            existante.augmenterQuantite(quantite);
            return existante;
        }
        LignePanier ligne = new LignePanier(this, prestation, quantite);
        this.lignes.add(ligne);
        return ligne;
    }

    /**
     * <b>Un panier est d'une seule nature</b> : que des pièces, ou que des services.
     *
     * <p>Garde qui évite la rétractation PARTIELLE. Une commande mixte serait
     * insoluble en V1 : une pièce reste rétractable quand un service pleinement
     * exécuté sous renonciation VI.53 ne l'est plus, et annuler une partie de la
     * commande demanderait de lever {@code uq_avoir_facture}, ce que F30 a remis à
     * la V2.</p>
     *
     * <p>La garde vit ICI, au plus tôt, plutôt qu'au passage en commande : refuser
     * l'ajout laisse le membre corriger tout de suite, alors qu'un panier constitué
     * puis rejeté à la caisse lui ferait perdre son travail sans qu'il comprenne
     * pourquoi.</p>
     */
    private void exigerNatureCompatible(boolean ajoutDUnService) {
        boolean contientDesServices = lignes.stream().anyMatch(LignePanier::estService);
        boolean contientDesPieces = lignes.stream().anyMatch(ligne -> !ligne.estService());
        if (ajoutDUnService && contientDesPieces) {
            throw new PanierDeNatureMixteException(true);
        }
        if (!ajoutDUnService && contientDesServices) {
            throw new PanierDeNatureMixteException(false);
        }
    }

    /** Le panier ne contient que des prestations, et il en contient au moins une. */
    public boolean estPanierDeServices() {
        return !lignes.isEmpty() && lignes.stream().allMatch(LignePanier::estService);
    }

    /** Quantité déjà au panier pour cette prestation. */
    public int quantitePour(Prestation prestation) {
        return lignePour(prestation).map(ligne -> (int) ligne.getQuantite()).orElse(0);
    }

    /** Quantité déjà au panier pour cette pièce : sert au contrôle de stock cumulé. */
    public int quantitePour(Piece piece) {
        return lignePour(piece).map(l -> (int) l.getQuantite()).orElse(0);
    }

    public Optional<LignePanier> ligne(Long ligneId) {
        Objects.requireNonNull(ligneId, "ligneId");
        return lignes.stream().filter(l -> ligneId.equals(l.getId())).findFirst();
    }

    /**
     * Change la quantité d'une ligne. Passe par l'agrégat : {@code changerQuantite}
     * reste package-private, aucune ligne ne se modifie hors de son panier.
     */
    public boolean modifierQuantite(Long ligneId, int quantite) {
        LignePanier existante = ligne(ligneId).orElse(null);
        if (existante == null) {
            return false;
        }
        existante.changerQuantite(quantite);
        return true;
    }

    // Les deux retraits ci-dessous EPARGNENT toute ligne déjà rattachée à une
    // commande : la retirer de la collection la marquerait orpheline et Hibernate
    // supprimerait physiquement une pièce comptable. En production le cas est
    // inatteignable (une ligne de commande n'est plus chargée avec le panier),
    // mais l'immuabilité ne repose pas sur cette hypothèse de chargement.

    public boolean retirerLigne(Long ligneId) {
        Objects.requireNonNull(ligneId, "ligneId");
        return this.lignes.removeIf(l -> ligneId.equals(l.getId()) && l.getCommande() == null);
    }

    public void vider() {
        this.lignes.removeIf(l -> l.getCommande() == null);
    }

    public boolean estVide() {
        return lignes.isEmpty();
    }

    /** Nombre total d'articles (somme des quantités), pour le compteur d'en-tête. */
    public int nombreArticles() {
        return lignes.stream().mapToInt(LignePanier::getQuantite).sum();
    }

    /**
     * La comparaison passe par la référence publique de la pièce, pas par l'égalité
     * d'entité : deux instances non persistées ont un id null et {@code equals} les
     * distinguerait à tort, alors que la référence est posée dès la construction.
     */
    private Optional<LignePanier> lignePour(Piece piece) {
        return lignes.stream()
                .filter(l -> !l.estService())
                .filter(l -> l.getPiece().getReference().equals(piece.getReference()))
                .findFirst();
    }

    /** Même recherche par référence pour une prestation (F12). */
    private Optional<LignePanier> lignePour(Prestation prestation) {
        return lignes.stream()
                .filter(LignePanier::estService)
                .filter(l -> l.getPrestation().getReference().equals(prestation.getReference()))
                .findFirst();
    }

    // --- totaux (RM-30) --------------------------------------------------------------
    //
    // Le total taxe comprise est la somme des totaux de ligne, chacun étant le prix unitaire
    // affiché fois la quantité. La base et la TVA se déduisent de ce total, par taux et sur
    // tout le panier (CalculTva) : la TVA est une différence, ce qui garantit l'identité
    // HTVA + TVA = TVAC que la table commande vérifie par CHECK (ck_commande_coherence) à la
    // conversion. Ce sont ces trois nombres que la commande fige et que la facture reprend.

    public BigDecimal totalHtva() {
        return CalculTva.totalHtva(ventilation());
    }

    public BigDecimal totalTva() {
        return CalculTva.totalTva(ventilation());
    }

    public BigDecimal totalTvac() {
        return somme(LignePanier::totalTvac);
    }

    /** Base et TVA par taux, sur l'ensemble des lignes du panier. */
    public List<CalculTva.Tranche> ventilation() {
        return CalculTva.ventiler(lignes.stream()
                .map(ligne -> new CalculTva.MontantTaxe(ligne.getTauxTva(), ligne.totalTvac()))
                .toList());
    }

    private BigDecimal somme(Function<LignePanier, BigDecimal> montant) {
        return lignes.stream()
                .map(montant)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    // --- getters ---------------------------------------------------------------------

    public Long getId() { return id; }
    public UUID getReference() { return reference; }
    public Utilisateur getMembre() { return membre; }
    public List<LignePanier> getLignes() { return Collections.unmodifiableList(lignes); }

    @Override
    public boolean equals(Object autre) {
        if (this == autre) return true;
        if (!(autre instanceof Panier panier)) return false;
        return id != null && id.equals(panier.id);
    }

    @Override
    public int hashCode() {
        return Panier.class.hashCode();
    }
}
