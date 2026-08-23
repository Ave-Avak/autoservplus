package be.autoservplus.galerie.domain;

import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.Prestation;
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

import java.util.Objects;

/**
 * Une image de la galerie (BL-9). Table {@code photo} du socle V7, elargie par V30.
 *
 * <p><b>Exactement une origine</b> parmi prestation, piece et intervention — invariant
 * porte par le CHECK {@code ck_photo_origine_unique} et redouble ici par des fabriques
 * nommees. Sans lui, une photo sans origine serait orpheline (invisible partout,
 * jamais nettoyee) et une photo a deux origines s afficherait a deux endroits sans
 * qu on sache lequel fait foi.</p>
 *
 * <p><b>Le chemin est relatif</b> a la racine de stockage, comme celui des factures :
 * un chemin absolu casserait au premier changement de machine ou de volume. La base ne
 * connait que ce chemin ; le fichier lui-meme vit hors du webroot et n est servi que
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", updatable = false)
    private Prestation prestation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "piece_id", updatable = false)
    private Piece piece;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "intervention_id", updatable = false)
    private Intervention intervention;

    @Column(name = "chemin", length = 255, nullable = false, updatable = false)
    private String chemin;

    @Column(name = "texte_alt", length = LONGUEUR_TEXTE_ALT, nullable = false)
    private String texteAlt;

    @Column(name = "ordre", nullable = false)
    private short ordre;

    protected Photo() {
        // requis par JPA
    }

    private Photo(String chemin, String texteAlt, short ordre) {
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
    public Prestation getPrestation() { return prestation; }
    public Piece getPiece() { return piece; }
    public Intervention getIntervention() { return intervention; }
    public String getChemin() { return chemin; }
    public String getTexteAlt() { return texteAlt; }
    public short getOrdre() { return ordre; }
}
