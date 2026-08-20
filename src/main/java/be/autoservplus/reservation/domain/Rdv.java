package be.autoservplus.reservation.domain;

import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.common.entity.BaseEntity;
import be.autoservplus.identite.domain.Utilisateur;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.SQLRestriction;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.math.BigDecimal;
/**
 * Demande de rendez-vous d un membre, sur un creneau, pour un de ses vehicules.
 *
 * <p>La machine a etats est portee par l entite : aucune couche superieure ne peut
 * forcer une transition interdite. Le lien vers le creneau n est jamais rompu, la
 * colonne etant NOT NULL : une annulation rend le creneau disponible sans effacer la
 * trace de ce qui l avait occupe.</p>
 */
@Entity
@Table(name = "rdv")
@SQLRestriction("deleted_at IS NULL")
public class Rdv extends BaseEntity {

    /** Delai minimal d annulation par le membre (RM-11). */
    public static final Duration DELAI_ANNULATION = Duration.ofHours(24);

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
    @JoinColumn(name = "creneau_id", nullable = false)
    private CreneauHoraire creneau;

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

    @OneToMany(mappedBy = "rdv", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<LigneRdv> lignes = new ArrayList<>();

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Rdv() {
        // requis par JPA
    }

    public Rdv(String numero, Utilisateur membre, Vehicule vehicule, CreneauHoraire creneau,
               Collection<Prestation> prestations, String commentaire) {
        this.reference = UUID.randomUUID();
        this.numero = Objects.requireNonNull(numero, "numero");
        this.membre = Objects.requireNonNull(membre, "membre");
        this.vehicule = Objects.requireNonNull(vehicule, "vehicule");
        this.creneau = Objects.requireNonNull(creneau, "creneau");
        if (!vehicule.appartientA(membre)) {
            throw new IllegalArgumentException("Le vehicule n appartient pas au membre (RM-06).");
        }
        if (prestations == null || prestations.isEmpty()) {
            throw new IllegalArgumentException("Un rendez-vous porte sur au moins une prestation.");
        }
        prestations.stream().distinct()
                .forEach(p -> this.lignes.add(new LigneRdv(this, p, (short) 1)));
        this.commentaire = (commentaire == null || commentaire.isBlank()) ? null : commentaire.trim();
        creneau.reserver();
    }

    // --- transitions ---------------------------------------------------------------

    public void confirmer() {
        transitionVers(StatutRdv.CONFIRME);
    }

    /** Le membre s est presente et l intervention a eu lieu. */
    public void marquerHonore() {
        transitionVers(StatutRdv.HONORE);
    }

    /** Le membre ne s est pas presente : le creneau reste consomme. */
    public void marquerAbsent() {
        transitionVers(StatutRdv.ABSENT);
    }

    /** Refus par le garage, motif obligatoire. Libere le creneau. */
    public void refuser(String motif, Instant maintenant) {
        if (motif == null || motif.isBlank()) {
            throw new IllegalArgumentException("Un refus doit etre motive.");
        }
        transitionVers(StatutRdv.REFUSE);
        this.motifRefus = motif.trim();
        this.dateAnnulation = maintenant;
        creneau.liberer();
    }

    /**
     * Annulation par le membre, au plus tard 24 heures avant le creneau (RM-11).
     *
     * @param maintenant instant de reference, injecte pour rester testable
     */
    public void annulerParLeMembre(Instant maintenant) {
        if (!peutEtreAnnuleParLeMembre(maintenant)) {
            throw new IllegalStateException(
                    "L annulation n est plus possible a moins de 24 heures du rendez-vous (RM-11).");
        }
        transitionVers(StatutRdv.ANNULE);
        this.dateAnnulation = maintenant;
        creneau.liberer();
    }

    public boolean peutEtreAnnuleParLeMembre(Instant maintenant) {
        return statut.estEnCours()
                && !maintenant.plus(DELAI_ANNULATION).isAfter(creneau.getDebut());
    }

    private void transitionVers(StatutRdv cible) {
        if (!statut.peutPasserA(cible)) {
            throw new IllegalStateException(
                    "Transition interdite : %s vers %s (RM-10).".formatted(statut, cible));
        }
        this.statut = cible;
    }

    /** Duree totale estimee, somme des lignes (RM-09). */
    public int dureeEstimeeMinutes() {
        return lignes.stream().mapToInt(LigneRdv::dureeMinutes).sum();
    }

    /** Montant hors taxe fige a la reservation. */
    public BigDecimal montantHtva() {
        return lignes.stream().map(LigneRdv::totalHtva)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Montant taxe comprise, addition des lignes deja arrondies chacune au centime. */
    public BigDecimal montantTvac() {
        return lignes.stream().map(LigneRdv::totalTvac)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public boolean appartientA(Utilisateur candidat) {
        return membre != null && membre.equals(candidat);
    }

    public Long getId() { return id; }
    public UUID getReference() { return reference; }
    public String getNumero() { return numero; }
    public Utilisateur getMembre() { return membre; }
    public Vehicule getVehicule() { return vehicule; }
    public CreneauHoraire getCreneau() { return creneau; }
    public StatutRdv getStatut() { return statut; }
    public String getCommentaire() { return commentaire; }
    public String getMotifRefus() { return motifRefus; }
    public Instant getDateAnnulation() { return dateAnnulation; }
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