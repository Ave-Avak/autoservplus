package be.autoservplus.rgpd.repository;

import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.domain.LignePanier;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * Lecture des commandes d un membre et de leurs lignes pour l export (F22).
 *
 * <p>Deux requetes plutot qu un {@code JOIN FETCH} des lignes : la seconde ramene
 * en un seul appel les lignes de <b>toutes</b> les commandes du membre, que le
 * service regroupe ensuite en memoire. Le cout est de deux requetes quel que soit
 * le nombre de commandes — ni N+1, ni produit cartesien a dedupliquer.
 *
 * <p>La piece du catalogue n est pas jointe : le libelle et les montants sont
 * <b>figes sur la ligne</b> a l ajout au panier (RM-30), l export n a donc rien a
 * lire du catalogue courant. C est aussi ce qui evite le piege de
 * {@code CommandeRepository.lignesDe}, dont le {@code JOIN FETCH l.piece} est une
 * jointure interne : une ligne de service (colonne {@code service_id}, F12 a
 * venir) y serait silencieusement absente.
 */
public interface CommandeExportRepository extends Repository<Commande, Long> {

    @Query("""
            SELECT c FROM Commande c
            WHERE LOWER(c.membre.email) = LOWER(:email)
            ORDER BY c.dateCommande
            """)
    List<Commande> pourMembre(@Param("email") String email);

    /** Lignes de plusieurs commandes d un coup. L appelant garantit une liste non vide. */
    @Query("""
            SELECT l FROM LignePanier l
            WHERE l.commande IN :commandes
            ORDER BY l.id
            """)
    List<LignePanier> lignesDe(@Param("commandes") Collection<Commande> commandes);
}
