package be.autoservplus.identite.web;

import be.autoservplus.common.exception.RefusTraduit;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.service.ComptesAdministrateursService;
import be.autoservplus.identite.service.GestionComptesService;
import be.autoservplus.identite.service.JournalSecuriteCompte;
import be.autoservplus.identite.web.dto.CompteVue;
import be.autoservplus.identite.web.dto.CreationAdministrateurForm;
import be.autoservplus.identite.web.dto.EvenementSecuriteVue;
import be.autoservplus.identite.web.dto.EvenementStatutVue;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.UUID;

/**
 * Gestion des comptes depuis le back-office (CdC 5.2.3).
 *
 * <p>La protection d URL {@code /admin/**} filtre le role ADMINISTRATEUR. Le service
 * redouble par {@code @PreAuthorize}, et c est lui qui porte l exclusivite des
 * pouvoirs sur les comptes privilegies. Le controleur n arbitre rien : il affiche,
 * transmet, et traduit les refus.</p>
 *
 * <p>Toutes les mutations passent par POST puis redirection : un rafraichissement ne
 * doit pas rejouer une suspension, et le message de confirmation doit survivre a la
 * redirection sans que l URL en porte la trace.</p>
 */
@Controller
@RequestMapping("/admin/comptes")
public class AdminComptesController {

    private final GestionComptesService service;
    private final ComptesAdministrateursService administrateurs;
    private final JournalSecuriteCompte journalSecurite;
    private final MessageSource messages;

    public AdminComptesController(GestionComptesService service,
                                  ComptesAdministrateursService administrateurs,
                                  JournalSecuriteCompte journalSecurite,
                                  MessageSource messages) {
        this.service = service;
        this.administrateurs = administrateurs;
        this.journalSecurite = journalSecurite;
        this.messages = messages;
    }

    private String msg(String cle) {
        return messages.getMessage(cle, null, LocaleContextHolder.getLocale());
    }

    @GetMapping
    public String liste(@RequestParam(required = false) String recherche, Model modele) {
        modele.addAttribute("comptes", service.membres(recherche).stream()
                .map(CompteVue::de)
                .toList());
        modele.addAttribute("recherche", recherche);
        return "admin/comptes";
    }

    /** Comptes du back-office. La garde est au service : lui seul connaît l auteur. */
    @GetMapping("/administrateurs")
    public String administrateurs(Model modele) {
        modele.addAttribute("comptes", service.comptesPrivilegies().stream()
                .map(CompteVue::de)
                .toList());
        return "admin/administrateurs";
    }

    @GetMapping("/{reference}")
    public String detail(@PathVariable UUID reference, Model modele) {
        Utilisateur compte = service.parReference(reference);
        modele.addAttribute("compte", CompteVue.de(compte));
        List<EvenementStatutVue> journal = service.historiqueDe(compte).stream()
                .map(EvenementStatutVue::de)
                .toList();
        modele.addAttribute("journal", journal);
        modele.addAttribute("journalSecurite", journalSecurite.evenementsDe(compte).stream()
                .map(EvenementSecuriteVue::de)
                .toList());
        return "admin/compte-detail";
    }

    /**
     * Formulaire de creation d un administrateur. La garde est posee ICI, et non au seul
     * service : ce GET n appelle aucun service, et sans elle un administrateur ordinaire
     * verrait le formulaire avant d etre refuse a l envoi.
     */
    @GetMapping("/administrateurs/nouveau")
    @PreAuthorize("hasRole('SUPER_ADMINISTRATEUR')")
    public String nouvelAdministrateur(Model modele) {
        modele.addAttribute("formulaire", new CreationAdministrateurForm());
        modele.addAttribute("titre", msg("admin.administrateurs.nouveau.titre"));
        return "admin/administrateur-nouveau";
    }

    /** Meme garde qu au GET : un envoi invalide re-rend le formulaire avant tout appel au service. */
    @PostMapping("/administrateurs/nouveau")
    @PreAuthorize("hasRole('SUPER_ADMINISTRATEUR')")
    public String creerAdministrateur(
            @Valid @ModelAttribute("formulaire") CreationAdministrateurForm formulaire,
            BindingResult erreurs, Model modele, RedirectAttributes redirection) {
        if (!erreurs.hasErrors()) {
            try {
                Utilisateur cree = administrateurs.creerAdministrateur(formulaire.getEmail(),
                        formulaire.getPrenom(), formulaire.getNom(), formulaire.getFonction());
                redirection.addFlashAttribute("message", msg("admin.administrateurs.cree"));
                return "redirect:/admin/comptes/" + cree.getReference();
            } catch (RefusTraduit refus) {
                erreurs.rejectValue("email", "adresse.prise", msg(refus.getCleMessage()));
            }
        }
        modele.addAttribute("titre", msg("admin.administrateurs.nouveau.titre"));
        return "admin/administrateur-nouveau";
    }

    @PostMapping("/{reference}/lien-definition")
    public String renvoyerLien(@PathVariable UUID reference, RedirectAttributes redirection) {
        try {
            administrateurs.renvoyerLienDefinition(reference);
            redirection.addFlashAttribute("message", msg("admin.compte.lien.renvoye"));
        } catch (RefusTraduit refus) {
            redirection.addFlashAttribute("erreur", msg(refus.getCleMessage()));
        }
        return "redirect:/admin/comptes/" + reference;
    }

    @PostMapping("/{reference}/second-facteur/reinitialisation")
    public String reinitialiserSecondFacteur(@PathVariable UUID reference,
                                             RedirectAttributes redirection) {
        try {
            administrateurs.reinitialiserSecondFacteur(reference);
            redirection.addFlashAttribute("message", msg("admin.compte.mfa.reinitialise"));
        } catch (RefusTraduit refus) {
            redirection.addFlashAttribute("erreur", msg(refus.getCleMessage()));
        }
        return "redirect:/admin/comptes/" + reference;
    }


    @PostMapping("/{reference}/suspendre")
    public String suspendre(@PathVariable UUID reference,
                            @RequestParam(required = false) String motif,
                            RedirectAttributes redirection) {
        try {
            service.suspendre(reference, motif);
            redirection.addFlashAttribute("message", msg("admin.comptes.suspendu"));
        } catch (RefusTraduit refus) {
            // Le refus PORTE sa cle : le controleur traduit, sans table a tenir.
            redirection.addFlashAttribute("erreur", msg(refus.getCleMessage()));
        }
        return "redirect:/admin/comptes/" + reference;
    }

    @PostMapping("/{reference}/reactiver")
    public String reactiver(@PathVariable UUID reference, RedirectAttributes redirection) {
        try {
            service.reactiver(reference);
            redirection.addFlashAttribute("message", msg("admin.comptes.reactive"));
        } catch (RefusTraduit refus) {
            redirection.addFlashAttribute("erreur", msg(refus.getCleMessage()));
        }
        return "redirect:/admin/comptes/" + reference;
    }
}
