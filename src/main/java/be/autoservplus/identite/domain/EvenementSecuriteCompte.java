package be.autoservplus.identite.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;

/**
 * Un evenement de securite d un compte : second facteur active ou retire, echec,
 * verrouillage, code de secours consomme, mot de passe defini ou change.
 *
 * <p>Journal append-only, sur le patron de {@link HistoriqueStatutUtilisateur}, mais dans
 * une table a part : celle-ci consigne des <b>transitions</b> d etat, dont le CHECK exige
 * un avant et un apres differents. Un evenement de securite n a ni l un ni l autre.</p>
 *
 * <p><b>Ce que la ligne ne porte jamais</b> : secret, code saisi, adresse IP. Le fait et
 * sa date suffisent a l enquete, et un journal qui porterait un code deviendrait lui-meme
 * un moyen de s authentifier.</p>
 */
@Entity
@Table(name = "evenement_securite_compte")
@EntityListeners(AuditingEntityListener.class)
public class EvenementSecuriteCompte {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false, updatable = false)
    private Utilisateur utilisateur;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40, updatable = false)
    private TypeEvenementSecurite type;

    @Column(name = "horodatage", nullable = false, updatable = false)
    private Instant horodatage;

    /**
     * Qui a agi. Le titulaire lui-meme pour ses propres gestes. Un autre compte pour une
     * creation ou une reinitialisation. Nul quand le systeme constate (echec, verrouillage),
     * l auteur de la saisie restant inconnu. Distinguer les deux premiers cas du troisieme
     * est ce que la fiche affiche : un nul unique les confondait.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auteur_id", updatable = false)
    private Utilisateur auteur;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @CreatedBy
    @Column(name = "created_by", length = 120, updatable = false)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by", length = 120)
    private String updatedBy;

    protected EvenementSecuriteCompte() {
        // requis par JPA
    }

    public EvenementSecuriteCompte(Utilisateur utilisateur, TypeEvenementSecurite type,
                                   Instant horodatage, Utilisateur auteur) {
        this.utilisateur = Objects.requireNonNull(utilisateur, "utilisateur");
        this.type = Objects.requireNonNull(type, "type");
        this.horodatage = Objects.requireNonNull(horodatage, "horodatage");
        this.auteur = auteur;
    }

    public Long getId() { return id; }
    public Utilisateur getUtilisateur() { return utilisateur; }
    public TypeEvenementSecurite getType() { return type; }
    public Instant getHorodatage() { return horodatage; }
    public Utilisateur getAuteur() { return auteur; }
}
