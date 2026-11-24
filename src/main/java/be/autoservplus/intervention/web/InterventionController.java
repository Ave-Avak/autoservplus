package be.autoservplus.intervention.web;

import be.autoservplus.avis.service.AvisService;
import be.autoservplus.common.exception.ConflitConcurrenceException;
import be.autoservplus.common.web.Rafraichissement;
import be.autoservplus.intervention.domain.Intervention;
import be.autoservplus.intervention.service.InterventionService;
import be.autoservplus.intervention.web.dto.DemandeValidationVue;
import be.autoservplus.intervention.web.dto.InterventionVueMembre;
import jakarta.servlet.http.HttpServletResponse;
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

import java.time.Clock;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Suivi de l intervention par le membre proprietaire, sous /mes-interventions.
 *
 * <p>La page complete est rendue par {@link #suivi}. Le fragment de statut,
 * rafraichi par polling HTMX toutes les 10 secondes, est rendu par
 * {@link #blocStatut} : meme methode de chargement, meme service,
 * meme controle d ownership.</p>
 */
@Controller
@RequestMapping("/mes-interventions")
public class InterventionController {

    private final InterventionService service;
    private final AvisService avis;
    private final MessageSource messages;
    private final Clock horloge;

    public InterventionController(InterventionService service, AvisService avis, MessageSource messages,
                                  Clock horloge) {
        this.service = service;
        this.avis = avis;
        this.messages = messages;
        this.horloge = horloge;
    }

    @GetMapping("/{reference}")
    public String suivi(@AuthenticationPrincipal UserDetails membre,
                        @PathVariable UUID reference,
                        Model modele) {
        InterventionVueMembre vue = service.interventionDuMembre(reference, membre.getUsername());
        // BL-4 : le lien de depot n apparait que si le depot aboutirait, travaux
        // termines et aucun avis deja depose. Le controle reel reste fait au depot,
        // un lien absent n etant pas une securite.
        modele.addAttribute("peutDeposerAvis", avis.peutDeposer(membre.getUsername(), reference));
        modele.addAttribute("intervention", vue);
        // Facture d'atelier, son statut et son PDF au suivi (Q-H-48).
        modele.addAttribute("facture",
                service.factureAtelierDuMembre(reference, membre.getUsername()).orElse(null));
        return "intervention/suivi";
    }

    /**
     * Fragment rafraichi par polling HTMX toutes les 10 secondes. Ne renvoie que le
     * bloc statut, injecte a la place de son homologue dans la page complete via
     * {@code hx-swap="outerHTML"}.
     */
    @GetMapping("/{reference}/statut")
    public String blocStatut(@AuthenticationPrincipal UserDetails membre,
                             @PathVariable UUID reference,
                             @RequestParam(required = false) Long depuis,
                             Model modele,
                             HttpServletResponse reponse) {
        // Quinze minutes après l'affichage de la page, le rafraîchissement s'arrête.
        if (Rafraichissement.expire(depuis, horloge.instant())) {
            reponse.setStatus(Rafraichissement.ARRET);
            return null;
        }
        InterventionVueMembre vue = service.interventionDuMembre(reference, membre.getUsername());
        modele.addAttribute("intervention", vue);
        if (depuis != null) {
            modele.addAttribute("instantPage", depuis);
        }
        return "intervention/suivi :: blocStatut";
    }

    /**
     * Resolution RDV -> intervention pour le lien « Suivre l intervention »
     * depuis la fiche RDV. Redirige vers /mes-interventions/{ref} si trouvee,
     * 404 sinon (via {@code RessourceIntrouvableException}).
     */
    @GetMapping("/depuis-rdv/{rdvReference}")
    public String depuisRdv(@AuthenticationPrincipal UserDetails membre,
                            @PathVariable UUID rdvReference) {
        UUID interventionRef = service.referenceParRdvDuMembre(rdvReference, membre.getUsername());
        return "redirect:/mes-interventions/" + interventionRef;
    }

    // --- RM-15 : accord du membre sur un depassement de devis ------------------------

    /**
     * Ecran de decision. L identite vient du contexte de securite, jamais de l URL :
     * la reference seule ne suffit pas a acceder au dossier d un autre membre.
     */
    @GetMapping("/{reference}/validation")
    public String validation(@AuthenticationPrincipal UserDetails membre,
                             @PathVariable UUID reference,
                             Model modele) {
        DemandeValidationVue vue = service.demandeValidation(reference, membre.getUsername());
        modele.addAttribute("demande", vue);
        return "intervention/validation";
    }

    @PostMapping("/{reference}/valider")
    public String valider(@AuthenticationPrincipal UserDetails membre,
                          @PathVariable UUID reference,
                          RedirectAttributes redirection) {
        return repondre(reference, redirection,
                () -> service.validerDepassement(reference, membre.getUsername()),
                "intervention.validation.acceptee");
    }

    @PostMapping("/{reference}/refuser")
    public String refuser(@AuthenticationPrincipal UserDetails membre,
                          @PathVariable UUID reference,
                          RedirectAttributes redirection) {
        return repondre(reference, redirection,
                () -> service.refuserDepassement(reference, membre.getUsername()),
                "intervention.validation.refusee");
    }

    /**
     * Pattern PRG commun aux deux reponses. {@link IllegalStateException} couvre le
     * double envoi (le membre revient en arriere et re-soumet alors que la demande est
     * deja tranchee) : le domaine refuse, le membre recoit un message, pas une 500.
     *
     * <p>{@link RessourceIntrouvableException} n est volontairement PAS catchee : elle
     * doit remonter en 404, comme sur les GET. La rattraper en message d erreur ferait
     * repondre 302 a un membre qui vise le dossier d autrui : un canal qui distingue
     * « reference inexistante » de « reference existante mais pas a vous ».</p>
     */
    private String repondre(UUID reference, RedirectAttributes redirection,
                            Supplier<Intervention> action, String cleSucces) {
        try {
            action.get();
            redirection.addFlashAttribute("message", msg(cleSucces));
        } catch (ConflitConcurrenceException e) {
            redirection.addFlashAttribute("erreur", msg("erreur.409.message"));
        } catch (IllegalStateException e) {
            redirection.addFlashAttribute("erreur", msg("intervention.validation.deja-traitee"));
        }
        return "redirect:/mes-interventions/" + reference;
    }

    private String msg(String cle) {
        return messages.getMessage(cle, null, LocaleContextHolder.getLocale());
    }
}
