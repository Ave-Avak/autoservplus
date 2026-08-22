package be.autoservplus.intervention.domain;

import be.autoservplus.common.entity.BaseEntity;
import be.autoservplus.reservation.domain.LigneRdv;
import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.domain.Vehicule;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Travail effectue sur un vehicule au garage. Peut naitre d un rendez-vous
 * (constructeur ici) ou d une entree directe (constructeur futur, hors V1).
 *
 * <p>La machine a etats est portee par l entite. Les transitions autorisees
 * sont encapsulees dans {@link StatutIntervention#peutPasserA} et une transition
 * interdite leve {@link IllegalStateException}. {@code @Version} protege les
 * ecritures concurrentes : deux mecaniciens ne peuvent pas modifier la meme
 * intervention en meme temps.</p>
 *
 * <p>Le mot cle {@code commentaireAdmin} est visible du client dans son ecran
 * de suivi (F17), distinct du diagnostic technique qui reste interne.</p>
 */
@Entity
@Table(name = "intervention")
@SQLRestriction("deleted_at IS NULL")
public class Intervention extends BaseEntity {

    /**
     * Coefficient de RM-15 : « un depassement de plus de dix pour cent du devis
     * exige un accord expres du client avant poursuite » (dictionnaire, commentaire
     * de {@code intervention.depassement_notifie}).
     */
    private static final BigDecimal SEUIL_RM15 = new BigDecimal("1.10");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference", nullable = false, updatable = false)
    private UUID reference;

    @Column(name = "numero", nullable = false, updatable = false, length = 20)
    private String numero;

    // rdv_id est NULLABLE en base : une intervention peut naitre d une entree
    // directe au garage. En V1, ce constructeur n en cree que depuis un RDV.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rdv_id")
    private Rdv rdv;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicule_id", nullable = false)
    private Vehicule vehicule;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 30)
    private StatutIntervention statut = StatutIntervention.PLANIFIEE;

    @Column(name = "commentaire_admin", columnDefinition = "text")
    private String commentaireAdmin;

    /**
     * Devis initial HTVA, fige a la creation depuis les lignes du RDV. Reference de
     * comparaison de RM-15 : le seuil se calcule sur ce montant, jamais sur un total
     * recalcule apres coup — sinon chaque ajout deplacerait la base de comparaison et
     * aucun depassement ne serait jamais atteint.
     *
     * <p>Colonne {@code montant_devis_htva} posee des V5 (dictionnaire, RM-15) et
     * restee vide jusqu au mapping de ce lot. V21 la passe NOT NULL : une intervention
     * sans devis rendrait le seuil incalculable, et la regle s eteindrait sans bruit.
     * {@link #devisReferenceHtva()} conserve un repli pour l instance encore transiente,
     * avant que la base n ait son mot a dire.</p>
     */
    @Column(name = "montant_devis_htva", nullable = false, precision = 10, scale = 2)
    private BigDecimal montantDevisInitialHtva;

    @Column(name = "debut_reel")
    private Instant debutReel;

    @Column(name = "fin_reelle")
    private Instant finReelle;

    @OneToMany(mappedBy = "intervention", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<LigneIntervention> lignes = new ArrayList<>();

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Intervention() {
        // requis par JPA
    }

    /**
     * Cree une intervention PLANIFIEE liee au rendez-vous fourni, avec une ligne
     * de main d oeuvre pre-remplie par prestation reservee. Prix et taux sont
     * recopies de la ligne du RDV, ils ont deja ete figes a la reservation.
     *
     * <p>Les lignes naissent {@code ajouteeEnCours = false} et validees : le membre
     * les a acceptees en reservant, elles constituent le devis initial. Ce devis est
     * fige ici dans {@link #montantDevisInitialHtva} — c est l invariant que RM-15
     * compare. Le figer dans l entite plutot que dans le service garantit qu aucun
     * chemin de creation ne puisse produire une intervention sans devis de reference.</p>
     */
    public Intervention(String numero, Rdv rdv) {
        this.reference = UUID.randomUUID();
        this.numero = Objects.requireNonNull(numero, "numero");
        this.rdv = Objects.requireNonNull(rdv, "rdv");
        this.vehicule = Objects.requireNonNull(rdv.getVehicule(), "rdv.vehicule");
        for (LigneRdv l : rdv.getLignes()) {
            this.lignes.add(new LigneIntervention(this, l.getPrestation(),
                    l.getQuantite(), l.getPrixUnitaireHtva(), l.getTauxTva(), false));
        }
        this.montantDevisInitialHtva = totalDevisInitialHtva();
    }

    // --- transitions ---------------------------------------------------------------

    public void demarrer(Instant maintenant) {
        transitionVers(StatutIntervention.EN_COURS);
        if (debutReel == null) {
            debutReel = Objects.requireNonNull(maintenant, "maintenant");
        }
    }

    public void suspendre() {
        transitionVers(StatutIntervention.SUSPENDUE);
    }

    /**
     * Reprend le travail depuis SUSPENDUE ou ATTENTE_VALIDATION_MEMBRE. La
     * machine a etats garde les deux cas via {@code peutPasserA} ; le domaine
     * n a pas besoin de distinguer, le service peut proposer deux boutons
     * distincts a l ecran si necessaire.
     *
     * <p><b>RM-15</b> : le garage ne peut pas reprendre la main tant qu une ligne
     * attend la reponse du membre. La garde vit ici, pas dans le DTO qui masque
     * deja le bouton : masquer un bouton n empeche pas un POST direct.</p>
     */
    public void reprendre() {
        if (aDesLignesEnAttente()) {
            throw new IllegalStateException(
                    "Le membre doit d abord se prononcer sur le depassement de devis (RM-15).");
        }
        transitionVers(StatutIntervention.EN_COURS);
    }

    /**
     * Terminaison de l intervention. Ne peut se faire qu apres passage par
     * EN_COURS (la machine a etats interdit PLANIFIEE -> TERMINEE), donc
     * {@code debutReel} est toujours pose lorsqu on arrive ici. Le module
     * facturation (post-V1) branchera sa generation de facture sur cette
     * transition (RM-17).
     */
    public void terminer(Instant maintenant) {
        transitionVers(StatutIntervention.TERMINEE);
        this.finReelle = Objects.requireNonNull(maintenant, "maintenant");
    }

    /** Annulation definitive de l intervention (avant, pendant, ou en pause). */
    public void annuler() {
        transitionVers(StatutIntervention.ANNULEE);
    }

    private void transitionVers(StatutIntervention cible) {
        if (!statut.peutPasserA(cible)) {
            throw new IllegalStateException(
                    "Transition d intervention interdite : %s vers %s.".formatted(statut, cible));
        }
        this.statut = cible;
    }

    // --- gestion des lignes et du commentaire (uniquement si editable) ---

    public void modifierCommentaireAdmin(String texte) {
        exigerEditable();
        this.commentaireAdmin = (texte == null || texte.isBlank()) ? null : texte.trim();
    }

    public LigneIntervention ajouterLigneMainOeuvre(be.autoservplus.catalogue.domain.Prestation prestation,
                                                    short quantite,
                                                    BigDecimal prixUnitaireHtva,
                                                    BigDecimal tauxTva) {
        exigerEditable();
        LigneIntervention ligne = new LigneIntervention(this, prestation, quantite,
                prixUnitaireHtva, tauxTva, true);
        this.lignes.add(ligne);
        appliquerSeuilDepassement(ligne);
        return ligne;
    }

    public LigneIntervention ajouterLignePiece(be.autoservplus.catalogue.domain.Piece piece,
                                               short quantite) {
        exigerEditable();
        LigneIntervention ligne = new LigneIntervention(this, piece, quantite, true);
        this.lignes.add(ligne);
        appliquerSeuilDepassement(ligne);
        return ligne;
    }

    // --- RM-15 : depassement de devis -----------------------------------------------

    /**
     * Applique RM-15 a la ligne qui vient d etre ajoutee.
     *
     * <p>Trois issues. Si l intervention attend deja une reponse du membre, la ligne
     * rejoint simplement le lot en attente — on ne redemande pas un accord deja
     * demande. Si le travail n a pas commence (PLANIFIEE), la regle ne s applique
     * pas : le CdC garde la « poursuite » des travaux, et le garage ajuste encore
     * son chiffrage. Sinon on compare le total facturable au devis majore : au-dela,
     * la ligne bascule en attente et l intervention avec elle.</p>
     *
     * <p>La comparaison est <b>cumulative</b> et porte sur le total, pas sur l apport
     * de la ligne seule : trois ajouts de 4 % chacun declenchent la regle, alors
     * qu aucun ne la declencherait isolement.</p>
     */
    private void appliquerSeuilDepassement(LigneIntervention nouvelle) {
        if (statut == StatutIntervention.ATTENTE_VALIDATION_MEMBRE) {
            nouvelle.mettreEnAttente();
            return;
        }
        if (statut != StatutIntervention.EN_COURS && statut != StatutIntervention.SUSPENDUE) {
            return;
        }
        if (totalFacturableHtva().compareTo(seuilDepassementHtva()) <= 0) {
            return;
        }
        nouvelle.mettreEnAttente();
        transitionVers(StatutIntervention.ATTENTE_VALIDATION_MEMBRE);
    }

    /**
     * Devis de reference majore de 10 %, en HTVA. Le depassement est franchi
     * <b>strictement au-dela</b> : le CdC parle d un depassement « de plus de dix
     * pour cent », donc un total pile a 110 % du devis ne declenche rien. La
     * comparaison passe par {@code compareTo} et non {@code equals}, qui tiendrait
     * compte de l echelle et distinguerait a tort 53.90 de 53.900.
     */
    public BigDecimal seuilDepassementHtva() {
        return devisReferenceHtva().multiply(SEUIL_RM15).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Le membre accepte le depassement : les lignes en attente entrent dans le
     * total facturable et le garage reprend la main.
     */
    public void validerDepassement() {
        exigerAttenteValidation();
        lignesEnAttente().forEach(LigneIntervention::valider);
        transitionVers(StatutIntervention.EN_COURS);
    }

    /**
     * Le membre refuse le depassement : les lignes proposees sont marquees refusees
     * — conservees comme trace du defaut constate, hors total, non executees — et le
     * travail reprend sur le perimetre initial.
     */
    public void refuserDepassement() {
        exigerAttenteValidation();
        lignesEnAttente().forEach(LigneIntervention::refuser);
        transitionVers(StatutIntervention.EN_COURS);
    }

    private void exigerAttenteValidation() {
        if (statut != StatutIntervention.ATTENTE_VALIDATION_MEMBRE) {
            throw new IllegalStateException(
                    "Aucun depassement de devis n est en attente de reponse (statut %s)."
                            .formatted(statut));
        }
    }

    public List<LigneIntervention> lignesEnAttente() {
        return lignes.stream().filter(LigneIntervention::estEnAttente).toList();
    }

    public boolean aDesLignesEnAttente() {
        return lignes.stream().anyMatch(LigneIntervention::estEnAttente);
    }

    public boolean retirerLigne(Long ligneId) {
        exigerEditable();
        return this.lignes.removeIf(l -> ligneId.equals(l.getId()));
    }

    private void exigerEditable() {
        if (!statut.estEditable()) {
            throw new IllegalStateException(
                    "L intervention est %s, ses lignes et son commentaire ne peuvent plus etre modifies."
                            .formatted(statut));
        }
    }

    // --- calculs -----------------------------------------------------------------

    /**
     * Devis initial recalcule : somme HTVA des seules lignes issues du RDV.
     * Sert a figer {@link #montantDevisInitialHtva} a la creation et de repli pour
     * les interventions anterieures au mapping de la colonne.
     */
    public BigDecimal totalDevisInitialHtva() {
        return sommeHtva(l -> !l.isAjouteeEnCours());
    }

    /**
     * Montant de reference de RM-15 : le devis fige, ou a defaut le devis recalcule
     * depuis les lignes d origine. Le repli couvre les interventions creees avant que
     * la colonne ne soit mappee ; sans lui, un {@code null} rendrait tout seuil
     * incalculable et desactiverait la regle sans que personne ne le voie.
     */
    public BigDecimal devisReferenceHtva() {
        return montantDevisInitialHtva != null ? montantDevisInitialHtva : totalDevisInitialHtva();
    }

    /**
     * Ce qui sera reellement facture : lignes validees et non refusees. Exclut donc
     * a la fois les lignes en attente d accord du membre et celles qu il a refusees.
     * C est ce total — et lui seul — qui s affiche au membre comme au garage.
     */
    public BigDecimal totalFacturableHtva() {
        return sommeHtva(LigneIntervention::estFacturable);
    }

    /**
     * Total HTVA si le membre accepte tout ce qui lui est propose : facturable +
     * lignes en attente. Sert a lui presenter le montant sur lequel il se prononce.
     */
    public BigDecimal totalProposeHtva() {
        return sommeHtva(l -> !l.isRefusee());
    }

    public BigDecimal totalFacturableTvac() {
        return lignes.stream()
                .filter(LigneIntervention::estFacturable)
                .map(LigneIntervention::totalTvac)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal sommeHtva(java.util.function.Predicate<LigneIntervention> filtre) {
        return lignes.stream()
                .filter(filtre)
                .map(LigneIntervention::totalHtva)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    // --- getters -----------------------------------------------------------------

    public Long getId() { return id; }
    public UUID getReference() { return reference; }
    public String getNumero() { return numero; }
    public Rdv getRdv() { return rdv; }
    public Vehicule getVehicule() { return vehicule; }
    public StatutIntervention getStatut() { return statut; }
    public String getCommentaireAdmin() { return commentaireAdmin; }
    public Instant getDebutReel() { return debutReel; }
    public Instant getFinReelle() { return finReelle; }
    public BigDecimal getMontantDevisInitialHtva() { return montantDevisInitialHtva; }
    public List<LigneIntervention> getLignes() { return Collections.unmodifiableList(lignes); }
    public long getVersion() { return version; }

    @Override
    public boolean equals(Object autre) {
        if (this == autre) return true;
        if (!(autre instanceof Intervention intervention)) return false;
        return id != null && id.equals(intervention.id);
    }

    @Override
    public int hashCode() {
        return Intervention.class.hashCode();
    }
}
