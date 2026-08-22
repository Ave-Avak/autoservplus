package be.autoservplus.catalogue.repository;

import be.autoservplus.catalogue.domain.HistoriqueModificationCatalogue;
import be.autoservplus.catalogue.domain.TypeEntiteCatalogue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface HistoriqueModificationCatalogueRepository
        extends JpaRepository<HistoriqueModificationCatalogue, Long> {

    /**
     * Historique d un element du catalogue, du plus recent au plus ancien (A2, A5).
     * Le tri secondaire sur l id departage les lignes d une meme modification : elles
     * partagent l horodatage a la milliseconde pres (une seule lecture de l horloge),
     * et l ordre d insertion — donc l ordre des champs — fait alors foi.
     *
     * <p>JOIN FETCH sur l auteur : la vue affiche son nom pour chaque ligne, et une
     * association LAZY produirait ici un N+1 sur un journal potentiellement long.
     * LEFT car l auteur est nullable (traitement systeme, compte supprime).</p>
     */
    @Query("""
            SELECT h FROM HistoriqueModificationCatalogue h
            LEFT JOIN FETCH h.auteur
            WHERE h.typeEntite = :typeEntite AND h.entiteId = :entiteId
            ORDER BY h.horodatage DESC, h.id DESC
            """)
    List<HistoriqueModificationCatalogue> historiqueDe(
            @Param("typeEntite") TypeEntiteCatalogue typeEntite,
            @Param("entiteId") Long entiteId);
}
