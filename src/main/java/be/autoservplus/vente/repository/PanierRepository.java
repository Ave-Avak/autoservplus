package be.autoservplus.vente.repository;

import be.autoservplus.vente.domain.Panier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Accès au panier en cours d'un membre. RM-19 garantit qu'il y en a au plus un :
 * l'index unique partiel {@code uq_panier_membre_actif} (membre_id, hors soft
 * delete) rend un doublon impossible en base, la requête peut donc rendre un
 * {@link Optional} sans arbitrage.
 */
public interface PanierRepository extends JpaRepository<Panier, Long> {

    /**
     * Panier du membre avec lignes et pièces chargées d'un coup : l'écran du panier
     * lit chaque ligne et le drapeau {@code actif} de sa pièce (contrainte F13,
     * signalement des pièces désactivées), un fetch
     * paresseux déclencherait une requête par ligne (problème N+1).
     */
    @Query("""
            SELECT DISTINCT p FROM Panier p
            JOIN FETCH p.membre
            LEFT JOIN FETCH p.lignes l LEFT JOIN FETCH l.piece
            WHERE p.membre.email = :email
            """)
    Optional<Panier> findByMembreEmail(@Param("email") String email);

    /**
     * Somme des quantités du panier du membre, pour le compteur d'en-tête. Une
     * agrégation plutôt qu'un chargement complet : le compteur s'affiche sur chaque
     * page du catalogue, il ne doit pas matérialiser les lignes.
     */
    @Query("""
            SELECT COALESCE(SUM(l.quantite), 0) FROM Panier p JOIN p.lignes l
            WHERE p.membre.email = :email
            """)
    int nombreArticles(@Param("email") String email);
}
