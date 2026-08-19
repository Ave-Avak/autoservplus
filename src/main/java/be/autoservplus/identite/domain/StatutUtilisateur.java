package be.autoservplus.identite.domain;

/** Cycle de vie d un compte, de l inscription a la suppression. */
public enum StatutUtilisateur {
    EN_ATTENTE_VALIDATION,
    ACTIF,
    SUSPENDU,
    SUPPRIME
}