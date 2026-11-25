package be.autoservplus.intervention.repository;

import be.autoservplus.intervention.domain.LigneIntervention;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Enregistrement explicite d'une ligne nouvelle avant l'écriture de son intervention. Les
 * lignes se créent et se modifient par l'intervention, ce dépôt ne sert qu'à cela.
 */
public interface LigneInterventionRepository extends JpaRepository<LigneIntervention, Long> {
}
