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
 * Rubrique « Supprimer mon compte » (F23, RM-05 — droit a l effacement, article 17
 * RGPD).
 *
 * <p>Self-service strict : le membre supprime <b>son</b> compte. L identite vient de
 * {@code @AuthenticationPrincipal}, jamais d un parametre — il n existe aucune URL
 * permettant de designer un autre titulaire, et donc aucun ecran administrateur a
 * proteger.</p>
 *
 * <p><b>Deux gardes, deux natures.</b> Le mot de passe prouve qui agit ; le mot
 * recopie prouve que l action est voulue. L operation est irreversible : elle ne se
 * declenche pas sur un clic depuis un poste laisse sans surveillance.</p>
 *
 * <p><b>Revocation de l acces apres commit.</b> La session courante est invalidee par
 * {@link SecurityContextLogoutHandler}, le mecanisme meme du {@code /deconnexion}
 * configure. Verification faite sur {@code SecuriteConfig} : pas de
 * {@code rememberMe}, pas de {@code SessionRegistry}, pas de {@code spring-session},
 * pas de table {@code persistent_logins} — il n existe donc pas d autre porte a
 * fermer. Une revocation qui oublierait un jeton persistant laisserait un acces
 * ouvert sur un compte suppose efface ; ce n est pas le cas ici, et le jour ou un
 * remember-me sera ajoute, ce point devra etre repris.</p>
 *
 * <p>Le refus voyage en <b>code</b> dans l URL et le GET le retraduit en message
 * i18n, patron deja retenu par {@code DonneesPersonnellesController} de la meme
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
        // Le mot a recopier vient du service : l ecran affiche exactement ce que la
        // garde attend, il n en tient pas une copie qui pourrait diverger.
        modele.addAttribute("motDeConfirmation", SuppressionCompteService.MOT_DE_CONFIRMATION);
        messageErreur(codeErreur).ifPresent(erreur -> modele.addAttribute("erreur", erreur));
        return "rgpd/supprimer-mon-compte";
    }

    /**
     * Supprime le compte puis ferme la session.
     *
     * <p>La deconnexion suit le commit du service : invalider la session avant
     * laisserait, si la transaction echouait, un membre deconnecte d un compte
     * toujours actif — l inverse exact de ce qu on veut.</p>
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
            // Compte introuvable, deja anonymise, ou compte administrateur : aucun 500
            // brut, l ecran le dit. Meme choix que les controleurs recents du projet.
            return ECRAN + "?erreur=" + ERREUR_IMPOSSIBLE;
        }
        new SecurityContextLogoutHandler().logout(requete, reponse, authentification);
        return "redirect:/compte-supprime";
    }

    /**
     * Page publique de confirmation. Publique par necessite : a l instant ou elle
     * s affiche, la session vient d etre invalidee et le compte n existe plus — une
     * page authentifiee renverrait vers la connexion, avec un formulaire que plus
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
            // Un code inconnu — l URL est modifiable a la main — ne produit aucun
            // message plutot qu une page en erreur.
            default -> java.util.Optional.empty();
        };
    }

    private String msg(String cle, Object... arguments) {
        return messages.getMessage(cle, arguments, LocaleContextHolder.getLocale());
    }
}
