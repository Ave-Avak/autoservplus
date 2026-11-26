package be.autoservplus.catalogue.domain;

import be.autoservplus.common.montant.CalculTva;
import be.autoservplus.common.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Prestation proposee par le garage : vidange, remplacement de plaquettes, diagnostic.
 *
 * <p>La classe se nomme Prestation et non Service afin d eviter toute confusion avec
 * l annotation Spring du meme nom. La table sous-jacente reste {@code service}.</p>
 */
@Entity
@Table(name = "service")
@SQLRestriction("deleted_at IS NULL")
public class Prestation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference", nullable = false, updatable = false)
    private UUID reference;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categorie_id", nullable = false)
    private Categorie categorie;

    @NotBlank
    @Size(max = 40)
    @Column(name = "code", nullable = false, length = 40, unique = true)
    private String code;

    @NotBlank
    @Size(max = 150)
    @Column(name = "libelle", nullable = false, length = 150)
    private String libelle;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Valid
    @Embedded
    private Traductions traductions = Traductions.aucune();

    @NotNull
    @DecimalMin("0.00")
    @Column(name = "prix_htva", nullable = false, precision = 10, scale = 2)
    private BigDecimal prixHtva;

    @NotNull
    @Column(name = "taux_tva", nullable = false, precision = 5, scale = 2)
    private BigDecimal tauxTva = new BigDecimal("21.00");

    @Positive
    @Column(name = "duree_minutes", nullable = false)
    private int dureeMinutes;

    /**
     * Fixée à la création (V72) : aucune méthode ne la change. Une prestation au forfait
     * déjà dans un panier, un rendez-vous ou un devis ne doit pas devenir une prestation
     * que le domaine refuse, et la durée forcée à une heure fausserait des créneaux pris.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "unite", nullable = false, length = 10, updatable = false)
    private UnitePrestation unite = UnitePrestation.FORFAIT;

    @Column(name = "actif", nullable = false)
    private boolean actif = true;

    /**
     * Prix hors TVA quand le client apporte ses pièces (V76). Nul quand le garage ne
     * propose pas ce choix, toujours nul pour une prestation à l'heure, et toujours
     * inférieur au prix pièces comprises.
     */
    @Column(name = "prix_pose_seule_htva", precision = 10, scale = 2)
    private BigDecimal prixPoseSeuleHtva;

    /** Rang dans les tarifs de l'accueil (V80), nul hors de l'accueil. Posé par la graine jusqu'à I4. */
    @Column(name = "ordre_vitrine")
    private Short ordreVitrine;

    /** Pièces consommées quand le garage les fournit (V76), dans l'ordre de leur ajout. */
    @OneToMany(mappedBy = "prestation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<ComposantPrestation> composition = new ArrayList<>();

    /** Durée d'une prestation à l'heure : celle d'une unité ({@code ck_service_duree_heure}). */
    public static final int DUREE_UNITE_HEURE = 60;

    protected Prestation() {
        // requis par JPA
    }

    /** Prestation au forfait. */
    public Prestation(Categorie categorie, String code, String libelle,
                      BigDecimal prixHtva, int dureeMinutes) {
        this(categorie, code, libelle, prixHtva, dureeMinutes, UnitePrestation.FORFAIT);
    }

    /**
     * @throws DureePrestationALHeureException une prestation à l'heure dont la durée n'est
     *                                         pas celle d'une unité
     */
    public Prestation(Categorie categorie, String code, String libelle,
                      BigDecimal prixHtva, int dureeMinutes, UnitePrestation unite) {
        this.reference = UUID.randomUUID();
        this.categorie = Objects.requireNonNull(categorie, "categorie");
        this.code = Objects.requireNonNull(code, "code");
        this.libelle = Objects.requireNonNull(libelle, "libelle");
        this.prixHtva = Objects.requireNonNull(prixHtva, "prixHtva");
        this.unite = Objects.requireNonNull(unite, "unite");
        setDureeMinutes(dureeMinutes);
    }

    /** Main-d'œuvre au taux du garage : se compte en heures, ne se réserve ni ne s'achète en ligne. */
    public boolean estALHeure() {
        return unite == UnitePrestation.HEURE;
    }

    /**
     * Refuse une unité différente de celle de la création, et accepte la même : un
     * formulaire ou un import qui renvoie l'unité en place ne change rien.
     *
     * @throws UnitePrestationFixeeException unité différente
     */
    public void verifierUnite(UnitePrestation demandee) {
        if (demandee != null && demandee != unite) {
            throw new UnitePrestationFixeeException(code);
        }
    }

    /**
     * Prix affiche au client, taxe comprise.
     *
     * <p>La règle RM-30 impose que les prix présentés au consommateur soient exprimés
     * toutes taxes comprises. C'est le nombre même qui sert au total d'une ligne : le prix
     * annoncé est le prix payé.</p>
     */
    public BigDecimal prixTvac() {
        return CalculTva.unitaireTvac(prixHtva, tauxTva);
    }

    /** Montant de la taxe pour une unite. */
    public BigDecimal montantTva() {
        return prixTvac().subtract(prixHtva);
    }

    /**
     * Change le prix pièces comprises et garde le prix pose seule en place.
     *
     * @throws PrixPoseSeuleRefuseException le nouveau prix ne dépasse plus le prix pose seule
     */
    public void modifierPrix(BigDecimal nouveauPrixHtva) {
        fixerPrix(nouveauPrixHtva, prixPoseSeuleHtva);
    }

    /**
     * Fixe les deux prix ensemble : les vérifier un à un refuserait une baisse simultanée
     * des deux, le nouveau prix pièces comprises passant sous l'ancien prix pose seule.
     *
     * @param poseSeuleHtva prix quand le client apporte ses pièces, {@code null} pour ne pas
     *                      proposer ce choix
     * @throws PrixPoseSeuleRefuseException prix pose seule négatif, égal ou supérieur au prix
     *                                      pièces comprises, ou posé sur une prestation à l'heure
     */
    public void fixerPrix(BigDecimal nouveauPrixHtva, BigDecimal poseSeuleHtva) {
        if (nouveauPrixHtva == null || nouveauPrixHtva.signum() < 0) {
            throw new IllegalArgumentException("Le prix ne peut pas etre negatif.");
        }
        if (poseSeuleHtva != null) {
            if (estALHeure()) {
                throw PrixPoseSeuleRefuseException.aLHeure(code);
            }
            if (poseSeuleHtva.signum() < 0 || poseSeuleHtva.compareTo(nouveauPrixHtva) >= 0) {
                throw PrixPoseSeuleRefuseException.pasInferieur(code);
            }
        }
        this.prixHtva = nouveauPrixHtva;
        this.prixPoseSeuleHtva = poseSeuleHtva;
    }

    /** Le client peut choisir d'apporter ses pièces, à un prix plus bas. */
    public boolean proposePoseSeule() {
        return prixPoseSeuleHtva != null;
    }

    /**
     * Prix hors TVA qu'une ligne fige pour ce choix. Une prestation qui propose un prix pose
     * seule exige un choix, une autre n'en admet aucun : la ligne garde ainsi ce qui a été
     * proposé au client, et pas seulement ce qu'il a pris.
     *
     * @param choix {@code null} quand aucun choix n'est proposé
     * @throws ChoixPiecesRefuseException choix absent alors qu'il est proposé, ou donné alors
     *                                    qu'il ne l'est pas
     */
    public BigDecimal prixHtvaPour(ChoixPieces choix) {
        if (proposePoseSeule() && choix == null) {
            throw ChoixPiecesRefuseException.manquant(this);
        }
        if (!proposePoseSeule() && choix != null) {
            throw ChoixPiecesRefuseException.nonPropose(this);
        }
        return choix == ChoixPieces.APPORTEES_PAR_CLIENT ? prixPoseSeuleHtva : prixHtva;
    }

    /**
     * Choix appliqué quand l'appelant ne le demande pas : pièces fournies si la prestation
     * propose le choix, aucun sinon. Présélection du back-office, et choix des surcharges
     * réservées aux tests. Jamais appliqué au client, qui choisit lui-même.
     */
    public ChoixPieces choixParDefaut() {
        return proposePoseSeule() ? ChoixPieces.FOURNIES_PAR_GARAGE : null;
    }

    /** Prix pose seule affiché au client, taxe comprise, ou {@code null} sans ce choix. */
    public BigDecimal prixPoseSeuleTvac() {
        return prixPoseSeuleHtva == null ? null : CalculTva.unitaireTvac(prixPoseSeuleHtva, tauxTva);
    }

    /**
     * Ajoute une pièce à la composition, ou change sa quantité si elle y est déjà. Une pièce
     * désactivée est admise : le garage peut encore la poser, et le formulaire le signale.
     *
     * @throws CompositionRefuseeException prestation à l'heure, ou quantité inférieure à un
     */
    public void fixerComposant(Piece piece, int quantite) {
        Objects.requireNonNull(piece, "piece");
        if (estALHeure()) {
            throw CompositionRefuseeException.aLHeure(code);
        }
        if (quantite < 1) {
            throw CompositionRefuseeException.quantite(code);
        }
        composition.stream().filter(c -> c.porte(piece)).findFirst()
                .ifPresentOrElse(c -> c.changerQuantite(quantite),
                        () -> composition.add(new ComposantPrestation(this, piece, quantite)));
    }

    /** @return {@code false} si la pièce n'était pas dans la composition */
    public boolean retirerComposant(Piece piece) {
        return composition.removeIf(c -> c.porte(piece));
    }

    public List<ComposantPrestation> getComposition() {
        return Collections.unmodifiableList(composition);
    }

    public void renommer(String nouveauLibelle) {
        this.libelle = Objects.requireNonNull(nouveauLibelle, "libelle");
    }

    /**
     * Deplace la prestation vers une autre categorie.
     *
     * @throws IllegalArgumentException si la categorie visee est destinee aux pieces
     */
    public void changerCategorie(Categorie nouvelleCategorie) {
        Objects.requireNonNull(nouvelleCategorie, "categorie");
        if (nouvelleCategorie.getType() != TypeCategorie.SERVICE) {
            throw new IllegalArgumentException(
                    "La categorie « %s » est destinee aux pieces, pas aux prestations."
                            .formatted(nouvelleCategorie.getCode()));
        }
        this.categorie = nouvelleCategorie;
    }

    public void activer() { this.actif = true; }
    public void desactiver() { this.actif = false; }

    public Long getId() { return id; }
    public UUID getReference() { return reference; }
    public Categorie getCategorie() { return categorie; }
    public String getCode() { return code; }
    public String getLibelle() { return libelle; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    /** Libellé dans la langue demandée, ou en français s'il n'y est pas traduit. */
    public TexteLocalise libelle(Locale langue) {
        return TexteLocalise.choisir(langue, libelle, getTraductions().getLibelleNl(), getTraductions().getLibelleEn());
    }

    /** Description dans la langue demandée, ou en français si elle n'y est pas traduite. */
    public TexteLocalise description(Locale langue) {
        return TexteLocalise.choisir(langue, description,
                getTraductions().getDescriptionNl(), getTraductions().getDescriptionEn());
    }

    /** Jamais {@code null} : Hibernate rend un composant nul quand toutes ses colonnes le sont. */
    public Traductions getTraductions() {
        return traductions == null ? Traductions.aucune() : traductions;
    }

    public void traduire(Traductions nouvelles) {
        this.traductions = Objects.requireNonNull(nouvelles, "traductions");
    }
    public BigDecimal getPrixHtva() { return prixHtva; }
    public BigDecimal getPrixPoseSeuleHtva() { return prixPoseSeuleHtva; }
    public Short getOrdreVitrine() { return ordreVitrine; }
    public BigDecimal getTauxTva() { return tauxTva; }
    public void setTauxTva(BigDecimal tauxTva) { this.tauxTva = TauxTvaBelge.verifier(tauxTva); }
    public int getDureeMinutes() { return dureeMinutes; }

    /** @throws DureePrestationALHeureException durée d'une prestation à l'heure autre qu'une heure */
    public void setDureeMinutes(int dureeMinutes) {
        if (estALHeure() && dureeMinutes != DUREE_UNITE_HEURE) {
            throw new DureePrestationALHeureException();
        }
        this.dureeMinutes = dureeMinutes;
    }

    public UnitePrestation getUnite() { return unite; }
    public boolean isActif() { return actif; }

    @Override
    public boolean equals(Object autre) {
        if (this == autre) return true;
        if (!(autre instanceof Prestation prestation)) return false;
        return id != null && id.equals(prestation.id);
    }

    @Override
    public int hashCode() {
        return Prestation.class.hashCode();
    }
}