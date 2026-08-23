package be.autoservplus.retractation.web;

import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.retractation.service.RetractationImpossibleException;
import be.autoservplus.retractation.service.RetractationService;
import be.autoservplus.retractation.web.dto.DemandeRetractationForm;
import be.autoservplus.vente.service.CommandeService;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

/**
 * Demande de retractation par le membre (F30, RM-23).
 *
 * <p><b>Confirmation explicite avant envoi</b> : le bouton de la liste des commandes
 * ne declenche rien, il ouvre une page qui rappelle ce qui va se passer (le garage
 * examine, le remboursement suit l acceptation) et exige une case cochee. Un clic
 * unique sur une action irreversible depuis une liste est une erreur d ergonomie
 * autant que de securite.</p>
 *
 * <p>Patron PRG : POST puis redirection vers la liste avec un flash message. Sans
 * lui, un rafraichissement du navigateur reposterait la demande — que l index partiel
 * refuserait, mais avec une erreur au lieu d un ecran normal.</p>
 *
 * <p>{@code RessourceIntrouvableException} est <b>catchee</b> et traduite en page
 * dediee plutot que laissee remonter en 500 : c est l asymetrie relevee sur
 * {@code RdvController} et il n y a pas de raison de la reproduire dans du code
 * neuf.</p>
 */
@Controller
@RequestMapping("/commandes/{reference}/annulation")
public class RetractationController {

    private static final String LISTE = "redirect:/commandes";

    private final RetractationService retractations;
    private final CommandeService commandes;
    private final MessageSource messages;

    public RetractationController(RetractationService retractations,
                                  CommandeService commandes,
                                  MessageSource messages) {
        this.retractations = retractations;
        this.commandes = commandes;
        this.messages = messages;
    }

    @GetMapping
    public String formulaire(@AuthenticationPrincipal UserDetails membre,
                             @PathVariable UUID reference,
                             Model modele) {
        return preparerFormulaire(membre, reference, new DemandeRetractationForm(), modele);
    }

    @PostMapping
    public String demander(@AuthenticationPrincipal UserDetails membre,
                           @PathVariable UUID reference,
                           @Valid @ModelAttribute("formulaire") DemandeRetractationForm formulaire,
                           BindingResult erreurs,
                           Model modele,
                           RedirectAttributes redirection) {
        if (erreurs.hasErrors()) {
            return preparerFormulaire(membre, reference, formulaire, modele);
        }
        try {
            retractations.demander(membre.getUsername(), reference, formulaire.getMotif());
            redirection.addFlashAttribute("message", msg("retractation.confirmation"));
        } catch (RetractationImpossibleException e) {
            // La cle i18n est derivee du motif : le service ne fabrique aucune chaine
            // destinee a l utilisateur, et ajouter un motif oblige a ajouter sa cle.
            redirection.addFlashAttribute("erreur",
                    msg("retractation.refus." + e.getMotif().name()));
        } catch (RessourceIntrouvableException e) {
            return "retractation/commande-introuvable";
        }
        return LISTE;
    }

    private String preparerFormulaire(UserDetails membre, UUID reference,
                                      DemandeRetractationForm formulaire, Model modele) {
        try {
            // ConfirmationCommandeVue porte exactement ce que cet ecran rappelle :
            // numero et montant TVAC. Creer une vue de plus pour deux champs deja
            // exposes serait du bruit.
            modele.addAttribute("titre", msg("retractation.titre"));
            modele.addAttribute("commande",
                    commandes.confirmation(reference, membre.getUsername()));
            modele.addAttribute("formulaire", formulaire);
            return "retractation/demander";
        } catch (RessourceIntrouvableException e) {
            return "retractation/commande-introuvable";
        }
    }

    private String msg(String cle, Object... arguments) {
        return messages.getMessage(cle, arguments, LocaleContextHolder.getLocale());
    }
}
