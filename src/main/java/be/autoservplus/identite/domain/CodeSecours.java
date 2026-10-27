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
 * Code de secours du second facteur, a usage unique.
 *
 * <p><b>Jamais supprime</b> : un code consomme garde sa date de consommation, un code
 * remplace par une regeneration garde sa date d invalidation. La ligne est la trace de son
 * usage, supprimer un code consomme effacerait la preuve qu il a servi, et donc la
 * reponse a la question « qui s est connecte sans telephone, et quand ».</p>
 *
 * <p>Empreinte BCrypt de cout 10 et non 12 comme les mots de passe : une connexion par
 * code de secours compare la saisie a jusqu a huit empreintes, soit deux secondes au cout
 * 12. Le code porte pres de 40 bits d entropie tires au hasard, et le verrouillage
 * progressif borne les essais : le cout de l empreinte n est pas ce qui le protege.</p>
 */
@Entity
@Table(name = "code_secours")
@EntityListeners(AuditingEntityListener.class)
public class CodeSecours {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false, updatable = false)
    private Utilisateur utilisateur;

    @Column(name = "empreinte", nullable = false, length = 60, updatable = false)
    private String empreinte;

    @Column(name = "consomme_le")
    private Instant consommeLe;

    @Column(name = "invalide_le")
    private Instant invalideLe;

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

    protected CodeSecours() {
        // requis par JPA
    }

    public CodeSecours(Utilisateur utilisateur, String empreinte) {
        this.utilisateur = Objects.requireNonNull(utilisateur, "utilisateur");
        this.empreinte = Objects.requireNonNull(empreinte, "empreinte");
    }

    public boolean estUtilisable() {
        return consommeLe == null && invalideLe == null;
    }

    /** Un code ne sert qu une fois : le consommer deux fois est un defaut, pas un cas. */
    public void consommer(Instant maintenant) {
        exigerUtilisable();
        this.consommeLe = Objects.requireNonNull(maintenant, "maintenant");
    }

    public void invalider(Instant maintenant) {
        exigerUtilisable();
        this.invalideLe = Objects.requireNonNull(maintenant, "maintenant");
    }

    private void exigerUtilisable() {
        if (!estUtilisable()) {
            throw new IllegalStateException("Code de secours deja consomme ou remplace.");
        }
    }

    public Long getId() { return id; }
    public Utilisateur getUtilisateur() { return utilisateur; }
    public String getEmpreinte() { return empreinte; }
    public Instant getConsommeLe() { return consommeLe; }
    public Instant getInvalideLe() { return invalideLe; }
}
