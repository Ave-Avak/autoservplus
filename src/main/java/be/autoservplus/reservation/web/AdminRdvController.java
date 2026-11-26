package be.autoservplus.reservation.web;

import be.autoservplus.common.exception.ConflitConcurrenceException;
import be.autoservplus.common.exception.RefusTraduit;
import be.autoservplus.common.exception.RegleMetierException;
import be.autoservplus.common.exception.TransitionRefusee;
import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.service.AdminRdvService;
import be.autoservplus.reservation.service.CreneauIndisponibleException;
import be.autoservplus.reservation.web.dto.DeplacementRdvForm;
import be.autoservplus.reservation.web.dto.MotifForm;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.format.annotation.DateTimeFormat;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.UUID;

/**
 * Actions administratives sur les rendez-vous depuis l espace garage.
 *
 * <p>La protection d URL {@code /admin/**} de {@code SecuriteConfig} filtre le role
 * ADMINISTRATEUR. {@code AdminRdvService} redouble par {@code @PreAuthorize} en
 * defense en profondeur. Le controleur ne duplique pas l annotation : le pattern
 * projet n en met pas sur les controleurs (verifie sur RdvController, VehiculeController).</p>
 *
 * <p>Les cinq transitions suivent le patron PRG : POST, message flash, puis
 * redirection. Confirmer, honorer et marquer absent reviennent à l'écran d'où part le
 * formulaire : la liste par défaut, le détail quand le champ {@code retour} vaut
 * {@code detail}. Toute autre valeur ramène à la liste, jamais à une adresse fournie
 * par la requête. Le refus et l'annulation par le garage exigent un motif : ils
 * s'appuient sur une page dédiée avec un formulaire, et aboutissent au détail, qui
 * montre le nouveau statut et le motif. Le déplacement aussi, sur une page qui
 * propose les heures libres du jour choisi.</p>
 */
@Controller
@RequestMapping("/admin/rendez-vous")
public class AdminRdvController {

    private static final String LISTE = "redirect:/admin/rendez-vous";
    private static final String RETOUR_DETAIL = "detail";

    private final AdminRdvService adminRdvs;
    private final MessageSource messages;

    public AdminRdvController(AdminRdvService adminRdvs, MessageSource messages) {
        this.adminRdvs = adminRdvs;
        this.messages = messages;
    }

    // --- liste ------------------------------------------------------------------------

    @GetMapping
    public String liste(Model modele) {
        modele.addAttribute("rendezVousJoursFeries", adminRdvs.rendezVousJoursFeries());
        modele.addAttribute("demandesEnAttente", adminRdvs.demandesEnAttente());
        modele.addAttribute("rendezVousDuJour", adminRdvs.rendezVousDuJour());
        modele.addAttribute("rendezVousPassesSansIssue", adminRdvs.rendezVousPassesSansIssue());
        return "admin/rendez-vous";
    }

    // --- agenda et detail -------------------------------------------------------------

    /** Dates au format ISO, celui que rend et relit un champ {@code type="date"}. */
    @GetMapping("/agenda")
    public String agenda(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
                         Model modele) {
        modele.addAttribute("agenda", adminRdvs.agenda(du, au));
        return "admin/rendez-vous-agenda";
    }

    /** Référence inconnue : 404 par GestionnaireErreursGlobal, comme partout. */
    @GetMapping("/{reference}")
    public String detail(@PathVariable UUID reference, Model modele) {
        modele.addAttribute("rdv", adminRdvs.vue(reference));
        return "admin/rendez-vous-detail";
    }

    // --- transitions sans motif -------------------------------------------------------

    @PostMapping("/{reference}/confirmer")
    public String confirmer(@AuthenticationPrincipal UserDetails admin,
                            @PathVariable UUID reference,
                            @RequestParam(required = false) String retour,
                            RedirectAttributes redirection) {
        return appliquerTransition(reference, retour, redirection,
                () -> adminRdvs.confirmer(reference),
                "admin.rdv.message.confirme");
    }

    @PostMapping("/{reference}/honorer")
    public String honorer(@AuthenticationPrincipal UserDetails admin,
                          @PathVariable UUID reference,
                          @RequestParam(required = false) String retour,
                          RedirectAttributes redirection) {
        return appliquerTransition(reference, retour, redirection,
                () -> adminRdvs.marquerHonore(reference),
                "admin.rdv.message.honore");
    }

    @PostMapping("/{reference}/absent")
    public String absent(@AuthenticationPrincipal UserDetails admin,
                         @PathVariable UUID reference,
                         @RequestParam(required = false) String retour,
                         RedirectAttributes redirection) {
        return appliquerTransition(reference, retour, redirection,
                () -> adminRdvs.marquerAbsent(reference),
                "admin.rdv.message.absent");
    }

