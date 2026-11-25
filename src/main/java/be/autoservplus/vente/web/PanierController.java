package be.autoservplus.vente.web;

import be.autoservplus.common.exception.ConflitConcurrenceException;
import be.autoservplus.common.exception.RefusTraduit;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.reservation.service.VehiculeService;
import be.autoservplus.vente.service.PanierService;
import be.autoservplus.vente.service.PieceInactiveException;
import be.autoservplus.vente.service.StockInsuffisantException;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

/**
 * Panier du membre connecté, sous /panier (F13). Toute la zone exige une
 * authentification ({@code anyRequest().authenticated()} de la configuration de
 * sécurité). Le service redouble par {@code @PreAuthorize} en défense en
 * profondeur, et l'identité vient du contexte de sécurité, jamais de l'URL.
 *
 * <p>Pattern PRG + flash comme les autres contrôleurs. Les messages de succès et
 * d'erreur sont résolus ici via le {@code MessageSource} (clés {@code panier.*}) :
 * le refus de stock, notamment, restitue la quantité encore disponible, car un refus
 * sans chiffre ne serait pas actionnable. {@link RessourceIntrouvableException}
 * n'est volontairement PAS catchée : une ligne d'autrui ou inconnue remonte en 404,
 * même raisonnement que sur les réponses RM-15 du suivi d'intervention.</p>
 *
 * <p>« Vider » passe par une page de confirmation dédiée (GET puis POST), comme
 * les écrans admin d'annulation : la CSP interdit les scripts en ligne, un
 * {@code confirm()} n'est pas disponible, et un geste destructif ne se déclenche
 * pas d'un seul clic.</p>
 */
@Controller
@RequestMapping("/panier")
public class PanierController {

    private final PanierService service;
    private final VehiculeService vehicules;
    private final MessageSource messages;

    public PanierController(PanierService service, VehiculeService vehicules, MessageSource messages) {
        this.service = service;
        this.vehicules = vehicules;
        this.messages = messages;
    }

    @GetMapping
    public String panier(@AuthenticationPrincipal UserDetails membre, Model modele) {
        modele.addAttribute("titre", msg("panier.titre"));
        modele.addAttribute("panier", service.panierDuMembre(membre.getUsername()));
        // Le devis (F15) se demande depuis le panier et nomme l'un de ces véhicules.
        modele.addAttribute("vehicules", vehicules.vuesDuMembre(membre.getUsername()));
        return "vente/panier";
    }

    @PostMapping("/ajouter")
    public String ajouter(@AuthenticationPrincipal UserDetails membre,
                          @RequestParam UUID reference,
                          @RequestParam int quantite,
                          RedirectAttributes redirection) {
        return executer(redirection, "panier.message.ajout",
                () -> service.ajouterPiece(membre.getUsername(), reference, quantite));
    }

    /**
     * Ajout d'une prestation au panier (F12). Endpoint distinct de
     * {@code /panier/ajouter} : les deux natures ne partagent ni le service appelé ni
     * les contrôles (stock pour la pièce, aucun pour la prestation), et un paramètre
     * « type » obligerait le contrôleur à trancher ce que la route dit déjà.
     */
    @PostMapping("/ajouter-service")
    public String ajouterService(@AuthenticationPrincipal UserDetails membre,
                                 @RequestParam UUID reference,
                                 @RequestParam int quantite,
                                 RedirectAttributes redirection) {
        return executer(redirection, "panier.message.ajout",
                () -> service.ajouterService(membre.getUsername(), reference, quantite));
    }

    @PostMapping("/lignes/{id}/quantite")
    public String modifierQuantite(@AuthenticationPrincipal UserDetails membre,
                                   @PathVariable Long id,
                                   @RequestParam int quantite,
                                   RedirectAttributes redirection) {
        return executer(redirection, "panier.message.quantite",
                () -> service.modifierQuantite(membre.getUsername(), id, quantite));
    }

    @PostMapping("/lignes/{id}/retirer")
    public String retirer(@AuthenticationPrincipal UserDetails membre,
                          @PathVariable Long id,
                          RedirectAttributes redirection) {
        return executer(redirection, "panier.message.retrait",
                () -> service.retirerLigne(membre.getUsername(), id));
    }

    /** Confirmation explicite avant le vidage : un clic ne suffit pas. */
    @GetMapping("/vider")
    public String confirmerVidage(Model modele) {
        modele.addAttribute("titre", msg("panier.vider.titre"));
        return "vente/vider";
    }

    @PostMapping("/vider")
    public String vider(@AuthenticationPrincipal UserDetails membre,
                        RedirectAttributes redirection) {
        return executer(redirection, "panier.message.vide",
                () -> service.vider(membre.getUsername()));
    }

    /**
     * Tronc commun PRG : exécute l'action, traduit les refus métier en messages
     * flash i18n, et revient toujours sur le panier. {@link IllegalArgumentException}
     * couvre la quantité invalide (0, négative, hors borne) détectée par le service :
     * le membre reçoit une consigne, pas une page d'erreur.
     */
    private String executer(RedirectAttributes redirection, String cleSucces, Runnable action) {
        try {
            action.run();
            redirection.addFlashAttribute("message", msg(cleSucces));
        } catch (StockInsuffisantException e) {
            redirection.addFlashAttribute("erreur",
                    msg("panier.erreur.stock", e.getQuantiteDisponible()));
        } catch (PieceInactiveException e) {
            redirection.addFlashAttribute("erreur", msg("panier.erreur.piece-inactive"));
        } catch (IllegalArgumentException e) {
            redirection.addFlashAttribute("erreur", msg("panier.erreur.quantite"));
        } catch (ConflitConcurrenceException e) {
            redirection.addFlashAttribute("erreur", msg("panier.erreur.conflit"));
        } catch (RefusTraduit refus) {
            // Refus du domaine qui porte sa clé, une prestation à l'heure par exemple (Q-H-36).
            redirection.addFlashAttribute("erreur", messages.getMessage(refus.getCleMessage(),
                    refus.getArguments(), LocaleContextHolder.getLocale()));
        }
        return "redirect:/panier";
    }

    private String msg(String cle, Object... arguments) {
        return messages.getMessage(cle, arguments, LocaleContextHolder.getLocale());
    }
}
