package be.autoservplus.vente.web;

import be.autoservplus.vente.service.SiAucunPrestataireConfigure;
import be.autoservplus.vente.service.SimulationPaiementService;
import be.autoservplus.vente.service.SimulationPaiementService.IssueSimulee;
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
 * Page de paiement de substitution, servie à l'adresse que le prestataire
 * bouchonné annonce. Elle rend la chaîne marchande parcourable à l'écran sans
 * aucun service externe : c'est le repli lisible quand aucune clé de prestataire
 * n'est fournie.
 *
 * <p><b>Elle s'annonce pour ce qu'elle est.</b> Bannière permanente « paiement
 * simulé », mention explicite qu'aucun débit n'a lieu, et trois issues offertes
 * plutôt qu'un seul bouton : l'échec d'encaissement et le paiement en attente de
 * confirmation font partie du parcours à démontrer autant que le succès, et rien
 * d'autre dans l'application ne permet de les provoquer. Une page qui se ferait passer pour une vraie page de paiement
 * serait, elle, indéfendable.</p>
 *
 * <p>Active tant qu'aucun identifiant de prestataire n'est fourni, comme le bouchon
 * qu'elle donne à voir, y compris en production, où un repli annoncé vaut mieux
 * qu'une rupture au paiement.</p>
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
     * Simule l'issue puis renvoie le membre par la MEME porte qu'un prestataire réel :
     * l'URL de retour de la commande, et non la page de confirmation en direct. Un
     * raccourci ici laisserait la réconciliation du retour sans couverture, alors
     * qu'elle est le seul chemin par lequel un paiement aboutit lorsque la
     * notification serveur à serveur n'arrive pas.
     */
    @PostMapping("/{referencePrestataire}")
    public String simuler(@AuthenticationPrincipal UserDetails membre,
                          @PathVariable String referencePrestataire,
                          @RequestParam(defaultValue = "ECHEC") IssueSimulee issue) {
        UUID commande =
                simulation.simuler(referencePrestataire, issue, membre.getUsername());
        return "redirect:/commande/" + commande + "/retour";
    }

    private String msg(String cle) {
        return messages.getMessage(cle, null, LocaleContextHolder.getLocale());
    }
}
