package be.autoservplus.vente.web;

import be.autoservplus.common.exception.RefusTraduit;
import be.autoservplus.vente.service.PrestataireIndisponibleException;
import be.autoservplus.vente.service.RemboursementApresAnnulationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

/**
 * Back-office : paiements encaissés sur une commande déjà annulée, à rembourser.
 * L'auteur du remboursement vient du contexte de sécurité, jamais de la requête.
 */
@Controller
@RequestMapping("/admin/paiements-a-rembourser")
public class AdminPaiementApresAnnulationController {

    private static final Logger JOURNAL =
            LoggerFactory.getLogger(AdminPaiementApresAnnulationController.class);

    private final RemboursementApresAnnulationService remboursements;
    private final MessageSource messages;

    public AdminPaiementApresAnnulationController(RemboursementApresAnnulationService remboursements,
                                                  MessageSource messages) {
        this.remboursements = remboursements;
        this.messages = messages;
    }

    @GetMapping
    public String liste(Model modele) {
        modele.addAttribute("paiements", remboursements.tous());
        return "admin/paiements-a-rembourser";
    }

    /**
     * Confirmation avant l'envoi : rendre de l'argent ne part pas d'un clic dans une
     * liste. La page rappelle la commande, le client et le montant.
     */
    @GetMapping("/{reference}")
    public String confirmer(@PathVariable UUID reference, Model modele,
                            RedirectAttributes redirection) {
        try {
            modele.addAttribute("paiement", remboursements.aConfirmer(reference));
            return "admin/paiement-a-rembourser";
        } catch (RefusTraduit refus) {
            redirection.addFlashAttribute("erreur", msg(refus.getCleMessage()));
            return "redirect:/admin/paiements-a-rembourser";
        }
    }

    @PostMapping("/{reference}/rembourser")
    public String rembourser(@AuthenticationPrincipal UserDetails administrateur,
                             @PathVariable UUID reference,
                             RedirectAttributes redirection) {
        try {
            remboursements.rembourser(reference, administrateur.getUsername());
            redirection.addFlashAttribute("message", msg("admin.paiements-tardifs.rembourse"));
        } catch (RefusTraduit refus) {
            redirection.addFlashAttribute("erreur", msg(refus.getCleMessage()));
        } catch (PrestataireIndisponibleException panne) {
            // Rien n'est écrit : le paiement reste à rembourser. Le détail va au
            // journal, pas à l'écran, il peut porter des éléments du prestataire.
            JOURNAL.warn("Remboursement du paiement {} impossible : {}", reference,
                    panne.getMessage(), panne);
            redirection.addFlashAttribute("erreur",
                    msg("admin.paiements-tardifs.erreur.prestataire"));
        }
        return "redirect:/admin/paiements-a-rembourser";
    }

    private String msg(String cle) {
        return messages.getMessage(cle, null, LocaleContextHolder.getLocale());
    }
}
