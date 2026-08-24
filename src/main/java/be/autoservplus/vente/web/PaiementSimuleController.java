package be.autoservplus.vente.web;

import be.autoservplus.vente.service.SiAucunPrestataireConfigure;
import be.autoservplus.vente.service.SimulationPaiementService;
import be.autoservplus.vente.web.dto.SimulationPaiementVue;
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

import java.util.UUID;

/**
 * Page de paiement de substitution, servie a l adresse que le prestataire
 * bouchonne annonce. Elle rend la chaine marchande parcourable a l ecran sans
 * aucun service externe — c est le repli lisible quand aucune cle de prestataire
 * n est fournie.
 *
 * <p><b>Elle s annonce pour ce qu elle est.</b> Banniere permanente « paiement
 * simule », mention explicite qu aucun debit n a lieu, et deux issues offertes
 * plutot qu un seul bouton : l echec d encaissement fait partie du parcours a
 * demontrer autant que le succes, et rien d autre dans l application ne permet de
 * le provoquer. Une page qui se ferait passer pour une vraie page de paiement
 * serait, elle, indefendable.</p>
 *
 * <p>Active tant qu aucun identifiant de prestataire n est fourni, comme le bouchon
 * qu elle donne a voir — y compris en production, ou un repli annonce vaut mieux
 * qu une rupture au paiement.</p>
 */
@Controller
@SiAucunPrestataireConfigure
@RequestMapping("/paiement-fictif")
public class PaiementSimuleController {

    private final SimulationPaiementService simulation;
    private final MessageSource messages;

    public PaiementSimuleController(SimulationPaiementService simulation,
                                    MessageSource messages) {
        this.simulation = simulation;
        this.messages = messages;
    }

    @GetMapping("/{referencePrestataire}")
    public String page(@AuthenticationPrincipal UserDetails membre,
                       @PathVariable String referencePrestataire,
                       Model modele) {
        SimulationPaiementVue vue =
                simulation.aRegler(referencePrestataire, membre.getUsername());
        modele.addAttribute("titre", msg("paiement.simule.titre"));
        modele.addAttribute("paiement", vue);
        return "vente/paiement-simule";
    }

    /**
     * Simule l issue puis ramene le membre sur sa commande. PRG : un rafraichissement
     * ne rejouerait pas l issue — et le rejouerait-il que le traitement est
     * idempotent, l etat de la commande ayant deja tranche.
     */
    @PostMapping("/{referencePrestataire}")
    public String simuler(@AuthenticationPrincipal UserDetails membre,
                          @PathVariable String referencePrestataire,
                          @RequestParam(defaultValue = "false") boolean reussite) {
        UUID commande =
                simulation.simuler(referencePrestataire, reussite, membre.getUsername());
        return "redirect:/commande/" + commande + "/confirmation";
    }

    private String msg(String cle) {
        return messages.getMessage(cle, null, LocaleContextHolder.getLocale());
    }
}
