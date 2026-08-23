package be.autoservplus.notification.domain;

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
 * Notification in-app adressee a un membre (BL-6). Table {@code notification} du
 * socle V7, restee vide jusqu ici.
 *
 * <p><b>Append-only du point de vue du contenu.</b> Le type, le membre, l argument et
 * la date d envoi sont poses a la creation et ne changent plus : une notification
 * relate un fait passe, le reecrire reviendrait a falsifier ce qui a ete annonce.
 * Seule la lecture evolue ({@link #marquerLue}), et une seule fois.</p>
 *
 * <p><b>Aucune donnee sensible.</b> Le corps ne recoit qu un <b>numero metier</b>
 * (rendez-vous, commande, intervention, avoir) — jamais un montant, une adresse, une
 * plaque ni un motif de refus redige. Une notification est lue dans un bandeau, parfois
 * a l ecran d un tiers ; elle dit qu il s est passe quelque chose et ou le consulter,
 * pas quoi. Le detail reste derriere l authentification, sur l ecran concerne.</p>
 *
 * <p>La suppression logique de {@link BaseEntity} est filtree par
 * {@code @SQLRestriction}, comme partout dans le projet.</p>
 */
@Entity
@Table(name = "notification")
@SQLRestriction("deleted_at IS NULL")
public class Notification extends BaseEntity {

    /** Longueur de la colonne {@code titre} du socle V7 : la trace y est tronquee. */
    private static final int LONGUEUR_TITRE = 150;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "membre_id", nullable = false, updatable = false)
    private Utilisateur membre;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 40, nullable = false, updatable = false)
    private TypeNotification type;

    @Column(name = "titre", length = LONGUEUR_TITRE, nullable = false, updatable = false)
    private String titre;

    /**
     * Argument metier du message (numero de commande, de rendez-vous…), stocke dans la
     * colonne {@code corps} du socle. Ce n est pas une phrase : le texte affiche est
     * resolu a la lecture depuis {@link TypeNotification}, dans la langue du lecteur.
     */
    @Column(name = "corps", nullable = false, updatable = false)
    private String corps;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", length = 20, nullable = false)
    private StatutNotification statut;

    @Enumerated(EnumType.STRING)
    @Column(name = "canal", length = 20, nullable = false, updatable = false)
    private CanalNotification canal;

    @Column(name = "date_envoi")
    private Instant dateEnvoi;

    @Column(name = "date_lecture")
    private Instant dateLecture;

    protected Notification() {
        // requis par JPA
    }

    /**
     * Cree une notification applicative non lue.
     *
     * @param traceFr libelle francais conserve dans la colonne {@code titre}, tronque
     *                a la longueur du socle ; sert de trace lisible en base, pas
     *                d affichage
     * @param argument numero metier passe au message i18n ; jamais de donnee sensible
     */
    public Notification(Utilisateur membre, TypeNotification type, String traceFr,
                        String argument, Instant maintenant) {
        this.membre = Objects.requireNonNull(membre, "membre");
        this.type = Objects.requireNonNull(type, "type");
        this.corps = Objects.requireNonNull(argument, "argument");
        this.titre = tronquer(Objects.requireNonNull(traceFr, "traceFr"));
        this.statut = StatutNotification.NON_LUE;
        this.canal = CanalNotification.APPLICATION;
        this.dateEnvoi = Objects.requireNonNull(maintenant, "maintenant");
    }

    /**
     * Marque la notification lue. <b>Idempotent</b> : un second appel ne deplace pas la
     * date de lecture. Le membre peut rouvrir sa liste ou rejouer le formulaire ; la
     * premiere lecture est celle qui compte, et l ecraser fausserait la seule donnee
     * temporelle de la ligne.
     */
    public void marquerLue(Instant maintenant) {
        if (statut == StatutNotification.NON_LUE) {
            this.statut = StatutNotification.LUE;
            this.dateLecture = Objects.requireNonNull(maintenant, "maintenant");
        }
    }

    public boolean estNonLue() {
        return statut == StatutNotification.NON_LUE;
    }

    private static String tronquer(String valeur) {
        return valeur.length() <= LONGUEUR_TITRE ? valeur : valeur.substring(0, LONGUEUR_TITRE);
    }

    public Long getId() { return id; }
    public Utilisateur getMembre() { return membre; }
    public TypeNotification getType() { return type; }
    public String getTitre() { return titre; }
    public String getCorps() { return corps; }
    public StatutNotification getStatut() { return statut; }
    public CanalNotification getCanal() { return canal; }
    public Instant getDateEnvoi() { return dateEnvoi; }
    public Instant getDateLecture() { return dateLecture; }
}
