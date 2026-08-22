package be.autoservplus.rgpd.repository;

import be.autoservplus.identite.domain.Consentement;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Lecture des preuves de consentement d un membre pour l export (F22).
 *
 * <p>Tous types de documents confondus, dans l ordre chronologique : la table est
 * append-only, un retrait s exprime par une nouvelle ligne {@code accorde = false},
 * et c est la <b>derniere</b> ligne d un type donne qui dit l etat courant. Un tri
 * stable est donc necessaire a la lecture, pas seulement confortable.
 *
 * <p>{@code ConsentementRepository} du module identite filtre par type de document
 * (usage CGV a la commande) et ne convient pas ici : l export restitue l historique
 * entier.
 */
public interface ConsentementExportRepository extends Repository<Consentement, Long> {

    @Query("""
            SELECT c FROM Consentement c
            WHERE LOWER(c.utilisateur.email) = LOWER(:email)
            ORDER BY c.dateConsentement, c.id
            """)
    List<Consentement> pourMembre(@Param("email") String email);
}
