package be.autoservplus.reservation.domain;

import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.common.entity.BaseEntity;
import be.autoservplus.identite.domain.Utilisateur;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Rendez-vous d un membre : un vehicule, un poste, un intervalle, des prestations.
 *
 * <p>La fin est deduite de la somme des durees des prestations, arrondie au pas de
 * l atelier. La machine a etats est portee par l entite. Le non-chevauchement de deux
 * rendez-vous actifs sur un meme poste est garanti par une contrainte d exclusion
 * PostgreSQL. {@code @Version} protege quant a lui les transitions d etat
 * concurrentes (l admin confirme pendant que le membre annule).</p>
 */
@Entity
@Table(name = "rdv")
@SQLRestriction("deleted_at IS NULL")
public class Rdv extends BaseEntity {

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
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicule_id", nullable = false)
    private Vehicule vehicule;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "poste_id", nullable = false)
    private PosteAtelier poste;

    @NotNull
    @Column(name = "debut", nullable = false)
    private Instant debut;

    @NotNull
    @Column(name = "fin", nullable = false)
    private Instant fin;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 25)
    private StatutRdv statut = StatutRdv.EN_ATTENTE;

    @Size(max = 2000)
    @Column(name = "commentaire", columnDefinition = "text")
    private String commentaire;

    @Column(name = "motif_refus", columnDefinition = "text")
    private String motifRefus;

    @Column(name = "date_annulation")
    private Instant dateAnnulation;

    /**
     * Écrite seulement par la réservation conditionnelle de {@code RdvRepository.reserverRappel},
     * jamais par l'entité : une instance chargée avant l'envoi effacerait sinon la
     * réservation en se sauvegardant, et le rappel repartirait.
     */
    @Column(name = "rappel_envoye_le", insertable = false, updatable = false)
    private Instant rappelEnvoyeLe;

    /** Calculée une fois à la création d'une demande en mode manuel, jamais recalculée. */
    @Column(name = "echeance_reponse")
    private Instant echeanceReponse;

    /** Même règle que {@link #rappelEnvoyeLe}, écrite par {@code RdvRepository.reserverRelance}. */
    @Column(name = "relance_envoyee_le", insertable = false, updatable = false)
    private Instant relanceEnvoyeeLe;

    @OneToMany(mappedBy = "rdv", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<LigneRdv> lignes = new ArrayList<>();

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Rdv() {
        // requis par JPA
    }

    public Rdv(String numero, Utilisateur membre, Vehicule vehicule, PosteAtelier poste,
               Instant debut, Duration pas, Collection<Prestation> prestations,
               String commentaire) {
        this.reference = UUID.randomUUID();
        this.numero = Objects.requireNonNull(numero, "numero");
        this.membre = Objects.requireNonNull(membre, "membre");
        this.vehicule = Objects.requireNonNull(vehicule, "vehicule");
        this.poste = Objects.requireNonNull(poste, "poste");
        this.debut = Objects.requireNonNull(debut, "debut");
        if (!vehicule.appartientA(membre)) {
            throw new IllegalArgumentException("Le vehicule n appartient pas au membre (RM-06).");
        }
        if (prestations == null || prestations.isEmpty()) {
            throw new IllegalArgumentException("Un rendez-vous porte sur au moins une prestation.");
        }
        prestations.stream().distinct()
                .forEach(p -> this.lignes.add(new LigneRdv(this, p, (short) 1)));
        this.fin = debut.plus(dureeArrondie(dureeEstimeeMinutes(), pas));
        this.commentaire = (commentaire == null || commentaire.isBlank()) ? null : commentaire.trim();
    }

    /** Arrondit une duree au multiple superieur du pas : 50 min sur un pas de 30 donne 60. */
    public static Duration dureeArrondie(int minutes, Duration pas) {
        long pasMinutes = pas.toMinutes();
        long blocs = Math.max(1, (minutes + pasMinutes - 1) / pasMinutes);
        return Duration.ofMinutes(blocs * pasMinutes);
    }

    // --- transitions ---------------------------------------------------------------

    /**
     * Confirmation par le garage, possible tant que le rendez-vous n'a pas commencé.
     * Confirmer un créneau déjà entamé n'engage plus rien : une demande restée sans
     * réponse jusque-là ne peut qu'être refusée.
     */
    public void confirmer(Instant maintenant) {
        if (!debut.isAfter(maintenant)) {
            throw new IllegalStateException(
                    "Un rendez-vous dont le début est passé ne peut plus être confirmé, seulement refusé.");
        }
        transitionVers(StatutRdv.CONFIRME);
    }

    public boolean peutEtreConfirme(Instant maintenant) {
        return statut.peutPasserA(StatutRdv.CONFIRME) && debut.isAfter(maintenant);
    }

    /**
     * Le client s'est présenté. Possible dès l'heure de début, pas après la fin : le
     * garage le marque présent quand il arrive, l'horodatage réel du travail étant porté
     * par l'intervention.
     */
    public void marquerHonore(Instant maintenant) {
        if (debut.isAfter(maintenant)) {
            throw new IllegalStateException(
                    "Un rendez-vous ne peut être marqué honoré avant l'heure de début.");
        }
        transitionVers(StatutRdv.HONORE);
    }

    public boolean peutEtreMarqueHonore(Instant maintenant) {
        return statut.peutPasserA(StatutRdv.HONORE) && !debut.isAfter(maintenant);
    }

    /**
     * Le client ne s'est pas présenté. Seulement après la fin du créneau : tant qu'il
     * dure, le client peut encore arriver en retard.
     */
    public void marquerAbsent(Instant maintenant) {
        if (!fin.isBefore(maintenant)) {
            throw new IllegalStateException(
                    "Un rendez-vous ne peut être marqué absent avant la fin de son créneau.");
        }
        transitionVers(StatutRdv.ABSENT);
    }

    public boolean peutEtreMarqueAbsent(Instant maintenant) {
        return statut.peutPasserA(StatutRdv.ABSENT) && fin.isBefore(maintenant);
    }

    /**
     * Vrai si le rendez-vous, confirmé à {@code maintenant}, est déjà dans la fenêtre de
     * son rappel : le courriel de confirmation vient d'arriver et en tient lieu, un
     * rappel quelques minutes après le doublerait. Un délai de rappel nul ne réserve rien.
     */
    public boolean confirmationTientLieuDeRappel(Instant maintenant, Duration delaiRappel) {
        return statut == StatutRdv.CONFIRME && !delaiRappel.isZero()
                && !maintenant.isBefore(debut.minus(delaiRappel));
    }

    /**
     * Fixe l'échéance de réponse d'une demande en attente. Une seule fois : un
     * changement ultérieur des plages, des paramètres ou du mode ne la déplace pas.
     */
    public void fixerEcheanceReponse(Instant echeance) {
        if (statut != StatutRdv.EN_ATTENTE || echeanceReponse != null) {
            throw new IllegalStateException(
                    "L'échéance de réponse se fixe une seule fois, sur une demande en attente.");
        }
        this.echeanceReponse = echeance;
    }

    /**
     * La demande est restée sans réponse jusqu'à son échéance : le créneau est libéré.
     * Une demande sans échéance (confirmée d'office, ou antérieure à l'échéance) et un
     * rendez-vous confirmé n'expirent jamais.
     *
     * <p>{@code dateAnnulation} reste vide : personne n'a annulé. Le statut et
     * l'échéance enregistrée disent ce qui s'est passé et quand.</p>
     */
    public void expirer(Instant maintenant) {
        if (!peutExpirer(maintenant)) {
            throw new IllegalStateException(
                    "Seule une demande en attente dont l'échéance est atteinte peut expirer.");
        }
        transitionVers(StatutRdv.EXPIRE);
    }

    public boolean peutExpirer(Instant maintenant) {
        return statut == StatutRdv.EN_ATTENTE && echeanceReponse != null
                && !maintenant.isBefore(echeanceReponse);
    }

    /** Refus par le garage, motif obligatoire. */
    public void refuser(String motif, Instant maintenant) {
        if (motif == null || motif.isBlank()) {
            throw new IllegalArgumentException("Un refus doit etre motive.");
        }
        transitionVers(StatutRdv.REFUSE);
        this.motifRefus = motif.trim();
        this.dateAnnulation = maintenant;
    }

    /**
     * Annulation a l initiative du garage, motif obligatoire.
     *
     * <p><b>Depuis EN_ATTENTE comme depuis CONFIRME</b>, la machine a etats autorisant
     * les deux (RM-10). Ce Javadoc disait « apres confirmation » : il decrivait une
     * restriction que le code n a jamais eue, et
     * {@code RdvTest$AnnulationGarage.depuisEnAttente} prouve le contraire depuis le
     * debut. Le cas est reel : le garage peut fermer un jour donne et annuler des
     * demandes qu il n avait pas encore tranchees. Une fois le début passé,
     * l'annulation est refusée.</p>
     *
     * <p>A ne pas confondre avec {@link #refuser} : refuser, c est decliner une demande
     * que l on n accepte pas. Annuler, c est revenir sur un creneau que l on ne peut
     * plus tenir. Les deux liberent le creneau, mais ne disent pas la meme chose au
     * membre et n aboutissent pas au meme statut.</p>
     */
    public void annulerParLeGarage(String motif, Instant maintenant) {
        if (motif == null || motif.isBlank()) {
            throw new IllegalArgumentException("Une annulation par le garage doit etre motivee.");
        }
        if (!debut.isAfter(maintenant)) {
            // Le client est attendu ou présent : l'issue est honoré ou absent, et un
            // créneau entamé ne se libère plus.
            throw new IllegalStateException(
                    "Un rendez-vous dont le début est passé ne peut plus être annulé, seulement marqué honoré ou absent.");
        }
        transitionVers(StatutRdv.ANNULE);
        this.motifRefus = motif.trim();
        this.dateAnnulation = maintenant;
    }

    public boolean peutEtreAnnuleParLeGarage(Instant maintenant) {
        return statut.peutPasserA(StatutRdv.ANNULE) && debut.isAfter(maintenant);
    }

    /**
     * Annulation par le membre, au plus tard {@code delaiAnnulation} avant le debut (RM-11).
     * Le delai est un parametre de l atelier, d ou son passage en argument.
     */
    public void annulerParLeMembre(Instant maintenant, Duration delaiAnnulation) {
        if (!peutEtreAnnuleParLeMembre(maintenant, delaiAnnulation)) {
            throw new IllegalStateException(
                    // Message affiche tel quel au membre (RdvController.annuler) : le code RM
                    // reste porte par le RegleMetierException qui enveloppe, jamais par la phrase.
                    "L annulation n est plus possible a moins de %d heures du rendez-vous."
                            .formatted(delaiAnnulation.toHours()));
        }
        transitionVers(StatutRdv.ANNULE);
        this.dateAnnulation = maintenant;
    }

    public boolean peutEtreAnnuleParLeMembre(Instant maintenant, Duration delaiAnnulation) {
        return statut.estEnCours() && !maintenant.plus(delaiAnnulation).isAfter(debut);
    }

    private void transitionVers(StatutRdv cible) {
        if (!statut.peutPasserA(cible)) {
            throw new IllegalStateException(
                    "Transition interdite : %s vers %s (RM-10).".formatted(statut, cible));
        }
        this.statut = cible;
    }

    // --- calculs -------------------------------------------------------------------

    /** Somme des durees des lignes, avant arrondi (RM-09). */
    public int dureeEstimeeMinutes() {
        return lignes.stream().mapToInt(LigneRdv::dureeMinutes).sum();
    }

    public Duration duree() {
        return Duration.between(debut, fin);
    }

    public BigDecimal montantHtva() {
        return lignes.stream().map(LigneRdv::totalHtva).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal montantTvac() {
        return lignes.stream().map(LigneRdv::totalTvac).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public boolean appartientA(Utilisateur candidat) {
        return membre != null && candidat != null
                && membre.getEmail().equalsIgnoreCase(candidat.getEmail());
    }

    public Long getId() { return id; }
    public UUID getReference() { return reference; }
    public String getNumero() { return numero; }
    public Utilisateur getMembre() { return membre; }
    public Vehicule getVehicule() { return vehicule; }
    public PosteAtelier getPoste() { return poste; }
    public Instant getDebut() { return debut; }
    public Instant getFin() { return fin; }
    public StatutRdv getStatut() { return statut; }
    public String getCommentaire() { return commentaire; }
    public String getMotifRefus() { return motifRefus; }
    public Instant getDateAnnulation() { return dateAnnulation; }
    public Instant getRappelEnvoyeLe() { return rappelEnvoyeLe; }
    public Instant getEcheanceReponse() { return echeanceReponse; }
    public Instant getRelanceEnvoyeeLe() { return relanceEnvoyeeLe; }
    public List<LigneRdv> getLignes() { return Collections.unmodifiableList(lignes); }
    public long getVersion() { return version; }

    @Override
    public boolean equals(Object autre) {
        if (this == autre) return true;
        if (!(autre instanceof Rdv rdv)) return false;
        return id != null && id.equals(rdv.id);
    }

    @Override
    public int hashCode() {
        return Rdv.class.hashCode();
    }
}