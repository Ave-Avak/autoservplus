package be.autoservplus.reservation.web;

import be.autoservplus.catalogue.domain.ChoixPiecesRefuseException;
import be.autoservplus.common.exception.RefusTraduit;
import be.autoservplus.common.exception.TransitionRefusee;
import be.autoservplus.reservation.domain.StatutRdv;
import be.autoservplus.reservation.service.AucunePrestationChoisieException;
import be.autoservplus.reservation.service.CreneauIndisponibleException;
import be.autoservplus.reservation.service.ExportAgendaService;
import be.autoservplus.reservation.service.LimiteDemandesEnAttenteException;
import be.autoservplus.reservation.service.PrestationIndisponibleException;
import be.autoservplus.reservation.service.RdvService;
import be.autoservplus.reservation.service.VehiculeService;
import be.autoservplus.reservation.service.dto.FichierAgenda;
import be.autoservplus.reservation.web.dto.ModificationRdvForm;
import be.autoservplus.reservation.web.dto.RdvForm;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Prise et suivi des rendez-vous par le membre (F16, F17).
 *
 * <p>Le formulaire est une page unique : vehicule, prestations, date, puis les heures
 * disponibles sont rechargees par HTMX a chaque changement de date ou de prestation.
 * L identite du membre vient du contexte de securite.</p>
 */
@Controller
@RequestMapping("/mes-rendez-vous")
public class RdvController {

    private final RdvService rdvs;
    private final VehiculeService vehicules;
    private final ExportAgendaService agendas;
    private final MessageSource messages;

    public RdvController(RdvService rdvs, VehiculeService vehicules, ExportAgendaService agendas,
                         MessageSource messages) {
        this.rdvs = rdvs;
        this.vehicules = vehicules;
        this.agendas = agendas;
        this.messages = messages;
    }

    @GetMapping
    public String lister(@AuthenticationPrincipal UserDetails membre, Model modele) {
        modele.addAttribute("rendezVous", rdvs.vuesDuMembre(membre.getUsername()));
        return "reservation/rdv-liste";
    }

    @GetMapping("/nouveau")
    public String afficherFormulaire(@AuthenticationPrincipal UserDetails membre,
                                     @RequestParam(required = false) UUID vehicule,
                                     @RequestParam(required = false) String prestation,
                                     Model modele) {
        RdvForm formulaire = new RdvForm();
        formulaire.setVehicule(vehicule);
        rdvs.prestationPreselectionnee(prestation)
                .ifPresent(reference -> formulaire.setPrestations(new ArrayList<>(List.of(reference))));
        formulaire.setDate(rdvs.premierJourReservable());
        return retourFormulaire(membre, formulaire, modele);
    }

    /** Fragment HTMX : les heures disponibles pour une date et des prestations. */
    @GetMapping("/nouveau/creneaux")
    public String creneaux(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                           @RequestParam(required = false) List<UUID> prestations,
                           Model modele) {
        try {
            modele.addAttribute("creneaux", rdvs.creneauxPour(date, prestations));
        } catch (RefusTraduit refus) {
            modele.addAttribute("creneaux", List.of());
            modele.addAttribute("erreurCreneaux", traduire(refus, LocaleContextHolder.getLocale()));
        }
        modele.addAttribute("aucunePrestation", prestations == null || prestations.isEmpty());
        return "reservation/fragments/creneaux :: liste";
    }

    @PostMapping("/nouveau")
    public String reserver(@AuthenticationPrincipal UserDetails membre,
                           @Valid @ModelAttribute("formulaire") RdvForm formulaire,
                           BindingResult erreurs,
                           Model modele,
                           RedirectAttributes redirection) {

        exigerLesChoixDesPieces(formulaire, erreurs);
        if (erreurs.hasErrors()) {
            return retourFormulaire(membre, formulaire, modele);
        }

        Instant debut;
        try {
            debut = Instant.parse(formulaire.getDebut());
        } catch (DateTimeParseException e) {
            erreurs.rejectValue("debut", "rdv.formulaire.heure.liste", "Choisissez une heure dans la liste.");
            return retourFormulaire(membre, formulaire, modele);
        }

        try {
            var rdv = rdvs.reserver(membre.getUsername(), formulaire.getVehicule(),
                    formulaire.getPrestations(), formulaire.getChoixPieces(), debut, formulaire.getCommentaire());
            // Le statut, et non le réglage lu à part : une demande confirmée d'office
            // n'attend plus le garage, une demande en attente l'attend.
            String cle = rdv.getStatut() == StatutRdv.CONFIRME
                    ? "rdv.reservation.confirmee" : "rdv.reservation.en-attente";
            redirection.addFlashAttribute("message", messages.getMessage(cle,
                    new Object[]{rdv.getNumero()}, LocaleContextHolder.getLocale()));
            return "redirect:/mes-rendez-vous";
        } catch (LimiteDemandesEnAttenteException e) {
            // Erreur GLOBALE, et non sous « prestations » ou elle etait ancree par
            // heritage du refactor reservation. Le plafond porte sur le COMPTE : le
            // membre a trop de demandes en attente, quelles que soient les
            // prestations choisies. L ancrer sous un champ lui disait de corriger un
            // choix qui n est pas en cause, et changer ce choix n aurait rien change.
            erreurs.reject(e.getCleMessage(), e.getArguments(), e.getMessage());
            return retourFormulaire(membre, formulaire, modele);
        } catch (ChoixPiecesRefuseException e) {
            // La prestation a changé entre l'affichage et l'envoi : l'erreur va sous son groupe.
            erreurs.rejectValue(champChoix(e.getPrestation()), e.getCleMessage(), e.getArguments(), e.getMessage());
            return retourFormulaire(membre, formulaire, modele);
        } catch (PrestationIndisponibleException | AucunePrestationChoisieException e) {
            // Ces deux-la visent bien le choix de prestations : l erreur s ancre sous
            // ce champ, ou le membre peut agir.
            erreurs.rejectValue("prestations", e.getCleMessage(), e.getArguments(), e.getMessage());
            return retourFormulaire(membre, formulaire, modele);
        } catch (RefusTraduit e) {
            // Créneau indisponible (RM-08) et tout refus futur : sous le champ de l'heure.
            // Le routage se fait par type, et non par le code de règle, qui peut manquer.
            erreurs.rejectValue("debut", e.getCleMessage(), e.getArguments(), e.getMessage());
            return retourFormulaire(membre, formulaire, modele);
        }
    }

