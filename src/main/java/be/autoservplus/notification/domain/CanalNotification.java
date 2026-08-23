package be.autoservplus.notification.domain;

/**
 * Canal de remise d une notification, aligne sur le CHECK {@code ck_notification_canal}
 * du socle V7.
 *
 * <p>BL-6 ne produit que des notifications {@code APPLICATION}. Le courriel continue
 * de partir par {@code ServiceCourriel}, sur son propre chemin : les deux dispositifs
 * restent independants, et une panne du fournisseur de courriel ne doit pas priver le
 * membre de la notification in-app. {@code EMAIL} et {@code LES_DEUX} sont declares
 * pour couvrir le CHECK, pas employes.</p>
 */
public enum CanalNotification {

    APPLICATION,
    EMAIL,
    LES_DEUX
}
