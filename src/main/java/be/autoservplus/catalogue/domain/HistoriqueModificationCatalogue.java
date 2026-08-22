package be.autoservplus.catalogue.domain;

import be.autoservplus.identite.domain.Utilisateur;
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
 * Modification d un seul champ d un element du catalogue, telle qu elle s est
 * produite (A2, A5 : « les modifications sont historisees — qui, quand, quoi »).
 *
 * <p>Le grain est le <b>champ</b>, pas l enregistrement : changer le prix et la duree
 * d une prestation ecrit deux lignes. C est ce qui rend le « quoi » requetable — la
 * question « qui a touche au prix de cette piece ? » se lit sans comparer deux photos
 * completes de l entite. Le corollaire tient aussi : une modification qui ne change
 * aucune valeur n ecrit aucune ligne.</p>
 *
 * <p>Journal append-only ecrit dans la meme transaction que la modification qu il
 * trace, jamais modifie ensuite, sans suppression logique — meme precedent que
 * {@code HistoriqueStatutIntervention}. Il n herite donc pas de {@code BaseEntity}
 * (qui porte {@code deleted_at}), seulement de ses quatre colonnes d audit.</p>
 *
 * <p>La cible est designee par le couple {@code (typeEntite, entiteId)} plutot que par
 * une association JPA : la colonne est polymorphe, et A3/A6 autorisent la suppression
 * physique d un element jamais reference (RM-29) — une association obligerait a choisir
 * entre bloquer la suppression et effacer la trace de ce qui a ete supprime.</p>
 *
 * <p>{@code auteur} est nullable : la trace survit a la disparition du compte
 * (FK {@code ON DELETE SET NULL}) et un traitement sans utilisateur authentifie doit
 * pouvoir journaliser malgre tout.</p>
 */
@Entity
@Table(name = "historique_modification_catalogue")
@EntityListeners(AuditingEntityListener.class)
public class HistoriqueModificationCatalogue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type_entite", nullable = false, length = 20, updatable = false)
    private TypeEntiteCatalogue typeEntite;

    @NotNull
    @Column(name = "entite_id", nullable = false, updatable = false)
    private Long entiteId;

    @NotNull
    @Column(name = "champ_modifie", nullable = false, length = 60, updatable = false)
    private String champModifie;

    @Column(name = "valeur_avant", columnDefinition = "text", updatable = false)
    private String valeurAvant;

    @Column(name = "valeur_apres", columnDefinition = "text", updatable = false)
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

    protected HistoriqueModificationCatalogue() {
        // requis par JPA
    }

    public HistoriqueModificationCatalogue(TypeEntiteCatalogue typeEntite,
                                           Long entiteId,
                                           String champModifie,
                                           String valeurAvant,
                                           String valeurApres,
                                           Instant horodatage,
                                           Utilisateur auteur) {
        this.typeEntite = Objects.requireNonNull(typeEntite, "typeEntite");
        this.entiteId = Objects.requireNonNull(entiteId, "entiteId");
        this.champModifie = Objects.requireNonNull(champModifie, "champModifie");
        this.valeurAvant = valeurAvant;
        this.valeurApres = valeurApres;
        this.horodatage = Objects.requireNonNull(horodatage, "horodatage");
        this.auteur = auteur;
    }

    public Long getId() { return id; }
    public TypeEntiteCatalogue getTypeEntite() { return typeEntite; }
    public Long getEntiteId() { return entiteId; }
    public String getChampModifie() { return champModifie; }
    public String getValeurAvant() { return valeurAvant; }
    public String getValeurApres() { return valeurApres; }
    public Instant getHorodatage() { return horodatage; }
    public Utilisateur getAuteur() { return auteur; }

    @Override
    public boolean equals(Object autre) {
        if (this == autre) return true;
        if (!(autre instanceof HistoriqueModificationCatalogue historique)) return false;
        return id != null && id.equals(historique.id);
    }

    @Override
    public int hashCode() {
        return HistoriqueModificationCatalogue.class.hashCode();
    }
}
