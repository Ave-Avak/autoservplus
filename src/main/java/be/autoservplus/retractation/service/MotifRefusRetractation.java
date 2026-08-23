package be.autoservplus.retractation.service;

/**
 * Raison pour laquelle le controle automatique refuse une demande de retractation
 * (F30, RM-23).
 *
 * <p>Un enum plutot qu un message : la couche web le traduit en cle i18n, si bien
 * que le service ne fabrique aucune chaine destinee a l utilisateur. C est aussi ce
 * qui permet aux tests d assertar la <b>raison</b> du refus et pas seulement le fait
 * qu il y en ait eu un — deux gardes differentes qui rendraient le meme message
 * passeraient l une pour l autre.</p>
 *
 * <p>Cette liste ne couvre que ce que le systeme <b>sait</b>. Les exceptions legales
 * qui dependent de l etat physique de la piece (montee, deballee, abimee) n y
 * figurent pas : elles sont constatees par l atelier et opposees a la validation,
 * avec motif libre. C est toute la raison d etre du flux en deux temps.</p>
 */
public enum MotifRefusRetractation {

    /** Rien a rembourser : le paiement n a pas abouti, la commande n a pas ete encaissee. */
    COMMANDE_NON_PAYEE,

    /**
     * Les quatorze jours legaux sont ecoules (CDE, art. VI.47). Le delai court depuis
     * la conclusion de la commande — voir {@code RetractationService}.
     */
    DELAI_EXPIRE,

    /** Une demande est deja pendante sur cette commande : rien a ajouter, il faut attendre. */
    DEMANDE_DEJA_EN_COURS,

    /** La commande a deja ete remboursee, ou annulee faute de paiement : plus rien a retracter. */
    COMMANDE_CLOTUREE
}
