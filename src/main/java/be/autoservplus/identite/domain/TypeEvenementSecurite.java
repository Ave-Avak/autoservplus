package be.autoservplus.identite.domain;

/**
 * Evenements consignes au journal de securite d un compte.
 *
 * <p>Miroir exact de {@code ck_evenement_securite_type} (V41, elargie en V42 puis V46). La base porte
 * l'énumération, comme partout dans le schéma sauf {@code notification.type}. Cet écart
 * montre le prix de l'absence de contrainte : une valeur inconnue s'y insère sans erreur,
 * puis la lecture échoue et fait tomber toute la liste des notifications du membre, pas
 * seulement la ligne fautive.</p>
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
    CONNEXION_VERROUILLAGE,
    MOT_DE_PASSE_REINITIALISE
}
