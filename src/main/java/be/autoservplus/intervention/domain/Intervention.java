package be.autoservplus.intervention.domain;

import be.autoservplus.common.entity.BaseEntity;
import be.autoservplus.reservation.domain.LigneRdv;
import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.domain.Vehicule;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
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
    @Column(name = "statut", nullable = false, length = 25)
    private StatutIntervention statut = StatutIntervention.PLANIFIEE;

    @Column(name = "commentaire_admin", columnDefinition = "text")
    private String commentaireAdmin;

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
     */
    public Intervention(String numero, Rdv rdv) {
        this.reference = UUID.randomUUID();
        this.numero = Objects.requireNonNull(numero, "numero");
        this.rdv = Objects.requireNonNull(rdv, "rdv");
        this.vehicule = Objects.requireNonNull(rdv.getVehicule(), "rdv.vehicule");
        for (LigneRdv l : rdv.getLignes()) {
            this.lignes.add(new LigneIntervention(this, l.getPrestation(),
                    l.getQuantite(), l.getPrixUnitaireHtva(), l.getTauxTva()));
        }
    }

    // --- transitions ---------------------------------------------------------------

    public void demarrer(Instant maintenant) {
        transitionVers(StatutIntervention.EN_COURS);
        if (debutReel == null) {
            debutReel = Objects.requireNonNull(maintenant, "maintenant");
        }
    }

    public void mettreEnPause() {
        transitionVers(StatutIntervention.EN_PAUSE);
    }

    public void reprendre() {
        transitionVers(StatutIntervention.EN_COURS);
    }

    /**
     * Terminaison de l intervention. Si l on vient directement de PLANIFIEE
     * (raccourci express, pas de démarrage explicite), {@code debutReel} n a
     * jamais ete pose : on l aligne sur {@code finReelle} pour ne pas laisser
     * une trace incomplete.
     */
    public void terminer(Instant maintenant) {
        transitionVers(StatutIntervention.TERMINEE);
        Instant instant = Objects.requireNonNull(maintenant, "maintenant");
        if (this.debutReel == null) {
            this.debutReel = instant;
        }
        this.finReelle = instant;
    }

    /** Hook du module facturation, non declenche en V1. */
    public void marquerFacturee() {
        transitionVers(StatutIntervention.FACTUREE);
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
                prixUnitaireHtva, tauxTva);
        this.lignes.add(ligne);
        return ligne;
    }

    public LigneIntervention ajouterLignePiece(be.autoservplus.catalogue.domain.Piece piece,
                                               short quantite) {
        exigerEditable();
        LigneIntervention ligne = new LigneIntervention(this, piece, quantite);
        this.lignes.add(ligne);
        return ligne;
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

    public BigDecimal totalHtva() {
        return lignes.stream().map(LigneIntervention::totalHtva)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal totalTvac() {
        return lignes.stream().map(LigneIntervention::totalTvac)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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
