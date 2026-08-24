package be.autoservplus.legal.repository;

import be.autoservplus.identite.domain.Langue;
import be.autoservplus.legal.domain.TypeDocumentVersionne;
import be.autoservplus.legal.domain.VersionDocument;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface VersionDocumentRepository extends JpaRepository<VersionDocument, Long> {

    /**
     * Identifiants des versions en vigueur d un document, la plus recente d abord.
     *
     * <p>Ne rend que la colonne {@code version} : c est la seule donnee necessaire a
     * l ecriture d une preuve de consentement, et charger l entiere entite y tirerait
     * trois textes complets a chaque commande.</p>
     *
     * <p>Le filtre porte sur {@code date_effet <= maintenant} et pas seulement sur
     * {@code actif} : une version peut etre publiee a l avance — c est meme la pratique
     * attendue quand un changement de conditions doit etre annonce avant de
     * s appliquer. Sans ce filtre, elle prendrait effet a l instant de son insertion.</p>
     */
    @Query("""
            SELECT v.version FROM VersionDocument v
            WHERE v.typeDocument = :type
              AND v.actif = true
              AND v.dateEffet <= :maintenant
            GROUP BY v.version, v.dateEffet
            ORDER BY v.dateEffet DESC, v.version DESC
            """)
    List<String> versionsEnVigueur(@Param("type") TypeDocumentVersionne type,
                                   @Param("maintenant") Instant maintenant,
                                   Pageable limite);

    Optional<VersionDocument> findByTypeDocumentAndVersionAndLangue(TypeDocumentVersionne type,
                                                                    String version,
                                                                    Langue langue);

    /**
     * Toutes les langues d une version donnee — l archive doit pouvoir servir le texte
     * neerlandais a qui l a accepte, quelle que soit la langue de sa session du jour.
     */
    List<VersionDocument> findByTypeDocumentAndVersionOrderByLangue(TypeDocumentVersionne type,
                                                                     String version);
}
