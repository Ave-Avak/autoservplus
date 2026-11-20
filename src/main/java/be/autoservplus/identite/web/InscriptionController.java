package be.autoservplus.identite.web;

import be.autoservplus.common.exception.RefusTraduit;
import be.autoservplus.common.exception.RegleMetierException;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.identite.domain.Langue;
import be.autoservplus.identite.service.InscriptionService;
import be.autoservplus.identite.service.LimiteurDemandesCourriel;
import be.autoservplus.legal.domain.TypeDocumentVersionne;
import be.autoservplus.legal.service.VersionsDocumentsService;
import be.autoservplus.identite.service.PiegeAntiBot;
import be.autoservplus.identite.service.VerificateurTurnstile;
import org.springframework.beans.factory.annotation.Value;

import java.util.Arrays;
import java.util.Optional;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import be.autoservplus.identite.web.dto.InscriptionForm;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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
    private final VersionsDocumentsService versionsDocuments;

    public InscriptionController(InscriptionService service,
                                 LimiteurDemandesCourriel limiteur,
                                 MessageSource messages,
                                 PiegeAntiBot pieges,
                                 Optional<VerificateurTurnstile> turnstile,
                                 @Value("${autoservplus.securite.turnstile.cle-site:}")
                                 String cleTurnstile,
                                 VersionsDocumentsService versionsDocuments) {
        this.versionsDocuments = versionsDocuments;
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

    /**
     * Pose de quoi rendre le widget, vide si Turnstile n'est pas configuré, et le numéro des
     * CGV en vigueur, affiché à côté de la case.
     */
    private void armerFormulaire(Model modele) {
        modele.addAttribute("versionCgv", versionsDocuments.versionCourante(TypeDocumentVersionne.CGV));
        modele.addAttribute("horodatageAntiBot", pieges.horodatageActuel());
        modele.addAttribute("cleTurnstile", cleTurnstile);
    }

    @GetMapping("/inscription")
    public String afficherFormulaire(Model modele) {
        InscriptionForm formulaire = new InscriptionForm();
        formulaire.setLangue(langueAffichee());
        modele.addAttribute("formulaire", formulaire);
        armerFormulaire(modele);
        return "identite/inscription";
    }

    /**
     * Langue de la page, proposée comme langue du compte. Une langue d'affichage que le
     * compte ne connaît pas laisse le français, langue par défaut du formulaire.
     */
    private static Langue langueAffichee() {
        String code = LocaleContextHolder.getLocale().getLanguage();
        return Arrays.stream(Langue.values())
                .filter(langue -> langue.name().equals(code))
                .findFirst()
                .orElse(Langue.fr);
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
                                    RedirectAttributes redirection,
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
            return versConfirmation(formulaire, erreurs, redirection);
        }

        if (!limiteur.autoriserParIp(requete.getRemoteAddr())) {
            erreurs.reject("securite.debit.trop-de-demandes",
                    messages.getMessage("securite.debit.trop-de-demandes", null,
                            LocaleContextHolder.getLocale()));
            armerFormulaire(modele);
            return "identite/inscription";
        }

        if (!formulaire.motsDePasseConcordent()) {
            erreurs.rejectValue("confirmationMotDePasse", "inscription.erreur.differents",
                    "Les deux mots de passe ne correspondent pas.");
        }

        if (erreurs.hasErrors()) {
            armerFormulaire(modele);
            return "identite/inscription";
        }

        try {
            // Compte créé ou titulaire prévenu : la même page dans les deux cas.
            service.inscrire(formulaire.getEmail(), formulaire.getMotDePasse(),
                    formulaire.getNom(), formulaire.getPrenom(), formulaire.getLangue(),
                    formulaire.isCgv(), requete.getRemoteAddr());
        } catch (RegleMetierException e) {
            erreurs.addError(refusSousLeChamp(e));
            armerFormulaire(modele);
            return "identite/inscription";
        }

        return versConfirmation(formulaire, erreurs, redirection);
    }

    /**
     * Après l'envoi, redirection vers une page lue en GET : actualiser la page n'envoie plus
     * le formulaire une seconde fois. L'adresse saisie passe en attribut flash, lu une fois,
     * si elle a passé la validation du formulaire. Un envoi automatique écarté peut porter
     * une adresse vide ou mal formée : la page prend alors sa version générique.
     */
    private static String versConfirmation(InscriptionForm formulaire, BindingResult erreurs,
                                           RedirectAttributes redirection) {
        if (!erreurs.hasFieldErrors("email")) {
            redirection.addFlashAttribute("adresse", formulaire.getEmail());
        }
        return "redirect:/inscription/confirmee";
    }

    /**
     * Confirmation de l'envoi. Sans attribut flash, page ouverte directement ou actualisée
     * une seconde fois, la version générique s'affiche, sans adresse et sans erreur.
     */
    @GetMapping("/inscription/confirmee")
    public String afficherConfirmation() {
        return "identite/inscription-confirmee";
    }

    /**
     * Refus du service sous son champ : la case pour les CGV, l'adresse sinon. Un
     * {@link RefusTraduit} s'affiche par sa clé, dans la langue de la page. La phrase
     * française du refus reste le texte de repli.
     */
    private static FieldError refusSousLeChamp(RegleMetierException refus) {
        String[] codes = refus instanceof RefusTraduit traduit
                ? new String[] {traduit.getCleMessage()}
                : null;
        String champ = refus instanceof RefusTraduit traduit
                && InscriptionService.CLE_CGV_OBLIGATOIRES.equals(traduit.getCleMessage())
                ? "cgv" : "email";
        return new FieldError("formulaire", champ, null, false, codes, null, refus.getMessage());
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

    /**
     * Un lien refusé, expiré, déjà servi ou inconnu, répond 404 avec sa page : le même
     * statut dans les trois cas, pour qu'il ne dise pas qu'un jeton a existé.
     */
    @GetMapping("/inscription/verification")
    public String verifierAdresse(@RequestParam String jeton, Model modele, HttpServletResponse reponse) {
        modele.addAttribute("titre", "Vérification de votre adresse");
        try {
            service.confirmerAdresse(jeton);
            modele.addAttribute("succes", true);
        } catch (RessourceIntrouvableException | RegleMetierException e) {
            reponse.setStatus(HttpServletResponse.SC_NOT_FOUND);
            modele.addAttribute("succes", false);
            modele.addAttribute("motif", motifRefus(e));
        }
        return "identite/verification";
    }

    /**
     * Clé i18n du refus. Le message de l'exception reste au journal : c'est un texte
     * technique, en français, qui nommait jusqu'au jeton refusé.
     */
    private static String motifRefus(RuntimeException refus) {
        return refus instanceof RegleMetierException regle && "RM-03".equals(regle.getCodeRegle())
                ? "lien-courriel.verification-expire"
                : "lien-courriel.inconnu";
    }
}