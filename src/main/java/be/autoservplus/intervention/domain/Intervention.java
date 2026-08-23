package be.autoservplus.intervention.domain;

import be.autoservplus.common.entity.BaseEntity;
import be.autoservplus.reservation.domain.LigneRdv;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.domain.Vehicule;
import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.domain.LignePanier;
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

    /**
     * Commande de services payee dont cette intervention execute les lignes (F12-b).
     *
     * <p><b>Seconde origine, exclusive de {@link #rdv}</b> — CHECK
     * {@code ck_intervention_origine_unique}. Les deux nuls restent admis : c est
     * l entree directe au garage que le socle prevoit. Ce lien est ce qui permet a F30
     * de savoir si un service vendu sous renonciation VI.53 a ete pleinement
     * execute.</p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commande_id")
    private Commande commande;

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
     * <p>Les lignes naissent {@code ajouteeEnCours = false}, donc {@code accordMembre}
     * a {@code null} et pour toujours : le membre les a acceptees en reservant, on ne
     * lui redemande pas un accord sur ce qu il a commande. Ce devis est
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

    /**
     * Intervention nee d une commande de services payee (F12-b).
     *
     * <p><b>S ajoute a la creation depuis un RDV, ne la remplace pas.</b> Le constructeur
     * public reste celui du rendez-vous honore, et son invariant de creation atomique
     * (RM-14/15/16) est inchange. La machine a etats est la MEME quelle que soit
     * l origine : on n a pas dedouble les statuts.</p>
     *
     * <p>Les lignes reprennent les valeurs <b>figees a la commande</b> — prix, libelle,
     * taux — et non le catalogue du jour : c est ce que le client a paye qui fait foi,
     * comme partout ailleurs (RM-30).</p>
     *
     * <p>Le vehicule est <b>choisi par le garage</b> a la creation : la colonne est
     * {@code NOT NULL} et une prestation achetee en ligne n est rattachee a aucun
     * vehicule au moment de l achat. C est aussi le bon moment pour le demander —
     * l atelier sait sur quoi il va travailler.</p>
     *
     * @param lignes lignes de service de la commande, deja filtrees par l appelant
     */
    public static Intervention pourCommande(String numero, Commande commande,
                                            Vehicule vehicule, List<LignePanier> lignes) {
        Intervention it = new Intervention();
        it.reference = UUID.randomUUID();
        it.numero = Objects.requireNonNull(numero, "numero");
        it.commande = Objects.requireNonNull(commande, "commande");
        it.vehicule = Objects.requireNonNull(vehicule, "vehicule");
        Objects.requireNonNull(lignes, "lignes");
        if (lignes.isEmpty()) {
            throw new IllegalArgumentException(
                    "Une intervention doit executer au moins une ligne de service.");
        }
        for (LignePanier ligne : lignes) {
            if (!ligne.estService()) {
                throw new IllegalArgumentException(
                        "Seules les lignes de service donnent lieu a une intervention.");
            }
            it.lignes.add(new LigneIntervention(it, ligne.getPrestation(),
                    ligne.getQuantite(), ligne.getPrixUnitaireHtva(), ligne.getTauxTva(),
                    false));
        }
        it.montantDevisInitialHtva = it.totalDevisInitialHtva();
        return it;
    }

    /**
     * Membre concerne, quelle que soit l origine.
     *
     * <p>Centralise ce que plusieurs appelants faisaient a la main via
     * {@code getRdv().getMembre()} — expression qui levait une NPE des qu une
     * intervention ne venait pas d un rendez-vous. Rend {@code null} pour une entree
     * directe au garage, seul cas ou personne n est rattache.</p>
     */
    public Utilisateur titulaire() {
        if (rdv != null) {
            return rdv.getMembre();
        }
        return commande == null ? null : commande.getMembre();
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

    /**
     * Ajoute une prestation au dossier. Refuse hors EN_COURS (RM-14) : voir
     * {@link #exigerAjoutEnCours()}.
     */
    public LigneIntervention ajouterLigneMainOeuvre(be.autoservplus.catalogue.domain.Prestation prestation,
                                                    short quantite,
                                                    BigDecimal prixUnitaireHtva,
                                                    BigDecimal tauxTva) {
        exigerAjoutEnCours();
        LigneIntervention ligne = new LigneIntervention(this, prestation, quantite,
                prixUnitaireHtva, tauxTva, true);
        this.lignes.add(ligne);
        appliquerSeuilDepassement(ligne);
        return ligne;
    }

    /** Ajoute une piece detachee au dossier. Meme garde que la main d oeuvre (RM-14). */
    public LigneIntervention ajouterLignePiece(be.autoservplus.catalogue.domain.Piece piece,
                                               short quantite) {
        exigerAjoutEnCours();
        LigneIntervention ligne = new LigneIntervention(this, piece, quantite, true);
        this.lignes.add(ligne);
        appliquerSeuilDepassement(ligne);
        return ligne;
    }

    /**
     * <b>RM-14</b> : le CdC n autorise l ajout d une ligne que « en cours de
     * realisation ». La garde est ici, et non dans le service, pour la meme raison
     * que le blocage de {@link #reprendre()} : un invariant metier ne se defend pas
     * a la couche web.
     *
     * <p>Elle est plus stricte que {@link StatutIntervention#estEditable()}, qui
     * reste la regle du commentaire admin et du retrait de ligne. Trois etats
     * editables perdent donc le droit d ajout, chacun pour une raison propre :</p>
     * <ul>
     *   <li>PLANIFIEE : rien n a commence. C etait le trou de RM-15 — un ajout ici
     *       echappait au controle de seuil (voir {@link #appliquerSeuilDepassement}),
     *       et le devis pouvait grossir sans que le membre ne soit jamais consulte.</li>
     *   <li>SUSPENDUE : le travail est a l arret, le garage reprend d abord.</li>
     *   <li>ATTENTE_VALIDATION_MEMBRE : une question est deja posee au membre ; on
     *       n en empile pas une seconde avant sa reponse.</li>
     * </ul>
     *
     * <p>Consequence structurelle : la seule facon d obtenir une ligne hors EN_COURS
     * est le devis initial, pose par le constructeur depuis les lignes du RDV. Toute
     * ligne passee par {@code ajouterLigne*} l a donc ete en EN_COURS, ou le seuil
     * RM-15 est evalue sans exception. Le controle couvre tous les cas par
     * construction, pas par enumeration.</p>
     */
    private void exigerAjoutEnCours() {
        if (!statut.accepteAjoutDeLigne()) {
            throw new IllegalStateException(
                    "Une ligne ne peut être ajoutée qu'en cours d'intervention (RM-14). "
                            + (statut == StatutIntervention.PLANIFIEE
                                    ? "Démarrez l'intervention d'abord."
                                    : "Statut actuel : %s.".formatted(statut)));
        }
    }

    // --- RM-15 : depassement de devis -----------------------------------------------

    /**
     * Applique RM-15 a la ligne qui vient d etre ajoutee : le total facturable est
     * compare au devis majore, et au-dela la ligne bascule en attente d accord,
     * l intervention avec elle.
     *
     * <p>Aucune garde de statut ici : {@link #exigerAjoutEnCours()} a deja etabli
     * que l intervention est EN_COURS. C est ce qui ferme le trou de RM-15 — tant
     * que l ajout etait ouvert a PLANIFIEE, une exemption de statut vivait dans
     * cette methode et laissait passer un devis gonfle sans accord du membre.</p>
     *
     * <p>La comparaison est <b>cumulative</b> et porte sur le total, pas sur l apport
     * de la ligne seule : trois ajouts de 4 % chacun declenchent la regle, alors
     * qu aucun ne la declencherait isolement.</p>
     */
    private void appliquerSeuilDepassement(LigneIntervention nouvelle) {
        // La ligne naît sans réponse. Sous le seuil, le garage tranche d office : le
        // membre a accepte cette marge en acceptant le devis, on ne l interrompt pas
        // pour 2 %. Au-dela, la ligne reste sans reponse — c est la question posee.
        // Aucune ligne ne sort d ici indecise par omission : les deux cas ecrivent.
        if (totalProposeHtva().compareTo(seuilDepassementHtva()) <= 0) {
            nouvelle.accepter();
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
     * Le membre accepte le depassement : les lignes en attente passent
     * {@code accordMembre = true}, entrent dans le total facturable, et le garage
     * reprend la main.
     */
    public void validerDepassement() {
        exigerAttenteValidation();
        lignesEnAttente().forEach(LigneIntervention::accepter);
        transitionVers(StatutIntervention.EN_COURS);
    }

    /**
     * Le membre refuse le depassement : les lignes proposees passent
     * {@code accordMembre = false} — conservees comme trace du defaut constate, hors
     * total, non executees — et le travail reprend sur le perimetre initial.
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
        return lignes.stream().filter(LigneIntervention::estEnAttenteValidation).toList();
    }

    public boolean aDesLignesEnAttente() {
        return lignes.stream().anyMatch(LigneIntervention::estEnAttenteValidation);
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
     * Ce qui sera reellement facture : le devis initial, plus les seuls ajouts que le
     * membre a acceptes. Exclut donc a la fois les lignes <b>en attente</b> de sa
     * reponse et celles qu il a <b>refusees</b> — la premiere n est pas encore acquise,
     * la seconde ne le sera jamais. C est ce total, et lui seul, qui s affiche au
     * membre comme au garage.
     */
    public BigDecimal totalFacturableHtva() {
        return sommeHtva(LigneIntervention::estFacturable);
    }

    /**
     * Total HTVA si le membre accepte tout ce qui lui est propose : facturable +
     * lignes en attente. Sert a lui presenter le montant sur lequel il se prononce.
     */
    public BigDecimal totalProposeHtva() {
        return sommeHtva(l -> !l.estRefusee());
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
    public Commande getCommande() { return commande; }
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