    // --- refus (motif obligatoire) ----------------------------------------------------

    @GetMapping("/{reference}/refuser")
    public String afficherRefuser(@PathVariable UUID reference, Model modele) {
        return preparerFormulaireMotif(reference, new MotifForm(), modele, "admin/refuser");
    }

    @PostMapping("/{reference}/refuser")
    public String refuser(@AuthenticationPrincipal UserDetails admin,
                          @PathVariable UUID reference,
                          @Valid @ModelAttribute("formulaire") MotifForm formulaire,
                          BindingResult erreurs,
                          Model modele,
                          RedirectAttributes redirection) {

        if (erreurs.hasErrors()) {
            return preparerFormulaireMotif(reference, formulaire, modele, "admin/refuser");
        }
        return appliquerTransitionAvecMotif(reference, redirection,
                motif -> adminRdvs.refuser(reference, motif),
                formulaire.getMotif(),
                "admin.rdv.message.refuse");
    }

    // --- annulation par le garage (motif obligatoire) ---------------------------------

    @GetMapping("/{reference}/annuler")
    public String afficherAnnuler(@PathVariable UUID reference, Model modele) {
        return preparerFormulaireMotif(reference, new MotifForm(), modele, "admin/annuler");
    }

    @PostMapping("/{reference}/annuler")
    public String annuler(@AuthenticationPrincipal UserDetails admin,
                          @PathVariable UUID reference,
                          @Valid @ModelAttribute("formulaire") MotifForm formulaire,
                          BindingResult erreurs,
                          Model modele,
                          RedirectAttributes redirection) {

        if (erreurs.hasErrors()) {
            return preparerFormulaireMotif(reference, formulaire, modele, "admin/annuler");
        }
        return appliquerTransitionAvecMotif(reference, redirection,
                motif -> adminRdvs.annulerParLeGarage(reference, motif),
                formulaire.getMotif(),
                "admin.rdv.message.annule");
    }

    // --- déplacement par le garage (motif obligatoire) --------------------------------

    @GetMapping("/{reference}/modifier")
    public String afficherDeplacement(@PathVariable UUID reference,
                                      @ModelAttribute("formulaire") DeplacementRdvForm formulaire,
                                      Model modele, Locale langue, RedirectAttributes redirection) {
        return ecranDeplacement(reference, formulaire, modele, langue, redirection);
    }

    /** Fragment HTMX : les heures où le garage peut déplacer le rendez-vous. */
    @GetMapping("/{reference}/modifier/creneaux")
    public String creneauxDeplacement(@PathVariable UUID reference,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                      Model modele) {
        modele.addAttribute("creneaux", adminRdvs.creneauxPourDeplacer(reference, date));
        modele.addAttribute("aucunePrestation", false);
        return "reservation/fragments/creneaux :: liste";
    }

    @PostMapping("/{reference}/modifier")
    public String deplacer(@PathVariable UUID reference,
                           @ModelAttribute("formulaire") DeplacementRdvForm formulaire,
                           Model modele, Locale langue, RedirectAttributes redirection) {
        if (formulaire.getMotif() == null || formulaire.getMotif().isBlank()) {
            modele.addAttribute("erreurMotif", messages.getMessage("admin.rdv.modification.motif-requis", null, langue));
            return ecranDeplacement(reference, formulaire, modele, langue, redirection);
        }
        if (formulaire.getMotif().trim().length() > MotifForm.LONGUEUR_MAX) {
            modele.addAttribute("erreurMotif", messages.getMessage("admin.rdv.motif.longueur", null, langue));
            return ecranDeplacement(reference, formulaire, modele, langue, redirection);
        }
        Instant debut;
        try {
            debut = Instant.parse(formulaire.getDebut() == null ? "" : formulaire.getDebut());
        } catch (DateTimeParseException e) {
            modele.addAttribute("erreurHeure", messages.getMessage("admin.rdv.modification.heure-requise", null, langue));
            return ecranDeplacement(reference, formulaire, modele, langue, redirection);
        }
        try {
            long version = formulaire.getVersion() == null ? -1 : formulaire.getVersion();
            Rdv rdv = adminRdvs.deplacer(reference, debut, formulaire.getMotif(), version);
            redirection.addFlashAttribute("message",
                    messages.getMessage("admin.rdv.modification.fait", new Object[]{rdv.getNumero()}, langue));
        } catch (CreneauIndisponibleException e) {
            modele.addAttribute("erreurHeure",
                    messages.getMessage("admin.rdv.modification.creneau-indisponible", null, langue));
            return ecranDeplacement(reference, formulaire, modele, langue, redirection);
        } catch (ConflitConcurrenceException | IllegalStateException e) {
            // Page périmée, ou rendez-vous terminé ou commencé entre-temps : le détail
            // montre son état réel.
            redirection.addFlashAttribute("erreur", motifDuRefus(e, langue));
        }
        return versDetail(reference);
    }

