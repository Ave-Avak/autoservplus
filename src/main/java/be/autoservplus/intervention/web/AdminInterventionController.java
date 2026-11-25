package be.autoservplus.intervention.web;

import be.autoservplus.catalogue.domain.ChoixPieces;
import be.autoservplus.catalogue.domain.ChoixPiecesRefuseException;
import be.autoservplus.common.exception.ConflitConcurrenceException;
import be.autoservplus.common.exception.RefusTraduit;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.facturation.domain.MoyenEncaissement;
import be.autoservplus.intervention.domain.Intervention;
import be.autoservplus.intervention.domain.QuantiteLigneRefuseeException;
import be.autoservplus.intervention.service.ClotureRefuseeException;
import be.autoservplus.intervention.service.InterventionService;
import be.autoservplus.intervention.service.dto.ClotureIntervention;
import be.autoservplus.intervention.web.dto.InterventionVueAdmin;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Ecrans d administration des interventions sous /admin/interventions.
 *
 * <p>La protection d URL {@code /admin/**} filtre le role ADMINISTRATEUR.
 * {@link InterventionService} redouble par {@code @PreAuthorize} en defense
 * en profondeur. Pattern PRG + flash, catch des exceptions du service, comme
 * {@code AdminRdvController}.</p>
 */
@Controller
@RequestMapping("/admin/interventions")
public class AdminInterventionController {

    private final InterventionService service;
    private final MessageSource messages;

    public AdminInterventionController(InterventionService service, MessageSource messages) {
        this.service = service;
        this.messages = messages;
    }

    @GetMapping
    public String liste(Model modele) {
        modele.addAttribute("interventions", service.interventionsEnCours());
        return "admin/interventions";
    }

    /** Une intervention inconnue répond 404 par la page d'erreur commune, et non par un renvoi à la liste. */
    @GetMapping("/{reference}")
    public String detail(@PathVariable UUID reference, Model modele) {
        InterventionVueAdmin vue = service.vueAdmin(reference);
        modele.addAttribute("intervention", vue);
        modele.addAttribute("prestations", service.prestationsActives());
        modele.addAttribute("pieces", service.piecesAjoutables());
        modele.addAttribute("facture", service.factureAtelier(reference).orElse(null));
        // Avertissement à la réception, avant que la clôture ne refuse (Q-H-42).
        modele.addAttribute("compteSansAdresse", service.compteSansAdresse(reference).orElse(null));
        return "admin/intervention-detail";
    }

    // --- transitions ----------------------------------------------------------------

    @PostMapping("/{reference}/demarrer")
    public String demarrer(@AuthenticationPrincipal UserDetails admin,
                           @PathVariable UUID reference,
                           RedirectAttributes redirection) {
        return appliquerTransition(reference, redirection,
                () -> service.demarrer(reference), "admin.intervention.message.demarree");
    }

    @PostMapping("/{reference}/suspendre")
    public String suspendre(@AuthenticationPrincipal UserDetails admin,
                            @PathVariable UUID reference,
                            RedirectAttributes redirection) {
        return appliquerTransition(reference, redirection,
                () -> service.suspendre(reference), "admin.intervention.message.suspendue");
    }

    @PostMapping("/{reference}/reprendre")
    public String reprendre(@AuthenticationPrincipal UserDetails admin,
                            @PathVariable UUID reference,
                            RedirectAttributes redirection) {
        return appliquerTransition(reference, redirection,
                () -> service.reprendre(reference), "admin.intervention.message.reprise");
    }

    @PostMapping("/{reference}/terminer")
    public String terminer(@AuthenticationPrincipal UserDetails admin,
                           @PathVariable UUID reference,
                           RedirectAttributes redirection) {
        try {
            return appliquer(reference, redirection, () -> confirmationDeCloture(service.terminer(reference)));
        } catch (ClotureRefuseeException refus) {
            redirection.addFlashAttribute("erreur", message(refus.getCleMessage()));
            // Le lien mène à la modification du compte, qui revient ici par la référence (Q-H-42).
            redirection.addFlashAttribute("compteACompleter", refus.getCompteACompleter());
            return "redirect:/admin/interventions/" + reference;
        }
    }

    private String confirmationDeCloture(ClotureIntervention cloture) {
        StringBuilder confirmation = new StringBuilder(message("admin.intervention.message.terminee", cloture.numero()));
        if (cloture.numeroFacture() != null) {
            confirmation.append(' ').append(message("admin.intervention.message.facture-emise", cloture.numeroFacture()));
        } else {
            confirmation.append(' ').append(message(cloture.neeDUneCommande()
                    ? "admin.intervention.message.sans-facture-complementaire"
                    : "admin.intervention.message.sans-facture"));
        }
        if (!cloture.piecesRameneesAZero().isEmpty()) {
            // Le garage ne lit pas le journal : la confirmation dit quel stock vérifier (Q-H-31).
            confirmation.append(' ').append(message("admin.intervention.message.stock-ramene",
                    String.join(", ", cloture.piecesRameneesAZero())));
        }
        return confirmation.toString();
    }

    /**
     * Note de crédit totale de la facture d'atelier (Q-H-14, Q-H-50). La précision saisie
     * revient dans le formulaire quand elle est refusée.
     */
    @PostMapping("/{reference}/facture/annulation")
    public String annulerFacture(@AuthenticationPrincipal UserDetails admin,
                                 @PathVariable UUID reference,
                                 @RequestParam(required = false) String precision,
                                 RedirectAttributes redirection) {
        try {
            String avoir = service.annulerFactureAtelier(reference, precision);
            redirection.addFlashAttribute("message", message("admin.intervention.message.facture-annulee", avoir));
        } catch (RefusTraduit refus) {
            redirection.addFlashAttribute("erreur", message(refus.getCleMessage()));
            redirection.addFlashAttribute("precisionSaisie", precision);
        }
        return "redirect:/admin/interventions/" + reference;
    }

    /** Règlement au comptoir de la facture d'atelier (RM-20 révisée, Q-H-43 à Q-H-45). */
    @PostMapping("/{reference}/facture/encaissement")
    public String encaisserFacture(@AuthenticationPrincipal UserDetails admin,
                                   @PathVariable UUID reference,
                                   @RequestParam(required = false) MoyenEncaissement moyen,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                   LocalDate date,
                                   RedirectAttributes redirection) {
        if (moyen == null || date == null) {
            redirection.addFlashAttribute("erreur", message("encaissement.erreur.champs"));
            return "redirect:/admin/interventions/" + reference;
        }
        try {
            String facture = service.encaisserFactureAtelier(reference, moyen, date);
            redirection.addFlashAttribute("message", message("admin.intervention.message.encaissement", facture));
        } catch (RefusTraduit refus) {
            redirection.addFlashAttribute("erreur", message(refus.getCleMessage(), refus.getArguments()));
        }
        return "redirect:/admin/interventions/" + reference;
    }

    /** Annulation du règlement, motif obligatoire, rendu au formulaire quand il est refusé. */
    @PostMapping("/{reference}/facture/encaissement/annulation")
    public String annulerEncaissement(@AuthenticationPrincipal UserDetails admin,
                                      @PathVariable UUID reference,
                                      @RequestParam(required = false) String motif,
                                      RedirectAttributes redirection) {
        try {
            String facture = service.annulerEncaissement(reference, motif);
            redirection.addFlashAttribute("message", message("admin.intervention.message.encaissement-annule", facture));
        } catch (RefusTraduit refus) {
            redirection.addFlashAttribute("erreur", message(refus.getCleMessage(), refus.getArguments()));
            redirection.addFlashAttribute("motifSaisi", motif);
        }
        return "redirect:/admin/interventions/" + reference;
    }

    @PostMapping("/{reference}/annuler")
    public String annuler(@AuthenticationPrincipal UserDetails admin,
                          @PathVariable UUID reference,
                          RedirectAttributes redirection) {
        return appliquerTransition(reference, redirection,
                () -> service.annuler(reference), "admin.intervention.message.annulee");
    }

    // --- commentaire et lignes ------------------------------------------------------

    @PostMapping("/{reference}/commentaire")
    public String modifierCommentaire(@AuthenticationPrincipal UserDetails admin,
                                      @PathVariable UUID reference,
                                      @RequestParam(required = false) String commentaire,
                                      RedirectAttributes redirection) {
        try {
            service.modifierCommentaireAdmin(reference, commentaire);
            redirection.addFlashAttribute("message", message("admin.intervention.message.commentaire"));
        } catch (ConflitConcurrenceException e) {
            redirection.addFlashAttribute("erreur", message("erreur.409.message"));
        } catch (IllegalStateException e) {
            redirection.addFlashAttribute("erreur", message("admin.intervention.erreur.etat"));
        }
        return "redirect:/admin/interventions/" + reference;
    }

    /** Note interne de l'atelier (A16), modifiable dans tous les statuts. */
    @PostMapping("/{reference}/note-interne")
    public String modifierNoteInterne(@PathVariable UUID reference,
                                      @RequestParam(required = false) String noteInterne,
                                      RedirectAttributes redirection) {
        try {
            service.modifierNoteInterne(reference, noteInterne);
            redirection.addFlashAttribute("message", message("admin.intervention.note.enregistree"));
        } catch (IllegalArgumentException e) {
            redirection.addFlashAttribute("erreur", message("admin.intervention.note.trop-longue",
                    Intervention.LONGUEUR_NOTE_INTERNE));
        } catch (ConflitConcurrenceException e) {
            redirection.addFlashAttribute("erreur", message("erreur.409.message"));
        }
        return "redirect:/admin/interventions/" + reference;
    }

    @PostMapping("/{reference}/lignes")
    public String ajouterLigne(@AuthenticationPrincipal UserDetails admin,
                               @PathVariable UUID reference,
                               @RequestParam UUID prestation,
                               @RequestParam BigDecimal quantite,
                               @RequestParam(required = false) ChoixPieces choix,
                               RedirectAttributes redirection) {
        try {
            service.ajouterLigneMainOeuvre(reference, prestation, quantite, choix);
            redirection.addFlashAttribute("message", message("admin.intervention.message.ligne-ajoutee"));
        } catch (RessourceIntrouvableException e) {
            // La prestation choisie dans la liste a disparu du catalogue entre-temps.
            redirection.addFlashAttribute("erreur", message("admin.intervention.erreur.prestation"));
        } catch (QuantiteLigneRefuseeException e) {
            // Décimale pour un forfait, nulle, ou trop précise pour des heures (Q-H-37).
            redirection.addFlashAttribute("erreur", message(e.getCleMessage()));
        } catch (ChoixPiecesRefuseException e) {
            // Choix des pièces absent sur un forfait qui propose la pose seule : le formulaire
            // présélectionne les pièces fournies, seul un envoi sans le champ arrive ici.
            redirection.addFlashAttribute("erreur", message(e.getCleMessage(), e.getArguments()));
        } catch (ConflitConcurrenceException e) {
            redirection.addFlashAttribute("erreur", message("erreur.409.message"));
        } catch (IllegalArgumentException e) {
            redirection.addFlashAttribute("erreur", message("admin.intervention.erreur.quantite"));
        } catch (IllegalStateException e) {
            redirection.addFlashAttribute("erreur", message("admin.intervention.erreur.etat"));
        }
        return "redirect:/admin/interventions/" + reference;
    }

    /** Pièce posée en cours d'intervention (RM-14). Admise sans stock : la ligne dira « à commander ». */
    @PostMapping("/{reference}/lignes/piece")
    public String ajouterPiece(@AuthenticationPrincipal UserDetails admin,
                               @PathVariable UUID reference,
                               @RequestParam UUID piece,
                               @RequestParam int quantite,
                               RedirectAttributes redirection) {
        try {
            service.ajouterLignePiece(reference, piece, quantite);
            redirection.addFlashAttribute("message", message("admin.intervention.message.ligne-ajoutee"));
        } catch (RessourceIntrouvableException e) {
            // La pièce choisie a été retirée de la vente entre l'affichage et l'envoi.
            redirection.addFlashAttribute("erreur", message("admin.intervention.erreur.piece"));
        } catch (QuantiteLigneRefuseeException e) {
            redirection.addFlashAttribute("erreur", message(e.getCleMessage()));
        } catch (ConflitConcurrenceException e) {
            redirection.addFlashAttribute("erreur", message("erreur.409.message"));
        } catch (IllegalArgumentException e) {
            redirection.addFlashAttribute("erreur", message("admin.intervention.erreur.quantite"));
        } catch (IllegalStateException e) {
            redirection.addFlashAttribute("erreur", message("admin.intervention.erreur.etat"));
        }
        return "redirect:/admin/interventions/" + reference;
    }

    @PostMapping("/{reference}/lignes/{ligneId}/supprimer")
    public String supprimerLigne(@AuthenticationPrincipal UserDetails admin,
                                 @PathVariable UUID reference,
                                 @PathVariable Long ligneId,
                                 RedirectAttributes redirection) {
        try {
            service.retirerLigne(reference, ligneId);
            redirection.addFlashAttribute("message", message("admin.intervention.message.ligne-retiree"));
        } catch (ConflitConcurrenceException e) {
            redirection.addFlashAttribute("erreur", message("erreur.409.message"));
        } catch (IllegalStateException e) {
            redirection.addFlashAttribute("erreur", message("admin.intervention.erreur.etat"));
        }
        return "redirect:/admin/interventions/" + reference;
    }

    // --- helpers privees ------------------------------------------------------------

    private String appliquerTransition(UUID reference, RedirectAttributes redirection,
                                       Supplier<Intervention> action, String cleSucces) {
        return appliquer(reference, redirection, () -> message(cleSucces, action.get().getNumero()));
    }

    /** @param action exécute la transition et rend la confirmation à afficher */
    private String appliquer(UUID reference, RedirectAttributes redirection, Supplier<String> action) {
        try {
            redirection.addFlashAttribute("message", action.get());
        } catch (ConflitConcurrenceException e) {
            redirection.addFlashAttribute("erreur", message("erreur.409.message"));
        } catch (IllegalStateException e) {
            // Machine à états du domaine : la phrase française reste aux journaux.
            redirection.addFlashAttribute("erreur", message("admin.intervention.erreur.etat"));
        }
        return "redirect:/admin/interventions/" + reference;
    }

    private String message(String cle, Object... arguments) {
        return messages.getMessage(cle, arguments, LocaleContextHolder.getLocale());
    }
}
