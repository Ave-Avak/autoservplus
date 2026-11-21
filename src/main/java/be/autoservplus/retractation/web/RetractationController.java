package be.autoservplus.retractation.web;

import be.autoservplus.retractation.service.RetractationImpossibleException;
import be.autoservplus.retractation.service.RetractationService;
import be.autoservplus.retractation.service.dto.DeclarationRetractationVue;
import be.autoservplus.retractation.web.dto.DemandeRetractationForm;
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
 * Fonction de rétractation en ligne du membre (F30, RM-23, CDE art. VI.61/2).
 *
 * <p>Deux étapes, comme la loi les décrit. La déclaration montre le nom du
 * consommateur, la commande visée et l'adresse de l'accusé de réception, avec un motif
 * facultatif. La confirmation récapitule la déclaration et porte le seul bouton
 * « Confirmer la rétractation » : c'est lui qui enregistre la rétractation et fait
 * partir l'accusé de réception. Un clic unique depuis une liste sur un geste qui lie
 * le garage serait une erreur d'ergonomie autant que de sécurité.</p>
 *
 * <p>Patron PRG à la confirmation : POST puis redirection vers la liste avec un flash
 * message. Sans lui, un rafraîchissement du navigateur reposterait la rétractation,
 * que l'index partiel refuserait, mais avec une erreur au lieu d'un écran normal. La
 * seconde étape, elle, est rendue en réponse au POST de la première : elle n'écrit
 * rien, et la reposter ne fait que la rendre de nouveau.</p>
 *
 * <p>{@code RessourceIntrouvableException} n'est pas rattrapée : une commande inconnue
 * ou celle d'un autre membre répond 404 par la page d'erreur commune, sans rien
 * confirmer de son existence.</p>
 */
@Controller
@RequestMapping("/commandes/{reference}/annulation")
public class RetractationController {

    private static final String LISTE = "redirect:/commandes";

    private final RetractationService retractations;
    private final MessageSource messages;

    public RetractationController(RetractationService retractations, MessageSource messages) {
        this.retractations = retractations;
        this.messages = messages;
    }

    /** Première étape : la déclaration à compléter. */
    @GetMapping
    public String declaration(@AuthenticationPrincipal UserDetails membre,
                              @PathVariable UUID reference,
                              Model modele,
                              RedirectAttributes redirection) {
        return etape(membre, reference, new DemandeRetractationForm(), "retractation/demander",
                "retractation.titre", modele, redirection);
    }

    /** Seconde étape : le récapitulatif et la fonction de confirmation. Rien n'est écrit. */
    @PostMapping("/verification")
    public String verification(@AuthenticationPrincipal UserDetails membre,
                               @PathVariable UUID reference,
                               @Valid @ModelAttribute("formulaire") DemandeRetractationForm formulaire,
                               BindingResult erreurs,
                               Model modele,
                               RedirectAttributes redirection) {
        String gabarit = erreurs.hasErrors() ? "retractation/demander" : "retractation/confirmer";
        String titre = erreurs.hasErrors() ? "retractation.titre" : "retractation.confirmer.titre";
        return etape(membre, reference, formulaire, gabarit, titre, modele, redirection);
    }

    /** Confirmation : la rétractation est enregistrée, l'accusé de réception part. */
    @PostMapping
    public String confirmer(@AuthenticationPrincipal UserDetails membre,
                            @PathVariable UUID reference,
                            @Valid @ModelAttribute("formulaire") DemandeRetractationForm formulaire,
                            BindingResult erreurs,
                            Model modele,
                            RedirectAttributes redirection) {
        if (erreurs.hasErrors()) {
            return etape(membre, reference, formulaire, "retractation/demander",
                    "retractation.titre", modele, redirection);
        }
        try {
            retractations.demander(membre.getUsername(), reference, formulaire.getMotif());
            redirection.addFlashAttribute("message",
                    msg("retractation.confirmation", membre.getUsername()));
        } catch (RetractationImpossibleException e) {
            // La cle i18n est derivee du motif : le service ne fabrique aucune chaine
            // destinee a l utilisateur, et ajouter un motif oblige a ajouter sa cle.
            redirection.addFlashAttribute("erreur",
                    msg("retractation.refus." + e.getMotif().name()));
        }
        return LISTE;
    }

    private String etape(UserDetails membre, UUID reference, DemandeRetractationForm formulaire,
                         String gabarit, String cleTitre, Model modele,
                         RedirectAttributes redirection) {
        DeclarationRetractationVue declaration;
        try {
            declaration = retractations.declaration(membre.getUsername(), reference);
        } catch (RetractationImpossibleException e) {
            redirection.addFlashAttribute("erreur",
                    msg("retractation.refus." + e.getMotif().name()));
            return LISTE;
        }
        modele.addAttribute("titre", msg(cleTitre));
        modele.addAttribute("declaration", declaration);
        modele.addAttribute("formulaire", formulaire);
        return gabarit;
    }

    private String msg(String cle, Object... arguments) {
        return messages.getMessage(cle, arguments, LocaleContextHolder.getLocale());
    }
}
