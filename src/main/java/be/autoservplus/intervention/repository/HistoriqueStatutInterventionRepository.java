package be.autoservplus.intervention.repository;

import be.autoservplus.intervention.domain.HistoriqueStatutIntervention;
import be.autoservplus.intervention.domain.Intervention;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HistoriqueStatutInterventionRepository
        extends JpaRepository<HistoriqueStatutIntervention, Long> {

    /**
     * Chronologie d une intervention, du plus ancien au plus recent. Le tri secondaire
     * sur l id departage deux transitions au meme instant (horloge figee en test,
     * transitions dans la meme milliseconde) : l ordre d insertion fait foi.
     *
     * <p>Pas de JOIN FETCH sur l auteur : la vue membre ne l expose pas (les statuts
     * sont des enums, l intervention est deja chargee par l appelant), la requete
     * derivee suffit sans N+1.</p>
     */
    List<HistoriqueStatutIntervention> findByInterventionOrderByHorodatageAscIdAsc(
            Intervention intervention);
}
