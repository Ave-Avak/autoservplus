package be.autoservplus.legal.domain;

import be.autoservplus.identite.domain.Langue;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Texte engageant <b>gele</b> d une version de document, dans une langue (F24).
 *
 * <p><b>Archive, pas contenu editorial.</b> Une ligne s ecrit une fois et ne se
 * modifie jamais : aucun setter, aucun soft delete. Corriger une clause ne se fait pas
 * en retouchant cette ligne mais en publiant une version de plus — sinon les preuves
 * d acceptation deja enregistrees se mettraient a designer un texte que personne n a
 * lu, ce qui est exactement le defaut que F24 corrige.</p>
 *
 * <p><b>Une ligne par langue, un numero de version pour les trois.</b> Le membre
 * neerlandophone a accepte le texte neerlandais ; lui opposer la version francaise
 * reviendrait a lui opposer un document qu il n a jamais vu. La version est donc un
 * jeu de trois textes publies ensemble, pas un texte unique traduit a la volee.</p>
 *
 * <p><b>Ce que le contenu ne contient pas</b> : ni l identite du garage, ni le registre
 * des traitements. Tous deux restent resolus a chaque rendu (configuration
 * {@code autoservplus.garage.*} et {@code CatalogueTraitements}) parce qu ils
 * <i>informent</i> sans engager — un demenagement du garage ne doit pas invalider un
 * consentement, et le registre publie doit rester le meme objet que celui joint a
 * l export RGPD de l article 15.</p>
 */
@Entity
@Table(name = "version_document")
@EntityListeners(AuditingEntityListener.class)
public class VersionDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_document", nullable = false, length = 30, updatable = false)
    private TypeDocumentVersionne typeDocument;

    @Column(name = "version", nullable = false, length = 20, updatable = false)
    private String version;

    @Enumerated(EnumType.STRING)
    @Column(name = "langue", nullable = false, length = 2, updatable = false)
    private Langue langue;

    @Column(name = "date_effet", nullable = false, updatable = false)
    private Instant dateEffet;

    @Column(name = "contenu", nullable = false, updatable = false, columnDefinition = "text")
    private String contenu;

    @Column(name = "empreinte", nullable = false, length = 64, updatable = false)
    private String empreinte;

    @Column(name = "actif", nullable = false)
    private boolean actif;

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

    protected VersionDocument() {
        // requis par JPA
    }

    public Long getId() { return id; }
    public TypeDocumentVersionne getTypeDocument() { return typeDocument; }
    public String getVersion() { return version; }
    public Langue getLangue() { return langue; }
    public Instant getDateEffet() { return dateEffet; }
    public String getContenu() { return contenu; }
    public String getEmpreinte() { return empreinte; }
    public boolean isActif() { return actif; }

    @Override
    public boolean equals(Object autre) {
        if (this == autre) return true;
        if (!(autre instanceof VersionDocument version)) return false;
        return id != null && id.equals(version.id);
    }

    @Override
    public int hashCode() {
        return VersionDocument.class.hashCode();
    }
}
