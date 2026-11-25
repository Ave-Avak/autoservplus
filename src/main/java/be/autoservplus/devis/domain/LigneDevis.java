package be.autoservplus.devis.domain;

import be.autoservplus.catalogue.domain.ChoixPieces;
import be.autoservplus.catalogue.domain.ConvertisseurChoixPieces;
import be.autoservplus.common.montant.CalculTva;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Ligne d'un devis, recopiée d'une ligne du panier à l'émission. Elle naît avec son
 * devis et ne change plus : aucune méthode ne la modifie.
 */
@Entity
@Table(name = "ligne_devis")
public class LigneDevis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "devis_id", nullable = false, updatable = false)
    private Devis devis;

    @Column(name = "rang", nullable = false, updatable = false)
    private short rang;

    @Column(name = "libelle", nullable = false, updatable = false, length = 150)
    private String libelle;

    @Column(name = "quantite", nullable = false, updatable = false)
    private short quantite;

    @Column(name = "prix_unitaire_htva", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal prixUnitaireHtva;

    @Column(name = "taux_tva", nullable = false, updatable = false, precision = 5, scale = 2)
    private BigDecimal tauxTva;

    @Enumerated(EnumType.STRING)
    @Column(name = "nature", nullable = false, updatable = false, length = 20)
    private NatureLigneDevis nature;

    /**
     * Repris de la ligne du panier. La ligne de devis n'a aucune clé vers la prestation : sans
     * ce champ, rien ne dirait plus quel choix le prix suppose.
     */
    @Convert(converter = ConvertisseurChoixPieces.class)
    @Column(name = "pieces_client", updatable = false)
    private ChoixPieces choixPieces;

    protected LigneDevis() {
        // requis par JPA
    }

    LigneDevis(Devis devis, short rang, NatureLigneDevis nature, String libelle, short quantite,
               BigDecimal prixUnitaireHtva, BigDecimal tauxTva, ChoixPieces choixPieces) {
        if (choixPieces != null && nature != NatureLigneDevis.PRESTATION) {
            throw new IllegalArgumentException("Le choix des pièces ne vaut que pour une prestation.");
        }
        this.nature = Objects.requireNonNull(nature, "nature");
        this.devis = Objects.requireNonNull(devis, "devis");
        this.rang = rang;
        this.libelle = Objects.requireNonNull(libelle, "libelle");
        this.quantite = quantite;
        this.prixUnitaireHtva = Objects.requireNonNull(prixUnitaireHtva, "prixUnitaireHtva");
        this.tauxTva = Objects.requireNonNull(tauxTva, "tauxTva");
        this.choixPieces = choixPieces;
    }

    /** Prix unitaire taxe comprise, celui que le panier affichait. */
    public BigDecimal prixUnitaireTvac() {
        return CalculTva.unitaireTvac(prixUnitaireHtva, tauxTva);
    }

    /** Prix unitaire taxe comprise fois la quantité, comme la ligne du panier. */
    public BigDecimal totalTvac() {
        return CalculTva.totalTvac(prixUnitaireHtva, tauxTva, quantite);
    }

    public Long getId() { return id; }
    public short getRang() { return rang; }
    public String getLibelle() { return libelle; }
    public short getQuantite() { return quantite; }
    public BigDecimal getPrixUnitaireHtva() { return prixUnitaireHtva; }
    public BigDecimal getTauxTva() { return tauxTva; }
    public NatureLigneDevis getNature() { return nature; }
    public Optional<ChoixPieces> getChoixPieces() { return Optional.ofNullable(choixPieces); }

    @Override
    public boolean equals(Object autre) {
        if (this == autre) return true;
        if (!(autre instanceof LigneDevis ligne)) return false;
        return id != null && id.equals(ligne.id);
    }

    @Override
    public int hashCode() {
        return LigneDevis.class.hashCode();
    }
}
