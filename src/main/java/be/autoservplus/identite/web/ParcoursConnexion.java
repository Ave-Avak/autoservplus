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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Function;

/**
 * Conduit une connexion du mot de passe jusqu'à son aboutissement, étape par étape.
 *
 * <p><b>Point de greffe : le gestionnaire de succès du formulaire.</b> Spring Security
 * 6.5 n'a pas de MFA natif. Après le mot de passe, ce gestionnaire remplace
 * l'authentification complète par une {@link AuthentificationPartielle} sans autorité,
 * tant qu'une étape reste. Un filtre aurait dû redécouvrir à chaque requête ce que le
 * gestionnaire sait au moment exact où le mot de passe vient d'être vérifié. Un
 * intercepteur MVC, lui, ne verrait pas les refus de la chaîne de sécurité.</p>
 *
 * <p><b>L'étape suivante se déduit du compte</b>, relu en base à chaque pas, et non d'un
 * état tenu en session : un super-administrateur qui réinitialise un second facteur
 * pendant qu'une connexion est en cours change la suite de cette connexion.</p>
 *
 * <p><b>À l'aboutissement</b> : nouvel identifiant de session (protection contre la
 * fixation : l'identifiant connu d'un tiers pendant l'étape partielle ne doit pas
 * devenir celui d'une session complète), compteurs remis à zéro, langue du profil
 * appliquée.</p>
 *
 * <p><b>Cinq minutes par étape.</b> Au-delà, la connexion partielle est abandonnée et
 * l'utilisateur recommence au mot de passe. Le délai court de l'ouverture de l'étape,
 * et non du mot de passe : un administrateur neuf change son mot de passe puis installe
 * une application d'authentification, ce qu'un délai unique pour tout le parcours
 * rendrait difficile. L'abandon ne touche pas aux compteurs d'échecs du second facteur :
 * recommencer ne rouvre pas d'essais.</p>
 */
@Component
public class ParcoursConnexion implements AuthenticationSuccessHandler {

    private final UtilisateurRepository utilisateurs;
    private final TentativesConnexionService tentatives;
    private final LangueApresConnexionHandler langue;
    private final PageArrivee pageArrivee;
    private final DestinationApresConnexion destination;
    private final Clock horloge;
    private final SecurityContextHolderStrategy strategie =
            SecurityContextHolder.getContextHolderStrategy();
    /** Même composition que celle que HttpSecurity installe par défaut. */
    private final SecurityContextRepository depot = new DelegatingSecurityContextRepository(
            new RequestAttributeSecurityContextRepository(),
            new HttpSessionSecurityContextRepository());

    /** Durée d'une étape, de son ouverture à sa validation. */
    static final Duration DELAI_ETAPE = Duration.ofMinutes(5);
    static final String DELAI_CODE_DEPASSE = "/connexion?delai";
    static final String DELAI_MOT_DE_PASSE_DEPASSE = "/connexion?delai-mot-de-passe";

    public ParcoursConnexion(UtilisateurRepository utilisateurs,
                             TentativesConnexionService tentatives,
                             LangueApresConnexionHandler langue, PageArrivee pageArrivee,
                             DestinationApresConnexion destination, Clock horloge) {
        this.destination = destination;
        this.utilisateurs = utilisateurs;
        this.tentatives = tentatives;
        this.langue = langue;
        this.pageArrivee = pageArrivee;
        this.horloge = horloge;
    }

    /** Le mot de passe vient d'être vérifié. */
    @Override
    public void onAuthenticationSuccess(HttpServletRequest requete, HttpServletResponse reponse,
                                        Authentication complete)
            throws IOException, ServletException {
        Optional<EtapeConnexion> etape = etapeSuivante(complete.getName(), false);
        if (etape.isPresent()) {
            installer(requete, reponse,
                    new AuthentificationPartielle(complete, false, Instant.now(horloge)));
            reponse.sendRedirect(requete.getContextPath() + etape.get().chemin());
            return;
        }
        // Aucune étape : comportement d'avant le second facteur, à l'identique. Le
        // filtre du formulaire a déjà changé l'identifiant de session.
        tentatives.connexionAboutie(complete.getName());
        langue.onAuthenticationSuccess(requete, reponse, complete);
    }

    /**
     * Une étape vient d'être franchie : redirige vers la suivante, ou fait aboutir la
     * connexion sur la page demandée avant elle, à défaut sur la page d'arrivée du rôle.
     *
     * @return la vue de redirection
     */
    public String avancer(HttpServletRequest requete, HttpServletResponse reponse,
                          AuthentificationPartielle partielle) {
        return avancer(requete, reponse, partielle,
                complete -> destination.de(requete, reponse, complete));
    }

    /**
     * Comme {@link #avancer(HttpServletRequest, HttpServletResponse, AuthentificationPartielle)},
     * mais vers une destination imposée si la connexion aboutit : les codes de secours à
     * la fin d'un enrôlement, qui ne s'affichent qu'une fois.
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
            installer(requete, reponse, partielle.etapeOuverteA(Instant.now(horloge)));
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
     * Garde d'un écran d'étape : {@code null} si la requête est bien à cette étape,
     * sinon la redirection qui y remet de l'ordre. Un compte déjà connecté n'a rien à
     * faire ici. Une connexion partielle ne choisit pas son étape. Une étape ouverte
     * depuis plus de {@link #DELAI_ETAPE} est abandonnée.
     */
    public String garder(Authentication authentification, EtapeConnexion attendue,
                         HttpServletRequest requete, HttpServletResponse reponse) {
        if (!(authentification instanceof AuthentificationPartielle partielle)) {
            return "redirect:" + pageArrivee.de(authentification);
        }
        Optional<EtapeConnexion> courante =
                etapeSuivante(partielle.getName(), partielle.secondFacteurValide());
        if (courante.isEmpty()) {
            // Plus rien à franchir : un tiers a pu lever l'obligation entre-temps. On
            // ne fait pas aboutir sur un GET : l'utilisateur recommence proprement.
            return "redirect:/connexion";
        }
        if (Instant.now(horloge).isAfter(partielle.etapeOuverteLe().plus(DELAI_ETAPE))) {
            abandonner(requete, reponse);
            return "redirect:" + (courante.get() == EtapeConnexion.CHANGEMENT_MOT_DE_PASSE
                    ? DELAI_MOT_DE_PASSE_DEPASSE
                    : DELAI_CODE_DEPASSE);
        }
        return courante.get() == attendue ? null : "redirect:" + courante.get().chemin();
    }

    /** Écarte une connexion partielle : verrouillage, ou étape devenue impossible. */
    public void abandonner(HttpServletRequest requete, HttpServletResponse reponse) {
        strategie.clearContext();
        HttpSession session = requete.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    Optional<EtapeConnexion> etapeSuivante(String email, boolean secondFacteurValide) {
        Optional<Utilisateur> compte = utilisateurs.findByEmail(email);
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
