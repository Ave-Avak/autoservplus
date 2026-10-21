package be.autoservplus.identite.domain;

/**
 * Evenements consignes au journal de securite d un compte.
 *
 * <p>Miroir exact de {@code ck_evenement_securite_type} (V41, elargie en V42). La base porte
 * l enumeration, comme partout dans le schema sauf {@code notification.type} — ecart dont
 * le registre montre le prix : une valeur inconnue y passe et fait tomber l ecran a la
 * lecture.</p>
 */
public enum TypeEvenementSecurite {
    MFA_ACTIVE,
    MFA_DESACTIVE,
    SECOND_FACTEUR_ECHEC,
    SECOND_FACTEUR_VERROUILLAGE,
    CODE_SECOURS_CONSOMME,
    CODES_SECOURS_REGENERES,
    MFA_REINITIALISE,
    MOT_DE_PASSE_DEFINI,
    MOT_DE_PASSE_CHANGE,
    ADMINISTRATEUR_CREE,
    LIEN_DEFINITION_ENVOYE,
    CONNEXION_VERROUILLAGE
}
