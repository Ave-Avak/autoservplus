package be.autoservplus.notification.service.dto;

/**
 * Vue d une notification destinee a l ecran du membre (BL-6).
 *
 * <p>Le titre et le corps arrivent <b>deja resolus</b> dans la langue du lecteur : le
 * texte n existe pas en base, il est rendu a la lecture depuis le type et l argument
 * conserves. Le gabarit n a donc aucune cle a composer, et l entite ne franchit pas la
 * couche service.</p>
 *
 * <p>La date est preformatee dans le fuseau de l atelier, comme
 * {@code CommandeHistoriqueVue} : le gabarit affiche, il ne calcule pas.</p>
 */
public record NotificationVue(
        Long id,
        String titre,
        String corps,
        boolean lue,
        String date) {
}