    /**
     * Un forfait coché qui propose un prix pose seule attend la réponse du membre : rien n'est
     * présélectionné, pour que le prix retenu soit celui qu'il a choisi. Chaque choix manquant
     * s'ancre sous son groupe de boutons, et le résumé en tête du formulaire les reprend.
     */
    private void exigerLesChoixDesPieces(RdvForm formulaire, BindingResult erreurs) {
        rdvs.prestationsAvecChoixDesPieces(formulaire.getPrestations()).forEach((reference, libelle) -> {
            if (formulaire.getChoixPieces().get(reference) == null) {
                erreurs.rejectValue(champChoix(reference), "choix-pieces.erreur.manquant",
                        new Object[]{libelle}, "Choisissez qui fournit les pièces.");
            }
        });
    }

    private static String champChoix(UUID prestation) {
        return "choixPieces[" + prestation + "]";
    }

    @GetMapping("/{reference}")
    public String detail(@AuthenticationPrincipal UserDetails membre,
                         @PathVariable UUID reference,
                         Model modele) {
        var rdv = rdvs.vueDuMembre(reference, membre.getUsername());
        modele.addAttribute("rdv", rdv);
        return "reservation/rdv-detail";
    }

    /**
     * Fichier iCalendar du rendez-vous, pour l agenda personnel du membre (F38).
     *
     * <p>{@code Content-Disposition: attachment} et non {@code inline} : le fichier
     * n a aucun sens affiche dans un navigateur, il doit etre remis a l application
     * de calendrier du systeme. L appartenance et l etat CONFIRME sont verifies par
     * le service, qui repond {@code 404} dans les deux cas : jamais {@code 403},
     * conformement au patron des autres telechargements du projet.</p>
     */
    @GetMapping(value = "/{reference}/agenda.ics", produces = "text/calendar;charset=UTF-8")
    public ResponseEntity<byte[]> agenda(@AuthenticationPrincipal UserDetails membre,
                                         @PathVariable UUID reference,
                                         Locale langue) {
        FichierAgenda fichier = agendas.pourLeMembre(reference, membre.getUsername(), langue);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fichier.nomFichier()).build().toString())
                .body(fichier.octets());
    }

    @PostMapping("/{reference}/annuler")
    public String annuler(@AuthenticationPrincipal UserDetails membre,
                          @PathVariable UUID reference,
                          RedirectAttributes redirection) {
        try {
            var rdv = rdvs.annuler(reference, membre.getUsername());
            redirection.addFlashAttribute("message", messages.getMessage("rdv.annulation.confirmee",
                    new Object[]{rdv.getNumero()}, LocaleContextHolder.getLocale()));
        } catch (RefusTraduit refus) {
            redirection.addFlashAttribute("erreur", traduire(refus, LocaleContextHolder.getLocale()));
        } catch (TransitionRefusee refus) {
            redirection.addFlashAttribute("erreur",
                    messages.getMessage(refus.getCleMessage(), null, LocaleContextHolder.getLocale()));
        }
        return "redirect:/mes-rendez-vous/" + reference;
    }

    // --- déplacement -----------------------------------------------------------------

    /**
     * Écran de déplacement. Un rendez-vous qui ne se déplace plus en ligne (délai
     * dépassé, rendez-vous terminé) renvoie au détail avec le motif, sans page d'erreur.
     */
    @GetMapping("/{reference}/modifier")
    public String afficherModification(@AuthenticationPrincipal UserDetails membre,
                                       @PathVariable UUID reference,
                                       @ModelAttribute("formulaire") ModificationRdvForm formulaire,
                                       Model modele, Locale langue,
                                       RedirectAttributes redirection) {
        return retourModificationOuDetail(membre, reference, formulaire, modele, langue, redirection);
    }

    /** Fragment HTMX : les heures où déplacer le rendez-vous, un jour donné. */
    @GetMapping("/{reference}/modifier/creneaux")
    public String creneauxModification(@AuthenticationPrincipal UserDetails membre,
                                       @PathVariable UUID reference,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                       Model modele) {
        modele.addAttribute("creneaux", rdvs.creneauxPourModifier(reference, membre.getUsername(), date));
        modele.addAttribute("aucunePrestation", false);
        return "reservation/fragments/creneaux :: liste";
    }

    @PostMapping("/{reference}/modifier")
    public String modifier(@AuthenticationPrincipal UserDetails membre,
                           @PathVariable UUID reference,
                           @ModelAttribute("formulaire") ModificationRdvForm formulaire,
                           Model modele, Locale langue,
                           RedirectAttributes redirection) {
        Instant debut;
        try {
            debut = Instant.parse(formulaire.getDebut() == null ? "" : formulaire.getDebut());
        } catch (DateTimeParseException e) {
            modele.addAttribute("erreurHeure", messages.getMessage("rdv.modification.heure-requise", null, langue));
            return retourModificationOuDetail(membre, reference, formulaire, modele, langue, redirection);
        }
        try {
            long version = formulaire.getVersion() == null ? -1 : formulaire.getVersion();
            var rdv = rdvs.modifier(reference, membre.getUsername(), debut, version);
            String cle = rdv.getStatut() == StatutRdv.CONFIRME
                    ? "rdv.modification.confirmee" : "rdv.modification.en-attente";
            redirection.addFlashAttribute("message", messages.getMessage(cle, new Object[]{rdv.getNumero()}, langue));
            return "redirect:/mes-rendez-vous/" + reference;
        } catch (CreneauIndisponibleException e) {
            // Avant le cas général : un créneau pris se corrige sur le même écran.
            modele.addAttribute("erreurHeure",
                    messages.getMessage("rdv.modification.creneau-indisponible", null, langue));
            return retourModificationOuDetail(membre, reference, formulaire, modele, langue, redirection);
        } catch (RefusTraduit refus) {
            // Page périmée ou délai dépassé : le détail montre l'état réel du rendez-vous.
            redirection.addFlashAttribute("erreur", traduire(refus, langue));
            return "redirect:/mes-rendez-vous/" + reference;
        }
    }

    /**
     * Rend l'écran de déplacement, ou renvoie au détail avec le motif quand le
     * rendez-vous ne se déplace plus en ligne (délai dépassé, rendez-vous terminé).
     */
    private String retourModificationOuDetail(UserDetails membre, UUID reference, ModificationRdvForm formulaire,
                                              Model modele, Locale langue, RedirectAttributes redirection) {
        try {
            var vue = rdvs.vueModification(reference, membre.getUsername());
            if (formulaire.getDate() == null) {
                formulaire.setDate(rdvs.premierJourReservable());
            }
            modele.addAttribute("vue", vue);
            modele.addAttribute("formulaire", formulaire);
            modele.addAttribute("dateMin", rdvs.premierJourReservable());
            modele.addAttribute("dateMax", rdvs.dernierJourReservable());
            modele.addAttribute("creneaux",
                    rdvs.creneauxPourModifier(reference, membre.getUsername(), formulaire.getDate()));
            modele.addAttribute("aucunePrestation", false);
            return "reservation/rdv-modifier";
        } catch (RefusTraduit refus) {
            redirection.addFlashAttribute("erreur", traduire(refus, langue));
            return "redirect:/mes-rendez-vous/" + reference;
        }
    }

    /**
     * Le refus porte sa clé et ses valeurs. Un message sans valeur n'est pas passé par
     * MessageFormat, qui avalerait ses apostrophes simples.
     */
    private String traduire(RefusTraduit refus, Locale langue) {
        return messages.getMessage(refus.getCleMessage(), refus.getArguments(), langue);
    }

    private String retourFormulaire(UserDetails membre, RdvForm formulaire, Model modele) {
        modele.addAttribute("formulaire", formulaire);
        modele.addAttribute("vehicules", vehicules.vuesDuMembre(membre.getUsername()));
        modele.addAttribute("prestationsParCategorie", rdvs.prestationsProposees());
        modele.addAttribute("dateMin", rdvs.premierJourReservable());
        modele.addAttribute("dateMax", rdvs.dernierJourReservable());
        modele.addAttribute("conditions", rdvs.conditions());
        boolean aucune = formulaire.getPrestations() == null || formulaire.getPrestations().isEmpty();
        modele.addAttribute("aucunePrestation", aucune);
        modele.addAttribute("creneaux", aucune ? List.of() : rdvs.creneauxPour(formulaire.getDate(), formulaire.getPrestations()));
        return "reservation/rdv-formulaire";
    }
}