    /**
     * Écran de déplacement, ou retour au détail quand le rendez-vous ne se déplace plus.
     * Le jour proposé est celui du rendez-vous s'il est à venir, sinon aujourd'hui.
     */
    private String ecranDeplacement(UUID reference, DeplacementRdvForm formulaire, Model modele,
                                    Locale langue, RedirectAttributes redirection) {
        var rdv = adminRdvs.vue(reference);
        if (!rdv.peutDeplacer()) {
            redirection.addFlashAttribute("erreur",
                    messages.getMessage("admin.rdv.modification.impossible", null, langue));
            return versDetail(reference);
        }
        LocalDate aujourdhui = adminRdvs.aujourdhui();
        if (formulaire.getDate() == null) {
            LocalDate jourDuRdv = rdv.debut().toLocalDate();
            formulaire.setDate(jourDuRdv.isBefore(aujourdhui) ? aujourdhui : jourDuRdv);
        }
        if (formulaire.getVersion() == null) {
            formulaire.setVersion(adminRdvs.version(reference));
        }
        modele.addAttribute("rdv", rdv);
        modele.addAttribute("formulaire", formulaire);
        modele.addAttribute("dateMin", aujourdhui);
        modele.addAttribute("creneaux", adminRdvs.creneauxPourDeplacer(reference, formulaire.getDate()));
        modele.addAttribute("aucunePrestation", false);
        return "admin/rendez-vous-deplacer";
    }

    // --- helpers privees --------------------------------------------------------------

    /** Reference inconnue : 404 par GestionnaireErreursGlobal, comme partout. */
    private String preparerFormulaireMotif(UUID reference, MotifForm formulaire, Model modele, String vue) {
        modele.addAttribute("rdv", adminRdvs.vue(reference));
        modele.addAttribute("formulaire", formulaire);
        return vue;
    }

    private String appliquerTransition(UUID reference, String retour, RedirectAttributes redirection,
                                       java.util.function.Supplier<Rdv> action,
                                       String cleSucces) {
        Locale langue = LocaleContextHolder.getLocale();
        try {
            Rdv rdv = action.get();
            redirection.addFlashAttribute("message",
                    messages.getMessage(cleSucces, new Object[]{rdv.getNumero()}, langue));
        } catch (ConflitConcurrenceException | IllegalStateException | RegleMetierException e) {
            // Machine à états du domaine (RM-10), page périmée ou refus métier.
            redirection.addFlashAttribute("erreur", motifDuRefus(e, langue));
        }
        return RETOUR_DETAIL.equals(retour) ? versDetail(reference) : LISTE;
    }

    private String appliquerTransitionAvecMotif(UUID reference, RedirectAttributes redirection,
                                                java.util.function.Function<String, Rdv> action,
                                                String motif,
                                                String cleSucces) {
        Locale langue = LocaleContextHolder.getLocale();
        try {
            Rdv rdv = action.apply(motif);
            redirection.addFlashAttribute("message",
                    messages.getMessage(cleSucces, new Object[]{rdv.getNumero()}, langue));
        } catch (ConflitConcurrenceException | IllegalStateException e) {
            redirection.addFlashAttribute("erreur", motifDuRefus(e, langue));
        }
        return versDetail(reference);
    }

    /**
     * Message d'un refus dans la langue de l'écran. Une référence inconnue n'arrive pas
     * ici : elle remonte en 404. Un refus sans clé reçoit le message générique de son
     * genre, la phrase française de l'exception restant aux journaux.
     */
    private String motifDuRefus(RuntimeException refus, Locale langue) {
        String cle;
        Object[] arguments = null;
        if (refus instanceof TransitionRefusee transition) {
            cle = transition.getCleMessage();
        } else if (refus instanceof RefusTraduit traduit) {
            cle = traduit.getCleMessage();
            arguments = traduit.getArguments();
        } else if (refus instanceof ConflitConcurrenceException) {
            cle = "erreur.409.message";
        } else {
            cle = "admin.rdv.erreur.transition";
        }
        return messages.getMessage(cle, arguments, langue);
    }

    private static String versDetail(UUID reference) {
        return "redirect:/admin/rendez-vous/" + reference;
    }
}
