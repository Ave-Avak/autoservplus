package be.autoservplus.rgpd.repository;

import be.autoservplus.facturation.domain.Facture;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Lecture des factures d un membre pour l export (F22).
 *
 * <p>{@code LEFT JOIN FETCH} sur la commande, pour deux raisons distinctes. Le
 * {@code FETCH} d abord : {@code open-in-view} est desactive et l objet de
 * transfert lit le numero de la commande liee — sans lui, une initialisation
 * paresseuse par facture, donc un N+1 sur un membre fidele. Le {@code LEFT}
 * ensuite : la source d une facture est une commande <b>ou</b> une intervention
 * (CHECK {@code ck_facture_origine_unique}), et une jointure interne ferait
 * disparaitre en silence les futures factures d intervention (RM-17) de l export
 * d une personne qui les detient pourtant. Meme piege que celui deja consigne
 * dans {@code CommandeExportRepository}.
 *
 * <p>{@code FactureRepository.facturesDuMembre} du module facturation ne convient
 * pas ici : sa jointure sur la commande est <b>interne</b> — elle sert un ecran
 * qui n affiche que des factures de commande — et son ordre est decroissant, pour
 * un historique. L export, lui, restitue chronologiquement, comme toutes ses
 * autres sections.
 *
 * <p>Aucune suppression logique a contourner : {@code facture} ne porte pas de
 * {@code deleted_at}, un document comptable ne se supprime pas.
 */
public interface FactureExportRepository extends Repository<Facture, Long> {

    @Query("""
            SELECT f FROM Facture f
            LEFT JOIN FETCH f.commande
            WHERE LOWER(f.membre.email) = LOWER(:email)
            ORDER BY f.dateEmission, f.id
            """)
    List<Facture> pourMembre(@Param("email") String email);
}
