package be.autoservplus.avis.domain;

import be.autoservplus.common.entity.BaseEntity;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.intervention.domain.Intervention;
import be.autoservplus.intervention.domain.StatutIntervention;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Avis depose par un membre sur une intervention terminee (BL-4). Table {@code avis}
 * du socle V7, restee vide jusqu ici.
 *
 * <p><b>L avis porte sur une intervention, pas sur une prestation.</b> C est le
 * schema du socle qui l impose ({@code intervention_id NOT NULL}, unique par
 * {@code uq_avis_intervention}) et c est le bon niveau : le membre juge un travail
 * reellement effectue sur son vehicule, a une date donnee, pas une ligne de
 * catalogue. La note moyenne d une prestation se deduit ensuite par jointure sur les
 * lignes d intervention — un avis nourrit donc autant de moyennes qu il y avait de
 * prestations dans le travail.</p>
 *
 * <p><b>Un seul avis par intervention</b>, garanti en base et non seulement dans le
 * service : sans l unicite, un rechargement de formulaire ou deux onglets ouverts
 * suffiraient a peser deux fois sur la moyenne.</p>
 *
 * <p><b>Note immuable, commentaire effacable.</b> La note ne bouge plus une fois
 * deposee : elle entre dans une moyenne publique, et la reecrire changerait
 * retroactivement une statistique deja affichee. Le commentaire, lui, est du texte
 * libre pouvant contenir des donnees personnelles — il doit pouvoir etre neutralise
 * sans que la note disparaisse (voir {@link #anonymiserCommentaire}, F23).</p>
 *
 * <p><b>Publication a priori, moderation a posteriori.</b> {@code publie} vaut
 * {@code true} par defaut en base : un avis est visible des son depot. Le garage n a
 * pas a valider ce qui le concerne avant publication — un avis retenu jusqu a
 * approbation ne serait plus un avis mais une recommandation. La moderation existe
 * pour retirer ce qui est illicite ou hors sujet, pas pour filtrer ce qui deplait.</p>
 */
@Entity
@Table(name = "avis")
@SQLRestriction("deleted_at IS NULL")
public class Avis extends BaseEntity {

    /** Bornes du CHECK {@code ck_avis_note} du socle V7. */
    public static final int NOTE_MINIMALE = 1;
    public static final int NOTE_MAXIMALE = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference", nullable = false, updatable = false)
    private UUID reference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "membre_id", nullable = false, updatable = false)
    private Utilisateur membre;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "intervention_id", nullable = false, updatable = false)
    private Intervention intervention;

    @Column(name = "note", nullable = false, updatable = false)
    private short note;

    @Column(name = "commentaire")
    private String commentaire;

    @Column(name = "publie", nullable = false)
    private boolean publie;

    @Column(name = "signale", nullable = false)
    private boolean signale;

    @Column(name = "date_depot", nullable = false, updatable = false)
    private Instant dateDepot;

    protected Avis() {
        // requis par JPA
    }

    /**
     * Depose un avis sur une intervention terminee.
     *
     * <p>Les deux gardes vivent ici et non dans le service, comme partout dans le
     * projet : un second appelant (reprise, import, test) ne peut pas les contourner
     * en oubliant de les recopier.</p>
     *
     * @throws IllegalArgumentException note hors bornes, ou intervention non terminee
     */
    public Avis(Utilisateur membre, Intervention intervention, short note,
                String commentaire, Instant maintenant) {
        this.membre = Objects.requireNonNull(membre, "membre");
        this.intervention = Objects.requireNonNull(intervention, "intervention");
        if (note < NOTE_MINIMALE || note > NOTE_MAXIMALE) {
            throw new IllegalArgumentException(
                    "La note doit etre comprise entre %d et %d.".formatted(NOTE_MINIMALE, NOTE_MAXIMALE));
        }
        // On ne juge pas un travail qui n est pas fini : une intervention en cours peut
        // encore changer de tournure, et une intervention annulee n a rien produit.
        if (intervention.getStatut() != StatutIntervention.TERMINEE) {
            throw new IllegalArgumentException(
                    "Un avis ne peut etre depose que sur une intervention terminee.");
        }
        this.note = note;
        this.commentaire = normaliser(commentaire);
        this.reference = UUID.randomUUID();
        this.publie = true;
        this.signale = false;
        this.dateDepot = Objects.requireNonNull(maintenant, "maintenant");
    }

    /** Retire l avis de l affichage public sans le supprimer : la trace reste. */
    public void masquer() {
        this.publie = false;
    }

    /** Remet un avis masque en ligne. */
    public void publier() {
        this.publie = true;
    }

    /** Marque l avis comme problematique, pour suivi par le garage. */
    public void signaler() {
        this.signale = true;
    }

    /** Leve le signalement, l avis ayant ete examine. */
    public void leverLeSignalement() {
        this.signale = false;
    }

    /**
     * Efface le commentaire lors de l anonymisation du compte de son auteur (F23,
     * art. 17 RGPD).
     *
     * <p><b>La note numerique est conservee, le texte disparait.</b> Le commentaire est
     * du texte libre : le membre a pu y ecrire son nom, sa plaque, celui de son
     * garagiste. La note, elle, ne designe personne — c est un chiffre entre 1 et 5,
     * qui n est pas plus identifiant apres l anonymisation qu avant, et qui reste
     * rattache a une ligne dont l auteur porte desormais le jeton anonyme. L effacer
     * fausserait retroactivement les moyennes publiques d une prestation, sans rien
     * apporter au droit a l effacement.</p>
     *
     * <p>L avis reste par ailleurs <b>publie</b> : le retirer serait une decision
     * commerciale (faire disparaitre une mauvaise note en supprimant un compte), pas
     * une mesure de protection des donnees.</p>
     */
    public void anonymiserCommentaire() {
        this.commentaire = null;
    }

    public boolean aUnCommentaire() {
        return commentaire != null && !commentaire.isBlank();
    }

    private static String normaliser(String texte) {
        if (texte == null || texte.isBlank()) {
            return null;
        }
        return texte.strip();
    }

    public Long getId() { return id; }
    public UUID getReference() { return reference; }
    public Utilisateur getMembre() { return membre; }
    public Intervention getIntervention() { return intervention; }
    public short getNote() { return note; }
    public String getCommentaire() { return commentaire; }
    public boolean isPublie() { return publie; }
    public boolean isSignale() { return signale; }
    public Instant getDateDepot() { return dateDepot; }
}
