package be.autoservplus.identite.web;

import be.autoservplus.common.exception.RegleMetierException;
import be.autoservplus.identite.domain.Langue;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.service.ProfilService;
import be.autoservplus.identite.web.dto.ProfilForm;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Locale;

/**
 * Modification du profil par le membre (CdC §5.2.2).
 *
 * <p>{@code /mon-compte} etait en lecture seule et n affichait que l adresse de
 * courriel : ni nom, ni prenom, ni telephone, ni adresse, ni langue n etaient
 * modifiables.</p>
 *
 * <p><b>La langue solde la dette F6.</b> La colonne {@code utilisateur.langue} etait
 * lue — a la connexion, et par les documents PDF — mais <b>aucun ecran ne l ecrivait
 * jamais</b> : elle valait {@code fr} pour tout le monde. Un membre neerlandophone
 * devait donc recliquer « NL » a chaque session.</p>
 */
@Controller
@RequestMapping("/mon-profil")
public class ProfilController {

    private final ProfilService service;
    private final MessageSource messages;
    private final LocaleResolver resolveurLangue;

    public ProfilController(ProfilService service, MessageSource messages,
                            LocaleResolver resolveurLangue) {
        this.service = service;
        this.messages = messages;
        this.resolveurLangue = resolveurLangue;
    }

    @GetMapping
    public String afficher(@AuthenticationPrincipal UserDetails membre, Model modele) {
        Utilisateur profil = service.profilDe(membre.getUsername());
        modele.addAttribute("formulaire", ProfilForm.de(profil));
        preparer(modele, profil);
        return "identite/profil";
    }

    @PostMapping
    public String enregistrer(@AuthenticationPrincipal UserDetails membre,
                              @Valid @ModelAttribute("formulaire") ProfilForm formulaire,
                              BindingResult erreurs,
                              Model modele,
                              HttpServletRequest requete,
                              HttpServletResponse reponse,
                              RedirectAttributes redirection) {

        // Erreur GLOBALE et non portee par un champ : la regle lie trois champs, et
        // l accrocher a l un d eux designerait arbitrairement un coupable.
        if (!formulaire.adresseCoherente()) {
            erreurs.reject("adresse.incomplete",
                    msg("profil.erreur.adresse-incomplete"));
        }

        if (erreurs.hasErrors()) {
            preparer(modele, service.profilDe(membre.getUsername()));
            return "identite/profil";
        }

        try {
            service.enregistrer(membre.getUsername(), formulaire.getPrenom(),
                    formulaire.getNom(), formulaire.getTelephone(), formulaire.getRue(),
                    formulaire.getNumeroRue(), formulaire.getCodePostal(),
                    formulaire.getLocalite(), formulaire.getPays(), formulaire.getLangue());
        } catch (RegleMetierException refus) {
            // Code d erreur SPRING, pas un code RM : le premier argument de reject sert
            // a la resolution de message cote framework. Il valait jusqu ici
            // getCodeRegle(), devenu nul — le refus ne correspond a aucune exigence du
            // CdC. Le texte affiche reste la cle i18n, pour suivre la langue de session.
            erreurs.reject("adresse.requise.facture", msg("profil.erreur.adresse-facture"));
            preparer(modele, service.profilDe(membre.getUsername()));
            return "identite/profil";
        }

        appliquerLangue(requete, reponse, formulaire.getLangue());

        // Post/Redirect/Get, et non un rendu direct. Deux raisons, dont une decouverte
        // en ecrivant le test : la langue posee ci-dessus vit en SESSION, alors que la
        // locale de la requete courante est deja resolue quand le gestionnaire
        // s execute — rendre ici servirait la page de confirmation dans l ANCIENNE
        // langue, et le membre croirait son choix perdu. Le detour par une redirection
        // fait resoudre la locale a neuf. Il evite par ailleurs qu un rafraichissement
        // rejoue l enregistrement.
        redirection.addFlashAttribute("enregistre", true);
        return "redirect:/mon-profil";
    }

    private void appliquerLangue(HttpServletRequest requete, HttpServletResponse reponse,
                                 Langue langue) {
        resolveurLangue.setLocale(requete, reponse, Locale.forLanguageTag(langue.name()));
    }

    private void preparer(Model modele, Utilisateur profil) {
        modele.addAttribute("titre", msg("profil.titre"));
        modele.addAttribute("email", profil.getEmail());
        modele.addAttribute("langues", Langue.values());
    }

    private String msg(String cle) {
        return messages.getMessage(cle, null, LocaleContextHolder.getLocale());
    }
}
