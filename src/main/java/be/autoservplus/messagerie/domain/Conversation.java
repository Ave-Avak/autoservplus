package be.autoservplus.messagerie.domain;

import be.autoservplus.common.entity.BaseEntity;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.intervention.domain.Intervention;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Fil de discussion entre un membre et l administration du garage (BL-5). Table
 * {@code conversation} du socle V7, restee vide jusqu ici.
 *
 * <p><b>Membre ↔ garage, jamais membre ↔ membre.</b> Le schema le dit deja : un seul
 * {@code membre_id}, et le camp d en face est designe par un role
 * ({@link RoleExpediteur#ADMINISTRATEUR}) et non par un second titulaire. Il n existe
 * donc aucun chemin par lequel deux clients se parleraient, et rien a bloquer entre
 * eux.</p>
 *
 * <p><b>Rattachement a une intervention, facultatif.</b> {@code intervention_id} est
 * la seule accroche prevue au socle et elle est nullable : un fil peut donc porter sur
 * des travaux precis ou rester libre. <b>Aucun rattachement a un rendez-vous ni a une
 * commande</b> — le schema n a pas ces colonnes, et le membre qui veut parler d une
 * commande ouvre un fil libre en citant sa reference dans le message. Ajouter ces
 * accroches supposerait une migration et un ecran qui fasse choisir le contexte
 * (evolution documentee, arbitree en faveur de l option sans migration).</p>
 *
 * <p><b>Cloture reversible.</b> {@code cloturee} ferme le fil sans le supprimer : la
 * trace reste lisible, et le garage peut rouvrir si le sujet revient. Un fil clos
 * n accepte plus de message — la garde vit ici et non dans le service, pour qu aucun
 * appelant ne puisse l oublier.</p>
 */
@Entity
@Table(name = "conversation")
@SQLRestriction("deleted_at IS NULL")
public class Conversation extends BaseEntity {

    /** Longueur de la colonne {@code sujet} du socle V7. */
    public static final int LONGUEUR_SUJET = 150;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference", nullable = false, updatable = false)
    private UUID reference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "membre_id", nullable = false, updatable = false)
    private Utilisateur membre;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "intervention_id", updatable = false)
    private Intervention intervention;

    @Column(name = "sujet", length = LONGUEUR_SUJET, nullable = false, updatable = false)
    private String sujet;

    @Column(name = "cloturee", nullable = false)
    private boolean cloturee;

    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("dateEnvoi ASC, id ASC")
    private List<Message> messages = new ArrayList<>();

    protected Conversation() {
        // requis par JPA
    }

    /**
     * Ouvre un fil au nom du membre.
     *
     * @param intervention travaux concernes, ou {@code null} pour un fil libre
     * @throws IllegalArgumentException sujet vide
     */
    public Conversation(Utilisateur membre, Intervention intervention, String sujet) {
        this.membre = Objects.requireNonNull(membre, "membre");
        Objects.requireNonNull(sujet, "sujet");
        String titre = sujet.strip();
        if (titre.isEmpty()) {
            throw new IllegalArgumentException("Le sujet est obligatoire.");
        }
        this.sujet = titre.length() <= LONGUEUR_SUJET ? titre : titre.substring(0, LONGUEUR_SUJET);
        this.intervention = intervention;
        this.reference = UUID.randomUUID();
        this.cloturee = false;
    }

    /**
     * Ajoute un message au fil.
     *
     * @throws IllegalStateException fil clos — rouvrir est un geste explicite du
     *                               garage, pas un effet de bord de la reponse
     */
    public Message ajouter(Utilisateur expediteur, RoleExpediteur role, String corps,
                           Instant maintenant) {
        if (cloturee) {
            throw new IllegalStateException("Ce fil de discussion est clôturé.");
        }
        Message message = new Message(this, expediteur, role, corps, maintenant);
        messages.add(message);
        return message;
    }

    /** Marque lus les messages venus du camp oppose au lecteur. */
    public void marquerLuPar(RoleExpediteur lecteur) {
        messages.forEach(message -> message.marquerLuPar(lecteur));
    }

    public long nombreNonLusPar(RoleExpediteur lecteur) {
        return messages.stream().filter(message -> message.estNonLuPar(lecteur)).count();
    }

    public void cloturer() {
        this.cloturee = true;
    }

    public void rouvrir() {
        this.cloturee = false;
    }

    public boolean appartientA(String email) {
        return membre.getEmail().equalsIgnoreCase(email);
    }

    public Long getId() { return id; }
    public UUID getReference() { return reference; }
    public Utilisateur getMembre() { return membre; }
    public Intervention getIntervention() { return intervention; }
    public String getSujet() { return sujet; }
    public boolean isCloturee() { return cloturee; }
    public List<Message> getMessages() { return List.copyOf(messages); }
}
