package be.autoservplus.intervention.service.dto;

import java.util.UUID;

/**
 * Pièce proposée au formulaire d'ajout d'une intervention (RM-14), libellé dans la langue
 * de l'écran. Le stock est affiché pour que l'atelier sache, avant d'ajouter, s'il devra
 * commander la pièce : un stock nul n'empêche pas l'ajout.
 */
public record PieceAjoutableVue(UUID reference, String libelle, int stock) {
}
