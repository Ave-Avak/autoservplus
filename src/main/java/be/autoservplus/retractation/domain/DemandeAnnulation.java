package be.autoservplus.retractation.domain;

import be.autoservplus.common.entity.BaseEntity;
import be.autoservplus.facturation.domain.Avoir;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.vente.domain.Commande;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Demande de retractation d une commande de marchandises (F30, RM-23), tranchee par
 * l administrateur.
 *
 * <p><b>Pourquoi deux temps.</b> Le controle automatique sait verifier ce que le
 * systeme connait : la commande appartient bien au demandeur, elle est payee, les
 * quatorze jours legaux courent encore, aucune demande n est deja pendante. Il ne
 * sait rien de l etat physique de la piece — montee sur le vehicule, deballee,
 * abimee. Ce constat appartient a l atelier, et c est lui qui fonde les exceptions
 * legales au droit de retractation. La demande porte donc la part automatisable de
 * la decision et laisse l autre a un humain, qui peut refuser avec motif.</p>
 *
 * <p><b>Machine a etats dans l entite</b>, comme partout dans le projet : seules
 * {@code EN_ATTENTE -> VALIDEE} et {@code EN_ATTENTE -> REFUSEE} passent, et la garde
 * vit ici plutot que dans le service — un second appelant (reprise, tache planifiee,
 * test) ne peut pas la contourner en oubliant de la recopier.</p>
 *
 * <p><b>Validation et avoir posés d un seul geste</b> ({@link #valider}) : la base
 * exige par {@code ck_demande_annulation_avoir} qu une demande validee porte son
 * avoir. Transitionner d abord puis rattacher l avoir ensuite produirait, entre les
 * deux ecritures, un etat que le CHECK refuse — l invariant impose donc une seule
 * mutation, pas deux.</p>
 *
 * <p>{@code @Version} : la validation est une ressource critique concurrente. Un
 * double-clic de l administrateur, ou deux administrateurs sur le meme dossier,
 * rembourseraient deux fois. Le verrou optimiste fait perdre le second, avant meme
 * que l index unique de l avoir n ait a trancher.</p>
 *
 * <p><b>Perimetre V1 : annulation totale.</b> Aucune ligne n est designee — la
 * demande porte sur la commande entiere. L annulation partielle par ligne (V2)
 * ajoutera une table de lignes et devra lever l unicite {@code uq_avoir_facture}.</p>
 */
@Entity
@Table(name = "demande_annulation")
@SQLRestriction("deleted_at IS NULL")
public class DemandeAnnulation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference", nullable = false, updatable = false)
    private UUID reference;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "commande_id", nullable = false, updatable = false)
    private Commande commande;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    private StatutDemandeAnnulation statut = StatutDemandeAnnulation.EN_ATTENTE;

    /**
     * Facultatif : le droit de retractation est inconditionnel, le consommateur n a
     * pas a se justifier (CDE, art. VI.47). Exiger un motif reviendrait a poser une
     * condition la ou la loi n en pose aucune.
     */
    @Column(name = "motif_membre", columnDefinition = "text")
    private String motifMembre;

    /** Renseigne surtout au refus : le membre doit connaitre le constat qu on lui oppose. */
    @Column(name = "motif_decision", columnDefinition = "text")
    private String motifDecision;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decide_par")
    private Utilisateur decidePar;

    @Column(name = "decide_le")
    private Instant decideLe;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "avoir_id")
    private Avoir avoir;

    @Column(name = "date_demande", nullable = false, updatable = false)
    private Instant dateDemande;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected DemandeAnnulation() {
        // requis par JPA
    }

    /**
     * Ouvre une demande EN_ATTENTE. Les conditions d eligibilite ne sont PAS
     * verifiees ici : elles portent sur la commande, l horloge et l historique des
     * demandes, c est-a-dire sur un contexte que l entite ne voit pas. Le service
     * les tranche avant d appeler ce constructeur.
     */
    public DemandeAnnulation(Commande commande, String motifMembre, Instant dateDemande) {
        this.reference = UUID.randomUUID();
        this.commande = Objects.requireNonNull(commande, "commande");
        this.dateDemande = Objects.requireNonNull(dateDemande, "dateDemande");
        this.motifMembre = normaliser(motifMembre);
    }

    // --- transitions -----------------------------------------------------------------

    /**
     * L administrateur accepte la retractation : la demande porte desormais l avoir
     * qui contre-passe la facture, son auteur et sa date.
     *
     * @param avoir la note de credit emise dans la meme transaction — obligatoire,
     *              {@code ck_demande_annulation_avoir} refuse une validation sans elle
     */
    public void valider(Avoir avoir, Utilisateur administrateur, Instant maintenant) {
        transitionVers(StatutDemandeAnnulation.VALIDEE);
        this.avoir = Objects.requireNonNull(avoir, "avoir");
        this.decidePar = Objects.requireNonNull(administrateur, "administrateur");
        this.decideLe = Objects.requireNonNull(maintenant, "maintenant");
    }

    /**
     * L administrateur oppose une exception au droit de retractation (piece montee,
     * deballee, abimee). Le motif est <b>obligatoire</b>, a l inverse de celui du
     * membre : c est le garage qui doit se justifier quand il refuse, pas le
     * consommateur quand il demande.
     */
    public void refuser(String motif, Utilisateur administrateur, Instant maintenant) {
        String motifNettoye = normaliser(motif);
        if (motifNettoye == null) {
            throw new IllegalArgumentException(
                    "Un refus de retractation doit etre motive.");
        }
        transitionVers(StatutDemandeAnnulation.REFUSEE);
        this.motifDecision = motifNettoye;
        this.decidePar = Objects.requireNonNull(administrateur, "administrateur");
        this.decideLe = Objects.requireNonNull(maintenant, "maintenant");
    }

    private void transitionVers(StatutDemandeAnnulation cible) {
        if (!statut.peutPasserA(cible)) {
            throw new IllegalStateException(
                    "Transition de demande d annulation interdite : %s vers %s."
                            .formatted(statut, cible));
        }
        this.statut = cible;
    }

    /** Une chaine vide ou blanche vaut absence : la colonne reste NULL, pas « ». */
    private static String normaliser(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.strip();
    }

    public boolean estEnAttente() {
        return statut == StatutDemandeAnnulation.EN_ATTENTE;
    }

    /**
     * Refuse tout de suite une demande deja tranchee, <b>avant</b> le moindre effet de
     * bord.
     *
     * <p>La garde qui fait autorite reste celle de {@link #valider} et
     * {@link #refuser} : cette methode ne redit pas la table des transitions, elle
     * s appuie sur {@link #estEnAttente}. Elle existe parce que la validation
     * contre-passe une facture et rembourse un paiement avant d en arriver a la
     * transition : sans fail-fast, un second clic de l administrateur echouerait
     * bien, mais sur « aucun paiement encaisse » — le paiement etant deja
     * REMBOURSE — au lieu de dire que la demande est deja traitee. Le resultat
     * serait le meme, le message serait faux.</p>
     */
    public void exigerEnAttente() {
        if (!estEnAttente()) {
            throw new IllegalStateException(
                    "Cette demande d annulation a deja ete tranchee : %s.".formatted(statut));
        }
    }

    public Long getId() { return id; }
    public UUID getReference() { return reference; }
    public Commande getCommande() { return commande; }
    public StatutDemandeAnnulation getStatut() { return statut; }
    public String getMotifMembre() { return motifMembre; }
    public String getMotifDecision() { return motifDecision; }
    public Utilisateur getDecidePar() { return decidePar; }
    public Instant getDecideLe() { return decideLe; }
    public Avoir getAvoir() { return avoir; }
    public Instant getDateDemande() { return dateDemande; }
    public long getVersion() { return version; }

    @Override
    public boolean equals(Object autre) {
        if (this == autre) return true;
        if (!(autre instanceof DemandeAnnulation demande)) return false;
        return id != null && id.equals(demande.id);
    }

    @Override
    public int hashCode() {
        return DemandeAnnulation.class.hashCode();
    }
}
