package be.autoservplus.identite.repository;

import be.autoservplus.identite.domain.HistoriqueStatutUtilisateur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface HistoriqueStatutUtilisateurRepository
        extends JpaRepository<HistoriqueStatutUtilisateur, Long> {

    /**
     * Chronologie d un compte, du plus recent au plus ancien.
     *
     * <p>L auteur est charge en JOIN FETCH : l ecran le nomme pour chaque ligne, et
     * le laisser paresseux produirait une requete par ligne — le defaut N+1 sur un
     * ecran dont l interet est precisement de lister plusieurs evenements.</p>
     */
    @Query("""
           select h from HistoriqueStatutUtilisateur h
           left join fetch h.auteur
           where h.utilisateur.id = :compte
           order by h.horodatage desc, h.id desc
           """)
    List<HistoriqueStatutUtilisateur> historiqueDe(@Param("compte") Long compte);
}
