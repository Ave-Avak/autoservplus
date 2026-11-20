package be.autoservplus.identite.domain;

/**
 * Champ d'un compte dont la modification est historisée.
 *
 * <p>Liste fermée, tenue d'accord avec {@code ck_histo_compte_champ} (V57). Elle ne
 * contient aucun secret : ni le mot de passe, dont le changement va au journal de
 * sécurité sans valeur, ni le second facteur.</p>
 */
public enum ChampCompte {
    PRENOM,
    NOM,
    TELEPHONE,
    RUE,
    NUMERO_RUE,
    CODE_POSTAL,
    LOCALITE,
    PAYS,
    LANGUE,
    EMAIL
}
