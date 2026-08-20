package be.autoservplus.reservation.repository;

import be.autoservplus.reservation.domain.ParametreAtelier;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParametreAtelierRepository extends JpaRepository<ParametreAtelier, Short> {

    /**
     * La ligne unique de parametres. Son absence est une corruption de schema, pas un
     * cas metier : la migration V13 la cree et rien ne permet de la supprimer.
     */
    default ParametreAtelier courants() {
        return findById(ParametreAtelier.IDENTIFIANT_UNIQUE)
                .orElseThrow(() -> new IllegalStateException(
                        "La ligne de parametres d atelier est absente (migration V13)."));
    }
}