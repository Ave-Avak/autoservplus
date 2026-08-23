package be.autoservplus.messagerie.domain;

import be.autoservplus.common.entity.BaseEntity;
import be.autoservplus.identite.domain.Utilisateur;
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
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.Objects;

/**
 * Message d un fil de discussion (BL-5). Table {@code message} du socle V7.
 *
 * <p><b>Immuable une fois envoye</b>, a l exception du drapeau de lecture. Un fil de
 * discussion est une trace de ce qui a ete dit : autoriser la reecriture permettrait
 * de modifier apres coup un accord donne sur un devis ou une consigne de reparation.
 * Le correctif normal est un nouveau message, pas la reecriture du precedent.</p>
 *
 * <p>{@code lu} vaut faux a l envoi et ne concerne que le <b>destinataire</b> : c est
 * le cote oppose a {@link #role} qui le passera a vrai. Un expediteur n a pas a lire
 * son propre message.</p>
 */
@Entity
@Table(name = "message")
@SQLRestriction("deleted_at IS NULL")
public class Message extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false, updatable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "expediteur_id", nullable = false, updatable = false)
    private Utilisateur expediteur;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_expediteur", length = 20, nullable = false, updatable = false)
    private RoleExpediteur role;

    @Column(name = "corps", nullable = false, updatable = false)
    private String corps;

    @Column(name = "lu", nullable = false)
    private boolean lu;

    @Column(name = "date_envoi", nullable = false, updatable = false)
    private Instant dateEnvoi;

    protected Message() {
        // requis par JPA
    }

    /**
     * @throws IllegalArgumentException corps vide — un message sans texte n a rien a
     *                                  transmettre et la colonne est {@code NOT NULL}
     */
    public Message(Conversation conversation, Utilisateur expediteur, RoleExpediteur role,
                   String corps, Instant maintenant) {
        this.conversation = Objects.requireNonNull(conversation, "conversation");
        this.expediteur = Objects.requireNonNull(expediteur, "expediteur");
        this.role = Objects.requireNonNull(role, "role");
        Objects.requireNonNull(corps, "corps");
        String texte = corps.strip();
        if (texte.isEmpty()) {
            throw new IllegalArgumentException("Un message ne peut pas etre vide.");
        }
        this.corps = texte;
        this.lu = false;
        this.dateEnvoi = Objects.requireNonNull(maintenant, "maintenant");
    }

    /**
     * Marque le message lu par le camp oppose. <b>Sans effet si c est l expediteur qui
     * le relit</b> : le compteur de non-lus doit compter ce que l autre n a pas encore
     * vu, pas ce que son auteur revisite.
     */
    public void marquerLuPar(RoleExpediteur lecteur) {
        if (lecteur != role) {
            this.lu = true;
        }
    }

    public boolean estNonLuPar(RoleExpediteur lecteur) {
        return !lu && lecteur != role;
    }

    public Long getId() { return id; }
    public Conversation getConversation() { return conversation; }
    public Utilisateur getExpediteur() { return expediteur; }
    public RoleExpediteur getRole() { return role; }
    public String getCorps() { return corps; }
    public boolean isLu() { return lu; }
    public Instant getDateEnvoi() { return dateEnvoi; }
}
