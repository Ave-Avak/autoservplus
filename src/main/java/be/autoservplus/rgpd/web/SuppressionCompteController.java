package be.autoservplus.rgpd.web;

import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.rgpd.service.ConfirmationSuppressionInvalideException;
import be.autoservplus.rgpd.service.ReauthentificationEchoueeException;
import be.autoservplus.rgpd.service.SuppressionCompteService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Rubrique « Supprimer mon compte » (F23, RM-05 : droit à l'effacement, article 17
 * RGPD).
 *
 * <p>Self-service strict : le membre supprime <b>son</b> compte. L'identité vient de
 * {@code @AuthenticationPrincipal}, jamais d'un paramètre : il n'existe aucune URL
 * permettant de désigner un autre titulaire, et donc aucun écran administrateur à
 * protéger.</p>
 *
 * <p><b>Deux gardes, deux natures.</b> Le mot de passe prouve qui agit. Le mot
 * recopié prouve que l'action est voulue. L'opération est irréversible : elle ne se
 * déclenche pas sur un clic depuis un poste laissé sans surveillance.</p>
 *
 * <p><b>Révocation de l'accès après commit.</b> La session courante est invalidée par
 * {@link SecurityContextLogoutHandler}, le mécanisme même du {@code /deconnexion}
 * configuré. Vérification faite sur {@code SecuriteConfig} : pas de
 * {@code rememberMe}, pas de {@code SessionRegistry}, pas de {@code spring-session},
 * pas de table {@code persistent_logins}, il n'existe donc pas d'autre porte à
 * fermer. Une révocation qui oublierait un jeton persistant laisserait un accès
 * ouvert sur un compte supposé effacé. Ce n'est pas le cas ici, et le jour où un
 * remember-me sera ajouté, ce point devra être repris.</p>
 *
 * <p>Le refus voyage en <b>code</b> dans l'URL et le GET le retraduit en message
 * i18n, patron déjà retenu par {@code DonneesPersonnellesController} de la même
 * rubrique. Aucun code ne divulgue quoi que ce soit.</p>
 */
@Controller
public class SuppressionCompteController {

    private static final String ERREUR_MOT_DE_PASSE = "motdepasse";
    private static final String ERREUR_CONFIRMATION = "confirmation";
    private static final String ERREUR_IMPOSSIBLE = "impossible";
    private static final String ECRAN = "redirect:/supprimer-mon-compte";

    private final SuppressionCompteService service;
    private final MessageSource messages;

    public SuppressionCompteController(SuppressionCompteService service, MessageSource messages) {
        this.service = service;
        this.messages = messages;
    }

    @GetMapping("/supprimer-mon-compte")
    public String page(@RequestParam(name = "erreur", required = false) String codeErreur,
                       Model modele) {
        modele.addAttribute("titre", msg("suppression.titre"));
        // Le mot à recopier vient du service : l'écran affiche exactement ce que la
        // garde attend, il n'en tient pas une copie qui pourrait diverger.
        modele.addAttribute("motDeConfirmation", SuppressionCompteService.MOT_DE_CONFIRMATION);
        messageErreur(codeErreur).ifPresent(erreur -> modele.addAttribute("erreur", erreur));
        return "rgpd/supprimer-mon-compte";
    }

    /**
     * Supprime le compte puis ferme la session.
     *
     * <p>La déconnexion suit le commit du service : invalider la session avant
     * laisserait, si la transaction échouait, un membre déconnecté d'un compte
     * toujours actif, l'inverse exact de ce qu'on veut.</p>
     */
    @PostMapping("/supprimer-mon-compte")
    public String supprimer(@AuthenticationPrincipal UserDetails membre,
                            @RequestParam(name = "motDePasse", required = false) String motDePasse,
                            @RequestParam(name = "confirmation", required = false) String confirmation,
                            Authentication authentification,
                            HttpServletRequest requete,
                            HttpServletResponse reponse) {
        try {
            service.supprimer(membre.getUsername(), motDePasse, confirmation);
        } catch (ReauthentificationEchoueeException e) {
            return ECRAN + "?erreur=" + ERREUR_MOT_DE_PASSE;
        } catch (ConfirmationSuppressionInvalideException e) {
            return ECRAN + "?erreur=" + ERREUR_CONFIRMATION;
        } catch (RessourceIntrouvableException | IllegalStateException e) {
            // Compte introuvable, déjà anonymisé, ou compte administrateur : aucun 500
            // brut, l'écran le dit. Même choix que les contrôleurs récents du projet.
            return ECRAN + "?erreur=" + ERREUR_IMPOSSIBLE;
        }
        new SecurityContextLogoutHandler().logout(requete, reponse, authentification);
        return "redirect:/compte-supprime";
    }

    /**
     * Page publique de confirmation. Publique par nécessité : à l'instant où elle
     * s'affiche, la session vient d'être invalidée et le compte n'existe plus. Une
     * page authentifiée renverrait vers la connexion, avec un formulaire que plus
     * aucun identifiant ne satisfait.
     */
    @GetMapping("/compte-supprime")
    public String confirmation(Model modele) {
        modele.addAttribute("titre", msg("suppression.confirmee.titre"));
        return "rgpd/compte-supprime";
    }

    private java.util.Optional<String> messageErreur(String code) {
        return switch (code == null ? "" : code) {
            case ERREUR_MOT_DE_PASSE -> java.util.Optional.of(msg("suppression.erreur.mot-de-passe"));
            case ERREUR_CONFIRMATION -> java.util.Optional.of(msg("suppression.erreur.confirmation"));
            case ERREUR_IMPOSSIBLE -> java.util.Optional.of(msg("suppression.erreur.impossible"));
            // Un code inconnu (l'URL est modifiable à la main) ne produit aucun
            // message plutôt qu'une page en erreur.
            default -> java.util.Optional.empty();
        };
    }

    private String msg(String cle, Object... arguments) {
        return messages.getMessage(cle, arguments, LocaleContextHolder.getLocale());
    }
}
