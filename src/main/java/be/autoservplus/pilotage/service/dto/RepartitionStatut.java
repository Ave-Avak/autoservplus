package be.autoservplus.pilotage.service.dto;

/**
 * Nombre de rendez-vous pour un statut donne (BL-1).
 *
 * <p>Le statut voyage en chaine et non en enum : le gabarit compose la cle i18n a
 * partir de lui, comme il le fait deja pour les statuts de commande.</p>
 */
public record RepartitionStatut(String statut, long nombre) {
}
