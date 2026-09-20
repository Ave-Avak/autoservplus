package be.autoservplus.identite.web;

import be.autoservplus.common.exception.RegleMetierException;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.identite.service.InscriptionService;
import be.autoservplus.identite.service.LimiteurDemandesCourriel;
import be.autoservplus.identite.service.PiegeAntiBot;
import be.autoservplus.identite.service.VerificateurTurnstile;
import org.springframework.beans.factory.annotation.Value;

import java.util.Optional;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import be.autoservplus.identite.web.dto.InscriptionForm;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Parcours d inscription et de verification d adresse.
 *
 * <p>Le controleur ne porte aucune regle metier : il valide la forme des donnees,
 * delegue au service, et traduit le resultat en vue. Toute logique reste dans
 * {@link InscriptionService}.</p>
 */
@Controller
public class InscriptionController {

    private final InscriptionService service;
    private final LimiteurDemandesCourriel limiteur;
    private final MessageSource messages;
    private final PiegeAntiBot pieges;
    /** Absent quand aucune cle secrete n est configuree : voir TurnstileClientConfig. */
    private final Optional<VerificateurTurnstile> turnstile;
    private final String cleTurnstile;

    public InscriptionController(InscriptionService service,
                                 LimiteurDemandesCourriel limiteur,
                                 MessageSource messages,
                                 PiegeAntiBot pieges,
                                 Optional<VerificateurTurnstile> turnstile,
                                 @Value("${autoservplus.securite.turnstile.cle-site:}")
                                 String cleTurnstile) {
        this.service = service;
        this.limiteur = limiteur;
        this.messages = messages;
        this.pieges = pieges;
        this.turnstile = turnstile;
        this.cleTurnstile = cleTurnstile;
    }

    /**
     * Jeton refuse, ou absent alors que Turnstile est configure. Rend {@code false}
     * quand Turnstile n est pas configure : les trois autres couches suffisent alors.
     */
    private boolean turnstileRefuse(String jeton, HttpServletRequest requete) {
        return turnstile.isPresent()
                && !turnstile.get().jetonValide(jeton, requete.getRemoteAddr());
    }

    /** Pose de quoi rendre le widget ; vide, le fragment ne rend rien. */
    private void armerFormulaire(Model modele) {
        modele.addAttribute("horodatageAntiBot", pieges.horodatageActuel());
        modele.addAttribute("cleTurnstile", cleTurnstile);
    }

    @GetMapping("/inscription")
    public String afficherFormulaire(Model modele) {
        modele.addAttribute("titre", "Créer un compte");
        modele.addAttribute("formulaire", new InscriptionForm());
        armerFormulaire(modele);
        return "identite/inscription";
    }

    /**
     * Traite l inscription.
     *
     * <p>Le plafond est consomme <b>avant</b> la validation et avant tout acces a la
     * base : un formulaire rejete pour saisie invalide coute donc autant qu un autre,
     * sans quoi il suffirait d envoyer des saisies fautives pour sonder sans limite.</p>
     */
    @PostMapping("/inscription")
    public String traiterFormulaire(@Valid @ModelAttribute("formulaire") InscriptionForm formulaire,
                                    BindingResult erreurs,
                                    Model modele,
                                    HttpServletRequest requete,
                                    @RequestParam(name = PiegeAntiBot.CHAMP_PIEGE,
                                            required = false) String piege,
                                    @RequestParam(name = PiegeAntiBot.CHAMP_HORODATAGE,
                                            required = false) String horodatage,
                                    @RequestParam(name = VerificateurTurnstile.CHAMP_JETON,
                                            required = false) String jetonTurnstile) {

        // Ecarte en silence : dire au robot ce qui l a trahi, c est lui dire quoi
        // corriger. Un visiteur legitime ne peut pas atteindre ce cas.
        if (pieges.soumissionAutomatique(piege, horodatage)
                || turnstileRefuse(jetonTurnstile, requete)) {
            modele.addAttribute("titre", "Vérifiez votre courriel");
            modele.addAttribute("adresse", formulaire.getEmail());
            return "identite/inscription-confirmee";
        }

        if (!limiteur.autoriserParIp(requete.getRemoteAddr())) {
            erreurs.reject("securite.debit.trop-de-demandes",
                    messages.getMessage("securite.debit.trop-de-demandes", null,
                            LocaleContextHolder.getLocale()));
            modele.addAttribute("titre", "Créer un compte");
            armerFormulaire(modele);
            return "identite/inscription";
        }

        if (!formulaire.motsDePasseConcordent()) {
            erreurs.rejectValue("confirmationMotDePasse", "motsDePasse.differents",
                    "Les deux mots de passe ne correspondent pas.");
        }

        if (erreurs.hasErrors()) {
            modele.addAttribute("titre", "Créer un compte");
            armerFormulaire(modele);
            return "identite/inscription";
        }

        try {
            service.inscrire(formulaire.getEmail(), formulaire.getMotDePasse(),
                    formulaire.getNom(), formulaire.getPrenom(), formulaire.getLangue());
        } catch (RegleMetierException e) {
            erreurs.addError(new FieldError("formulaire", "email", e.getMessage()));
            modele.addAttribute("titre", "Créer un compte");
            armerFormulaire(modele);
            return "identite/inscription";
        }

        modele.addAttribute("titre", "Vérifiez votre courriel");
        modele.addAttribute("adresse", formulaire.getEmail());
        return "identite/inscription-confirmee";
    }

