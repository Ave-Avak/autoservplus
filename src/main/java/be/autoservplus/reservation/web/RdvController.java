package be.autoservplus.reservation.web;

import be.autoservplus.common.exception.RegleMetierException;
import be.autoservplus.reservation.service.AucunePrestationChoisieException;
import be.autoservplus.reservation.service.ExportAgendaService;
import be.autoservplus.reservation.service.LimiteDemandesEnAttenteException;
import be.autoservplus.reservation.service.PrestationIndisponibleException;
import be.autoservplus.reservation.service.RdvService;
import be.autoservplus.reservation.service.VehiculeService;
import be.autoservplus.reservation.service.dto.FichierAgenda;
import be.autoservplus.reservation.web.dto.RdvForm;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
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

    public RdvController(RdvService rdvs, VehiculeService vehicules, ExportAgendaService agendas) {
        this.rdvs = rdvs;
        this.vehicules = vehicules;
        this.agendas = agendas;
    }

    @GetMapping
    public String lister(@AuthenticationPrincipal UserDetails membre, Model modele) {
        modele.addAttribute("titre", "Mes rendez-vous");
        modele.addAttribute("rendezVous", rdvs.vuesDuMembre(membre.getUsername()));
        return "reservation/rdv-liste";
    }

    @GetMapping("/nouveau")
    public String afficherFormulaire(@AuthenticationPrincipal UserDetails membre,
                                     @RequestParam(required = false) UUID vehicule,
                                     Model modele) {
        RdvForm formulaire = new RdvForm();
        formulaire.setVehicule(vehicule);
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
        } catch (RegleMetierException e) {
            modele.addAttribute("creneaux", List.of());
            modele.addAttribute("erreurCreneaux", e.getMessage());
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

        if (erreurs.hasErrors()) {
            return retourFormulaire(membre, formulaire, modele);
        }

        Instant debut;
        try {
            debut = Instant.parse(formulaire.getDebut());
        } catch (DateTimeParseException e) {
            erreurs.addError(new FieldError("formulaire", "debut", "Choisissez une heure dans la liste."));
            return retourFormulaire(membre, formulaire, modele);
        }

        try {
            var rdv = rdvs.reserver(membre.getUsername(), formulaire.getVehicule(),
                    formulaire.getPrestations(), debut, formulaire.getCommentaire());
            redirection.addFlashAttribute("message",
                    "Votre demande " + rdv.getNumero() + " a bien été enregistrée. Le garage vous confirmera le rendez-vous.");
            return "redirect:/mes-rendez-vous";
        } catch (LimiteDemandesEnAttenteException e) {
            // Erreur GLOBALE, et non sous « prestations » ou elle etait ancree par
            // heritage du refactor reservation. Le plafond porte sur le COMPTE : le
            // membre a trop de demandes en attente, quelles que soient les
            // prestations choisies. L ancrer sous un champ lui disait de corriger un
            // choix qui n est pas en cause, et changer ce choix n aurait rien change.
            erreurs.reject("rdv.limite-demandes", e.getMessage());
            return retourFormulaire(membre, formulaire, modele);
        } catch (PrestationIndisponibleException | AucunePrestationChoisieException e) {
            // Ces deux-la visent bien le choix de prestations : l erreur s ancre sous
            // ce champ, ou le membre peut agir.
            erreurs.addError(new FieldError("formulaire", "prestations", e.getMessage()));
            return retourFormulaire(membre, formulaire, modele);
        } catch (RegleMetierException e) {
            // Creneau indisponible (RM-08) et tout refus metier futur : sous le champ
            // date, comme l ancien default. Le routage par TYPE remplace le switch sur
            // getCodeRegle() : plus de NPE possible sur un code null, et le case
            // « RM-06 -> vehicule » disparait — aucune exception ne portait ce code,
            // le controle d appartenance du vehicule repond deja 404 en amont.
            erreurs.addError(new FieldError("formulaire", "debut", e.getMessage()));
            return retourFormulaire(membre, formulaire, modele);
        }
    }

    @GetMapping("/{reference}")
    public String detail(@AuthenticationPrincipal UserDetails membre,
                         @PathVariable UUID reference,
                         Model modele) {
        var rdv = rdvs.vueDuMembre(reference, membre.getUsername());
        modele.addAttribute("titre", "Rendez-vous " + rdv.numero());
        modele.addAttribute("rdv", rdv);
        return "reservation/rdv-detail";
    }

    /**
     * Fichier iCalendar du rendez-vous, pour l agenda personnel du membre (F38).
     *
     * <p>{@code Content-Disposition: attachment} et non {@code inline} : le fichier
     * n a aucun sens affiche dans un navigateur, il doit etre remis a l application
     * de calendrier du systeme. L appartenance et l etat CONFIRME sont verifies par
     * le service, qui repond {@code 404} dans les deux cas — jamais {@code 403},
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
            redirection.addFlashAttribute("message", "Le rendez-vous " + rdv.getNumero() + " a été annulé.");
        } catch (RegleMetierException e) {
            redirection.addFlashAttribute("erreur", e.getMessage());
        }
        return "redirect:/mes-rendez-vous/" + reference;
    }

    private String retourFormulaire(UserDetails membre, RdvForm formulaire, Model modele) {
        modele.addAttribute("titre", "Prendre rendez-vous");
        modele.addAttribute("formulaire", formulaire);
        modele.addAttribute("vehicules", vehicules.vuesDuMembre(membre.getUsername()));
        modele.addAttribute("prestationsParCategorie", rdvs.prestationsProposees());
        modele.addAttribute("dateMin", rdvs.premierJourReservable());
        modele.addAttribute("dateMax", rdvs.dernierJourReservable());
        boolean aucune = formulaire.getPrestations() == null || formulaire.getPrestations().isEmpty();
        modele.addAttribute("aucunePrestation", aucune);
        modele.addAttribute("creneaux", aucune ? List.of() : rdvs.creneauxPour(formulaire.getDate(), formulaire.getPrestations()));
        return "reservation/rdv-formulaire";
    }
}