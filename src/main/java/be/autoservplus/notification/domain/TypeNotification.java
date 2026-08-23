package be.autoservplus.notification.domain;

/**
 * Motif d une notification in-app (BL-6). Stocke en clair dans
 * {@code notification.type} (varchar 40), et surtout <b>cle de rendu</b> : c est lui
 * qui designe les libelles i18n affiches, pas le texte fige en base.
 *
 * <p><b>Pourquoi le type porte le rendu.</b> Les colonnes {@code titre} et
 * {@code corps} du socle V7 supposaient un texte redige au moment de l ecriture. Le
 * projet est devenu trilingue depuis : un texte fige a l ecriture serait rendu dans
 * la langue de l evenement (souvent une action d administrateur), pas dans celle du
 * membre qui le lit. Le rendu se fait donc a la <b>lecture</b>, depuis ce type et
 * l argument conserve. Les deux colonnes du socle restent remplies — elles sont
 * {@code NOT NULL} — et servent de trace lisible en base.</p>
 *
 * <p>Chaque valeur resout deux cles : {@code notification.type.<NOM>.titre} et
 * {@code notification.type.<NOM>.corps}, cette derniere prenant l argument metier
 * (numero de rendez-vous, de commande, d intervention ou d avoir) en parametre.</p>
 */
public enum TypeNotification {

    RDV_CONFIRME,
    RDV_REFUSE,
    RDV_ANNULE,
    RDV_HONORE,
    RDV_ABSENT,
    COMMANDE_PAYEE,
    INTERVENTION_TERMINEE,
    AVOIR_EMIS,
    RETRACTATION_REFUSEE;

    /** Cle i18n du titre affiche. */
    public String cleTitre() {
        return "notification.type." + name() + ".titre";
    }

    /** Cle i18n du corps affiche ; le message attend l argument metier en {0}. */
    public String cleCorps() {
        return "notification.type." + name() + ".corps";
    }
}
