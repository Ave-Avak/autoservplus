package be.autoservplus.notification.domain;

/**
 * Cycle de vie d une notification, aligne sur le CHECK {@code ck_notification_statut}
 * du socle V7.
 *
 * <p>{@code ARCHIVEE} est admis par la base mais n est pas employe en V1 : l ecran
 * ne propose que « marquer comme lue ». La valeur reste declaree pour que l enum
 * couvre le CHECK — un statut present en base et absent de l enum ferait echouer la
 * lecture d une ligne posee par une version ulterieure.</p>
 */
public enum StatutNotification {

    NON_LUE,
    LUE,
    ARCHIVEE
}
