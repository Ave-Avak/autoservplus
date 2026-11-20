package be.autoservplus.vente.domain;

import be.autoservplus.identite.domain.Utilisateur;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Une modification d'une commande par le garage (V60), aujourd'hui la seule date de
 * remise des pièces. Journal append-only : écrit dans la transaction de la modification,
 * jamais modifié ensuite.
 */
@Entity
@Table(name = "historique_modification_commande")
@EntityListeners(AuditingEntityListener.class)
public class ModificationCommande {

    /** Champ modifié, tenu d'accord avec {@code ck_histo_commande_champ}. */
    public enum Champ { DATE_REMISE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "commande_id", nullable = false, updatable = false)
    private Commande commande;

    @Enumerated(EnumType.STRING)
    @Column(name = "champ", nullable = false, length = 20, updatable = false)
    private Champ champ;

    @Column(name = "valeur_avant", updatable = false)
    private String valeurAvant;

    @Column(name = "valeur_apres", updatable = false)
    private String valeurApres;

    @Column(name = "horodatage", nullable = false, updatable = false)
    private Instant horodatage;

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

    protected ModificationCommande() {
        // requis par JPA
    }

    /** Saisie ou correction de la date de remise. Les dates sont gardées au format ISO. */
    public static ModificationCommande dateRemise(Commande commande, LocalDate avant, LocalDate apres,
                                                  Instant horodatage, Utilisateur auteur) {
        if (Objects.equals(avant, apres)) {
            throw new IllegalArgumentException("La date de remise n'a pas changé.");
        }
        ModificationCommande ligne = new ModificationCommande();
        ligne.commande = Objects.requireNonNull(commande, "commande");
        ligne.champ = Champ.DATE_REMISE;
        ligne.valeurAvant = avant == null ? null : avant.toString();
        ligne.valeurApres = apres == null ? null : apres.toString();
        ligne.horodatage = Objects.requireNonNull(horodatage, "horodatage");
        ligne.auteur = Objects.requireNonNull(auteur, "auteur");
        return ligne;
    }

    public Commande getCommande() { return commande; }
    public Champ getChamp() { return champ; }
    public String getValeurAvant() { return valeurAvant; }
    public String getValeurApres() { return valeurApres; }
    public Instant getHorodatage() { return horodatage; }
    public Utilisateur getAuteur() { return auteur; }
}
