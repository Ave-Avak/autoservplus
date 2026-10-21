package be.autoservplus.identite.web;

import be.autoservplus.i18n.LangueApresConnexionHandler;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.identite.service.TentativesConnexionService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;
import java.util.function.Function;

/**
 * Conduit une connexion du mot de passe jusqu a son aboutissement, etape par etape.
 *
 * <p><b>Point de greffe : le gestionnaire de succes du formulaire.</b> Spring Security
 * 6.5 n a pas de MFA natif. Apres le mot de passe, ce gestionnaire remplace
 * l authentification complete par une {@link AuthentificationPartielle} sans autorite,
 * tant qu une etape reste. Un filtre aurait du redecouvrir a chaque requete ce que le
 * gestionnaire sait au moment exact ou le mot de passe vient d etre verifie ; un
 * intercepteur MVC, lui, ne verrait pas les refus de la chaine de securite.</p>
 *
 * <p><b>L etape suivante se deduit du compte</b>, relu en base a chaque pas, et non d un
 * etat tenu en session : un super-administrateur qui reinitialise un second facteur
 * pendant qu une connexion est en cours change la suite de cette connexion.</p>
 *
 * <p><b>A l aboutissement</b> : nouvel identifiant de session (protection contre la
 * fixation — l identifiant connu d un tiers pendant l etape partielle ne doit pas
 * devenir celui d une session complete), compteurs remis a zero, langue du profil
 * appliquee.</p>
 */
@Component
public class ParcoursConnexion implements AuthenticationSuccessHandler {

    private final UtilisateurRepository utilisateurs;
    private final TentativesConnexionService tentatives;
    private final LangueApresConnexionHandler langue;
    private final PageArrivee pageArrivee;
    private final SecurityContextHolderStrategy strategie =
            SecurityContextHolder.getContextHolderStrategy();
    /** Meme composition que celle que HttpSecurity installe par defaut. */
    private final SecurityContextRepository depot = new DelegatingSecurityContextRepository(
            new RequestAttributeSecurityContextRepository(),
            new HttpSessionSecurityContextRepository());

    public ParcoursConnexion(UtilisateurRepository utilisateurs,
                             TentativesConnexionService tentatives,
                             LangueApresConnexionHandler langue, PageArrivee pageArrivee) {
        this.utilisateurs = utilisateurs;
        this.tentatives = tentatives;
        this.langue = langue;
        this.pageArrivee = pageArrivee;
    }

    /** Le mot de passe vient d etre verifie. */
    @Override
    public void onAuthenticationSuccess(HttpServletRequest requete, HttpServletResponse reponse,
                                        Authentication complete)
            throws IOException, ServletException {
        Optional<EtapeConnexion> etape = etapeSuivante(complete.getName(), false);
        if (etape.isPresent()) {
            installer(requete, reponse, new AuthentificationPartielle(complete, false));
            reponse.sendRedirect(requete.getContextPath() + etape.get().chemin());
            return;
        }
        // Aucune etape : comportement d avant le second facteur, a l identique. Le
        // filtre du formulaire a deja change l identifiant de session.
        tentatives.connexionAboutie(complete.getName());
        langue.onAuthenticationSuccess(requete, reponse, complete);
    }

    /**
     * Une etape vient d etre franchie : redirige vers la suivante, ou fait aboutir la
     * connexion sur la page d arrivee du role.
     *
     * @return la vue de redirection
     */
    public String avancer(HttpServletRequest requete, HttpServletResponse reponse,
                          AuthentificationPartielle partielle) {
        return avancer(requete, reponse, partielle, pageArrivee::de);
    }

    /**
     * Comme {@link #avancer(HttpServletRequest, HttpServletResponse, AuthentificationPartielle)},
     * mais vers une destination imposee si la connexion aboutit : les codes de secours a
     * la fin d un enrolement, qui ne s affichent qu une fois.
     */
    public String avancer(HttpServletRequest requete, HttpServletResponse reponse,
                          AuthentificationPartielle partielle, String destinationFinale) {
        return avancer(requete, reponse, partielle, complete -> destinationFinale);
    }

    private String avancer(HttpServletRequest requete, HttpServletResponse reponse,
                           AuthentificationPartielle partielle,
                           Function<Authentication, String> destinationFinale) {
        Optional<EtapeConnexion> etape =
                etapeSuivante(partielle.getName(), partielle.secondFacteurValide());
        if (etape.isPresent()) {
            installer(requete, reponse, partielle);
            return "redirect:" + etape.get().chemin();
        }
        requete.changeSessionId();
        Authentication complete = partielle.complete();
        installer(requete, reponse, complete);
        tentatives.connexionAboutie(complete.getName());
        langue.appliquerLaPreference(requete, reponse, complete);
        return "redirect:" + destinationFinale.apply(complete);
    }

    /**
     * Garde d un ecran d etape : {@code null} si la requete est bien a cette etape,
     * sinon la redirection qui y remet de l ordre. Un compte deja connecte n a rien a
     * faire ici ; une connexion partielle ne choisit pas son etape.
     */
    public String garder(Authentication authentification, EtapeConnexion attendue) {
        if (!(authentification instanceof AuthentificationPartielle partielle)) {
            return "redirect:" + pageArrivee.de(authentification);
        }
        Optional<EtapeConnexion> courante =
                etapeSuivante(partielle.getName(), partielle.secondFacteurValide());
        if (courante.isEmpty()) {
            // Plus rien a franchir — un tiers a pu lever l obligation entre-temps. On
            // ne fait pas aboutir sur un GET : l utilisateur recommence proprement.
            return "redirect:/connexion";
        }
        return courante.get() == attendue ? null : "redirect:" + courante.get().chemin();
    }

    /** Ecarte une connexion partielle : verrouillage, ou etape devenue impossible. */
    public void abandonner(HttpServletRequest requete, HttpServletResponse reponse) {
        strategie.clearContext();
        HttpSession session = requete.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    Optional<EtapeConnexion> etapeSuivante(String email, boolean secondFacteurValide) {
        Optional<Utilisateur> compte = utilisateurs.findByEmailIgnoreCase(email);
        if (compte.isEmpty()) {
            return Optional.empty();
        }
        Utilisateur utilisateur = compte.get();
        if (utilisateur.secondFacteurActif() && !secondFacteurValide) {
            return Optional.of(EtapeConnexion.SECOND_FACTEUR);
        }
        if (utilisateur.doitChangerMotDePasse()) {
            return Optional.of(EtapeConnexion.CHANGEMENT_MOT_DE_PASSE);
        }
        if (utilisateur.secondFacteurObligatoire() && !utilisateur.secondFacteurActif()) {
            return Optional.of(EtapeConnexion.ENROLEMENT);
        }
        return Optional.empty();
    }

    private void installer(HttpServletRequest requete, HttpServletResponse reponse,
                           Authentication authentification) {
        SecurityContext contexte = strategie.createEmptyContext();
        contexte.setAuthentication(authentification);
        strategie.setContext(contexte);
        depot.saveContext(contexte, requete, reponse);
    }
}
