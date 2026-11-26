package be.autoservplus.intervention.service.dto;

import be.autoservplus.intervention.domain.StatutIntervention;
import be.autoservplus.intervention.domain.UniteLigne;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Carnet d'entretien d'un véhicule, tel que son propriétaire l'exporte (F42).
 *
 * <p>Même périmètre que l'historique à l'écran ({@link HistoriqueVehiculeMembre}) : les
 * lignes facturables seules, aucune note interne de l'atelier. Le carnet ajoute ce qu'un
 * autre système attend pour reprendre l'historique : le numéro de châssis, le kilométrage
 * courant, les pièces incluses dans un forfait et les totaux hors TVA et TVA comprise.
 * Une intervention annulée n'y figure pas : aucun travail n'a été fait.</p>
 *
 * <p>Le kilométrage est celui du véhicule aujourd'hui. Aucune intervention ne garde le
 * relevé du jour où elle a eu lieu.</p>
 */
public record CarnetEntretien(
        UUID vehiculeReference,
        String plaque,
        String marque,
        String modele,
        Short annee,
        String numeroChassis,
        Integer kilometrage,
        List<Entree> interventions) {

    /**
     * Une intervention du carnet, de la plus ancienne à la plus récente.
     *
     * @param debut début réel, à défaut celui du rendez-vous ou l'ouverture du dossier
     * @param fin   fin réelle, présente pour une intervention terminée seulement
     */
    public record Entree(
            UUID reference,
            String numero,
            StatutIntervention statut,
            OffsetDateTime debut,
            OffsetDateTime fin,
            BigDecimal totalHtva,
            BigDecimal totalTvac,
            List<Ligne> lignes) {
    }

    /**
     * Prestation, pièce, ou pièce comprise dans un forfait, sans prix à la ligne : le carnet
     * porte le total de l'intervention, comme l'écran.
     *
     * @param referenceFabricant présente pour une pièce, absente pour une prestation
     */
    public record Ligne(Nature nature, String libelle, String referenceFabricant,
                        BigDecimal quantite, UniteLigne unite) {
    }

    public enum Nature {
        PRESTATION,
        PIECE,
        PIECE_INCLUSE
    }
}
