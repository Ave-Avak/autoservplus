package be.autoservplus.vente.domain;

import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.common.entity.BaseEntity;
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
 * Panier d achat d un membre (F13). Un membre a au plus un panier en cours
 * (<b>RM-19</b>) : l unicite est garantie en base par l index partiel
 * {@code uq_panier_membre_actif} sur {@code membre_id}, le service se contente
 * de trouver-ou-creer.
 *
 * <p>Les prix, libelles et taux TVA des lignes sont <b>figes a l ajout</b>, copies
 * depuis la piece : une evolution du catalogue ne reecrit jamais un panier existant
 * (meme principe que {@code LigneRdv} et {@code LigneIntervention}, RM-30).</p>
 *
 * <p>L entite porte les calculs (RM-30 : totaux ligne a ligne, jamais sur un total
 * global d abord) et la mecanique des lignes (fusion des doublons, quantites
 * positives). Les regles qui dependent de l etat du <i>catalogue</i> — piece active
 * (RM-28), stock disponible (F13) — vivent dans le service : le panier n a pas a
 * interroger le stock, il recoit des ajouts deja arbitres.</p>
 *
 * <p>Colonnes non mappees en V1 : {@code date_expiration} appartient a la
 * reservation virtuelle de stock (RM-21, documentee V2).</p>
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
     * Ajoute une piece au panier, ou <b>incremente la ligne existante</b> si la piece
     * y figure deja : jamais deux lignes pour la meme piece. En cas de fusion, les
     * valeurs figees a la premiere entree (prix, libelle, taux) sont conservees —
     * c est la date d entree au panier qui fait foi, pas le catalogue du jour.
     */
    public LignePanier ajouterPiece(Piece piece, int quantite) {
        Objects.requireNonNull(piece, "piece");
        LignePanier existante = lignePour(piece).orElse(null);
        if (existante != null) {
            existante.augmenterQuantite(quantite);
            return existante;
        }
        LignePanier ligne = new LignePanier(this, piece, quantite);
        this.lignes.add(ligne);
        return ligne;
    }

    /** Quantite deja au panier pour cette piece — sert au controle de stock cumule. */
    public int quantitePour(Piece piece) {
        return lignePour(piece).map(l -> (int) l.getQuantite()).orElse(0);
    }

    public Optional<LignePanier> ligne(Long ligneId) {
        Objects.requireNonNull(ligneId, "ligneId");
        return lignes.stream().filter(l -> ligneId.equals(l.getId())).findFirst();
    }

    /**
     * Change la quantite d une ligne. Passe par l agregat : {@code changerQuantite}
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

    public boolean retirerLigne(Long ligneId) {
        Objects.requireNonNull(ligneId, "ligneId");
        return this.lignes.removeIf(l -> ligneId.equals(l.getId()));
    }

    public void vider() {
        this.lignes.clear();
    }

    public boolean estVide() {
        return lignes.isEmpty();
    }

    /** Nombre total d articles (somme des quantites), pour le compteur d en-tete. */
    public int nombreArticles() {
        return lignes.stream().mapToInt(LignePanier::getQuantite).sum();
    }

    /**
     * La comparaison passe par la reference publique de la piece, pas par l egalite
     * d entite : deux instances non persistees ont un id null et {@code equals} les
     * distinguerait a tort, alors que la reference est posee des la construction.
     */
    private Optional<LignePanier> lignePour(Piece piece) {
        return lignes.stream()
                .filter(l -> l.getPiece().getReference().equals(piece.getReference()))
                .findFirst();
    }

    // --- totaux (RM-30) --------------------------------------------------------------
    //
    // Chaque total est la somme des montants DE LIGNE deja arrondis a 2 decimales,
    // jamais un calcul sur le total d un coup : avec des taux mixtes (6 % et 21 %),
    // appliquer une TVA au total HTVA global serait tout simplement faux, et meme a
    // taux unique l arrondi different d un centime. La TVA de ligne est definie comme
    // TVAC - HTVA, ce qui garantit l identite HTVA + TVA = TVAC que la table commande
    // verifiera par CHECK (ck_commande_coherence) au moment de la conversion.

    public BigDecimal totalHtva() {
        return somme(LignePanier::totalHtva);
    }

    public BigDecimal totalTva() {
        return somme(LignePanier::totalTva);
    }

    public BigDecimal totalTvac() {
        return somme(LignePanier::totalTvac);
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
