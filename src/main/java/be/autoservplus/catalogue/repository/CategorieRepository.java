package be.autoservplus.catalogue.repository;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.TypeCategorie;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategorieRepository extends JpaRepository<Categorie, Long> {

    Optional<Categorie> findByCode(String code);

    boolean existsByCode(String code);

    List<Categorie> findByTypeAndActifTrueOrderByOrdreAsc(TypeCategorie type);
}