package be.autoservplus.galerie.domain;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.TexteLocalise;
import be.autoservplus.common.entity.BaseEntity;
import be.autoservplus.intervention.domain.Intervention;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Une image de la galerie (BL-9). Table {@code photo} du socle V7, elargie par V30.
 *
 * <p><b>Exactement une origine</b> parmi prestation, piece, intervention et catégorie
 * (V79) : invariant porte par le CHECK {@code ck_photo_origine_unique} et redouble ici par
 * des fabriques nommees. Sans lui, une photo sans origine serait orpheline (invisible partout,
 * jamais nettoyee) et une photo a deux origines s afficherait a deux endroits sans
 * qu on sache lequel fait foi.</p>
 *
 * <p><b>Le chemin est relatif</b> a la racine de stockage, comme celui des factures :
 * un chemin absolu casserait au premier changement de machine ou de volume. La base ne
 * connait que ce chemin. Le fichier lui-meme vit hors du webroot et n est servi que
 * par un controleur.</p>
 *
 * <p><b>Le texte alternatif est obligatoire</b>, colonne {@code NOT NULL} du socle :
 * une image sans alternative textuelle est invisible pour un lecteur d ecran, et
 * WCAG 2.1 AA (critere 1.1.1) l exige. Le rendre facultatif reviendrait a rendre
 * l accessibilite optionnelle.</p>
 */
@Entity
@Table(name = "photo")
@SQLRestriction("deleted_at IS NULL")
public class Photo extends BaseEntity {

    /** Longueur de la colonne {@code texte_alt} du socle V7. */
    public static final int LONGUEUR_TEXTE_ALT = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Seule adresse qui sort de la base (V68). La clé séquentielle se devinait : compter
     * de un en un suffisait à lire les photos d'intervention de tous les membres.
     */
    @Column(name = "reference", nullable = false, updatable = false)
    private UUID reference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", updatable = false)
    private Prestation prestation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "piece_id", updatable = false)
    private Piece piece;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "intervention_id", updatable = false)
    private Intervention intervention;

    /** Visuel commun aux articles de la catégorie qui n'ont pas de photo propre (V79). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categorie_id", updatable = false)
    private Categorie categorie;

    @Column(name = "chemin", length = 255, nullable = false, updatable = false)
    private String chemin;

    /**
     * Version de 480 pixels de large du même fichier (V79), servie aux écrans étroits.
     * Nulle pour une photo déposée au back-office : le JDK n'écrit pas le WebP.
     */
    @Column(name = "chemin_vignette", length = 255, updatable = false)
    private String cheminVignette;

    @Column(name = "texte_alt", length = LONGUEUR_TEXTE_ALT, nullable = false)
    private String texteAlt;

    @Column(name = "texte_alt_nl", length = LONGUEUR_TEXTE_ALT)
    private String texteAltNl;

    @Column(name = "texte_alt_en", length = LONGUEUR_TEXTE_ALT)
    private String texteAltEn;

    @Column(name = "ordre", nullable = false)
    private short ordre;

    /** Dimensions lues au dépôt (V79), nulles quand l'en-tête n'a pas pu être lu. */
    @Column(name = "largeur")
    private Integer largeur;

    @Column(name = "hauteur")
    private Integer hauteur;

    protected Photo() {
        // requis par JPA
    }

    private Photo(String chemin, String texteAlt, short ordre) {
        this.reference = UUID.randomUUID();
        this.chemin = Objects.requireNonNull(chemin, "chemin");
        this.texteAlt = exigerTexteAlt(texteAlt);
        this.ordre = ordre;
    }

    /** Illustration d une prestation du catalogue. */
    public static Photo pourPrestation(Prestation prestation, String chemin, String texteAlt,
                                       short ordre) {
        Photo photo = new Photo(chemin, texteAlt, ordre);
        photo.prestation = Objects.requireNonNull(prestation, "prestation");
        return photo;
    }

    /** Illustration d une piece du catalogue. */
    public static Photo pourPiece(Piece piece, String chemin, String texteAlt, short ordre) {
        Photo photo = new Photo(chemin, texteAlt, ordre);
        photo.piece = Objects.requireNonNull(piece, "piece");
        return photo;
    }

    /** Photo avant / apres d une intervention (V30). */
    public static Photo pourIntervention(Intervention intervention, String chemin,
                                         String texteAlt, short ordre) {
        Photo photo = new Photo(chemin, texteAlt, ordre);
        photo.intervention = Objects.requireNonNull(intervention, "intervention");
        return photo;
    }

    /** Visuel d'une catégorie, montré sur la fiche d'un article qui n'a pas de photo propre (V79). */
    public static Photo pourCategorie(Categorie categorie, String chemin, String texteAlt, short ordre) {
        Photo photo = new Photo(chemin, texteAlt, ordre);
        photo.categorie = Objects.requireNonNull(categorie, "categorie");
        return photo;
    }

    /**
     * Pose les dimensions lues dans l'en-tête du fichier. Les deux vont ensemble, comme
     * le veut {@code ck_photo_dimensions} : une largeur seule ne réserve aucune place.
     */
    public void poserDimensions(int largeur, int hauteur) {
        if (largeur <= 0 || hauteur <= 0) {
            throw new IllegalArgumentException("Dimensions d'image strictement positives attendues.");
        }
        this.largeur = largeur;
        this.hauteur = hauteur;
    }

    /** Texte alternatif dans la langue demandée, ou en français s'il n'y est pas traduit. */
    public TexteLocalise texteAlt(Locale langue) {
        return TexteLocalise.choisir(langue, texteAlt, texteAltNl, texteAltEn);
    }

    public void renommer(String texteAlt) {
        this.texteAlt = exigerTexteAlt(texteAlt);
    }

    private static String exigerTexteAlt(String valeur) {
        Objects.requireNonNull(valeur, "texteAlt");
        String texte = valeur.strip();
        if (texte.isEmpty()) {
            throw new IllegalArgumentException(
                    "Le texte alternatif est obligatoire (accessibilite, WCAG 1.1.1).");
        }
        return texte.length() <= LONGUEUR_TEXTE_ALT
                ? texte : texte.substring(0, LONGUEUR_TEXTE_ALT);
    }

    public Long getId() { return id; }
    public UUID getReference() { return reference; }
    public Prestation getPrestation() { return prestation; }
    public Piece getPiece() { return piece; }
    public Intervention getIntervention() { return intervention; }
    public String getChemin() { return chemin; }
    public Categorie getCategorie() { return categorie; }
    public String getTexteAlt() { return texteAlt; }
    public short getOrdre() { return ordre; }
    public String getCheminVignette() { return cheminVignette; }
    public Integer getLargeur() { return largeur; }
    public Integer getHauteur() { return hauteur; }
}
