package be.autoservplus.rgpd.service.dto;

/**
 * Fichier produit par l export du droit d acces (F22) : son nom et son contenu.
 *
 * <p>Le nom est calcule par le service et non par le controleur : il porte la date
 * de generation, donc l horloge injectee, donc une decision testable de facon
 * deterministe. Le controleur se contente de le poser dans l en-tete
 * {@code Content-Disposition}.
 */
public record FichierExport(String nom, byte[] contenu) {
}
