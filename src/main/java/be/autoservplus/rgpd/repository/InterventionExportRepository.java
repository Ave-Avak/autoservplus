package be.autoservplus.rgpd.repository;

import be.autoservplus.intervention.domain.Intervention;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Lecture des interventions d un membre pour l export (F22).
 *
 * <p>Le rattachement passe par le <b>vehicule</b> et non par le rendez-vous :
 * {@code rdv_id} est nullable en base (une intervention peut naitre d une entree
 * directe au garage), un filtre sur le RDV laisserait donc echapper des dossiers
 * du membre le jour ou ce chemin de creation existera. Le vehicule, lui, porte
 * toujours son proprietaire.
 *
 * <p>Le RDV et les lignes sont charges d avance ({@code open-in-view} desactive) ;
 * le RDV en {@code LEFT JOIN} pour la meme raison de nullabilite.
 */
public interface InterventionExportRepository extends Repository<Intervention, Long> {

    @Query("""
            SELECT DISTINCT i FROM Intervention i
            JOIN FETCH i.vehicule v
            LEFT JOIN FETCH i.rdv
            LEFT JOIN FETCH i.lignes
            WHERE LOWER(v.membre.email) = LOWER(:email)
            ORDER BY i.id
            """)
    List<Intervention> pourMembre(@Param("email") String email);
}
