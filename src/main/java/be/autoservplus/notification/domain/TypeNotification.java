package be.autoservplus.notification.domain;

import be.autoservplus.identite.domain.TypeUtilisateur;

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
 * l argument conserve. Les deux colonnes du socle restent remplies (elles sont
 * {@code NOT NULL}) et servent de trace lisible en base.</p>
 *
 * <p>Chaque valeur resout deux cles : {@code notification.type.<NOM>.titre} et
 * {@code notification.type.<NOM>.corps}, cette derniere prenant l argument metier
 * (numero de rendez-vous, de commande, d intervention ou d avoir) en parametre.
 * {@link #MESSAGE_RECU} a un second corps pour l'administrateur, voir
 * {@link #cleCorps(TypeUtilisateur)}.</p>
 */
public enum TypeNotification {

    RDV_CONFIRME,
    RDV_REFUSE,
    RDV_ANNULE,
    RDV_HONORE,
    RDV_ABSENT,
    COMMANDE_PAYEE,
    INTERVENTION_TERMINEE,
    /** Travaux supplémentaires soumis à l'accord du titulaire de l'intervention (RM-15). */
    DEPASSEMENT_A_VALIDER,
    AVOIR_EMIS,
    RETRACTATION_REFUSEE,
    /** Nouveau message dans un fil de discussion (BL-5) : vise le membre ou le garage. */
    MESSAGE_RECU,
    /** Avis déposé sur une intervention, adressé à chaque administrateur actif (BL-4). */
    AVIS_DEPOSE,
    /** Nouvelle demande de rendez-vous, adressée à chaque administrateur actif (F16). */
    RDV_DEMANDE,
    /** Demande toujours en attente à mi-délai, adressée à chaque administrateur actif. */
    RDV_RELANCE,
    /** Demande restée sans réponse à son échéance, adressée au membre. */
    RDV_EXPIRE,
    /** Rendez-vous déplacé par le membre, adressé à chaque administrateur actif. */
    RDV_MODIFIE,
    /** Rendez-vous déplacé par le garage, adressé au membre. */
    RDV_DEPLACE,
    /**
     * Commande payée qui attend le garage, adressée à chaque administrateur actif (F14).
     * Distincte de COMMANDE_PAYEE, qui annonce au membre que son paiement est reçu.
     */
    COMMANDE_A_PREPARER,
    /**
     * Paiement encaissé sur une commande déjà annulée, à rembourser depuis le
     * back-office. Adressée à chaque administrateur actif.
     */
    PAIEMENT_A_REMBOURSER,
    /**
     * Demande de rétractation déposée par un membre, à décider dans le délai de
     * remboursement (F30, VI.50). Adressée à chaque administrateur actif.
     */
    RETRACTATION_DEMANDEE;

    /** Cle i18n du titre affiche. */
    public String cleTitre() {
        return "notification.type." + name() + ".titre";
    }

    /** Cle i18n du corps affiche. Le message attend l argument metier en {0}. */
    public String cleCorps() {
        return "notification.type." + name() + ".corps";
    }

    /**
     * Clé i18n du corps pour ce lecteur. Un administrateur lit {@link #MESSAGE_RECU} sous une
     * clé propre, dont l'argument est le numéro du fil et non son sujet (Q-H-27) : la
     * notification reste chez lui après la suppression du compte du membre.
     */
    public String cleCorps(TypeUtilisateur lecteur) {
        if (this == MESSAGE_RECU && lecteur != TypeUtilisateur.MEMBRE) {
            return "notification.type.MESSAGE_RECU.garage.corps";
        }
        return cleCorps();
    }
}
