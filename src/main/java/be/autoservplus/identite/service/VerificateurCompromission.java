package be.autoservplus.identite.service;

/**
 * Frontiere vers un service de mots de passe compromis, seconde couche de refus apres
 * {@link MotsDePasseCourants}.
 *
 * <p>Une interface et non un appel direct, sur le patron de
 * {@code PrestatairePaiement} : le parcours d inscription doit s eprouver sans acces
 * a Internet, et la panne du service ne doit pas se propager au code appelant.</p>
 *
 * <p><b>Contrat en cas d echec : rendre {@code false}.</b> Un service injoignable ne
 * bloque pas l inscription — la liste embarquee a deja tranche, et refuser un mot de
 * passe correct parce qu un tiers est en panne serait un deni de service inflige a
 * l utilisateur. L implementation journalise, l appelant n en sait rien.</p>
 */
public interface VerificateurCompromission {

    /**
     * Dit si le mot de passe figure dans une fuite connue.
     *
     * @return {@code false} en cas d echec ou de depassement de delai — voir le
     *         contrat ci-dessus. Ne leve jamais.
     */
    boolean estCompromis(String motDePasse);
}