    /**
     * Formulaire public de renvoi du courriel de verification.
     *
     * <p>La route vit sous /inscription, deja ouverte a l anonyme par
     * SecuriteConfig : c est la suite du parcours d inscription, et la placer ailleurs
     * obligerait a elargir la surface publique pour un ecran qui n en a pas besoin.</p>
     */
    @GetMapping("/inscription/renvoyer-verification")
    public String afficherRenvoiVerification(Model modele) {
        armerFormulaire(modele);
        return "identite/renvoyer-verification";
    }

    /**
     * Traite la demande de renvoi.
     *
     * <p>Rend TOUJOURS la meme vue, sans exposer l adresse saisie ni le sort reel de la
     * demande : le service ne remonte rien qui permettrait de les distinguer. C est le
     * courriel, et lui seul, qui renseigne le titulaire du compte.</p>
     */
    @PostMapping("/inscription/renvoyer-verification")
    public String traiterRenvoiVerification(@RequestParam String email, Model modele,
                                            HttpServletRequest requete,
                                            @RequestParam(name = PiegeAntiBot.CHAMP_PIEGE,
                                                    required = false) String piege,
                                            @RequestParam(name = PiegeAntiBot.CHAMP_HORODATAGE,
                                                    required = false) String horodatage,
                                            @RequestParam(name = VerificateurTurnstile.CHAMP_JETON,
                                                    required = false) String jetonTurnstile) {
        // Meme page que le cas nominal : la neutralite de cet ecran vaut aussi face a
        // un robot, qui ne doit pas apprendre qu il a ete repere.
        if (pieges.soumissionAutomatique(piege, horodatage)
                || turnstileRefuse(jetonTurnstile, requete)) {
            return "identite/renvoyer-verification-envoye";
        }
        // Decompte AVANT le service, donc avant toute recherche en base : le compteur
        // monte pareil pour une adresse inconnue, ce qui est la condition pour que le
        // plafond n introduise pas l oracle que cet ecran est fait pour taire.
        if (limiteur.autoriser(email, requete.getRemoteAddr())) {
            service.demanderRenvoiVerification(email);
        } else {
            modele.addAttribute("debitDepasse", true);
        }
        return "identite/renvoyer-verification-envoye";
    }

    @GetMapping("/inscription/verification")
    public String verifierAdresse(@RequestParam String jeton, Model modele) {
        modele.addAttribute("titre", "Vérification de votre adresse");
        try {
            service.confirmerAdresse(jeton);
            modele.addAttribute("succes", true);
        } catch (RessourceIntrouvableException | RegleMetierException e) {
            modele.addAttribute("succes", false);
            modele.addAttribute("motif", e.getMessage());
        }
        return "identite/verification";
    }
}