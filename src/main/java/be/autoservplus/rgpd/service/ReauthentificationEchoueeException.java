package be.autoservplus.rgpd.service;

import be.autoservplus.common.exception.RegleMetierException;

/**
 * Le mot de passe confirme avant l export ne correspond pas au compte (F22).
 *
 * <p>Une session ouverte ne suffit pas a declencher un export : le fichier
 * rassemble en un seul document tout ce que la plateforme detient sur la
 * personne — profil, adresse, vehicules, historique d atelier, montants,
 * adresses IP de consentement. C est precisement la cible d un poste laisse sans
 * surveillance ou d une session volee. Redemander le mot de passe ramene la
 * preuve d identite au niveau de ce qui est communique.
 *
 * <p>Pas de code RM : la garde vient de la fonctionnalite F22, pas d une regle
 * numerotee du CdC — d ou le constructeur sans code.
 *
 * <p>Le message reste generique : il n indique jamais si l echec vient du mot de
 * passe ou du compte, meme raisonnement que {@code UtilisateurDetailsService}.
 */
public class ReauthentificationEchoueeException extends RegleMetierException {

    public ReauthentificationEchoueeException() {
        super("Mot de passe incorrect : aucun export n a ete produit.");
    }
}
