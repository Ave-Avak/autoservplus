package be.autoservplus.identite.web;

import be.autoservplus.common.exception.RefusTraduit;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.identite.service.PremierMotDePasseService;
import be.autoservplus.identite.web.dto.NouveauMotDePasseForm;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Definition du premier mot de passe d un administrateur cree au back-office, depuis le
 * lien recu par courriel.
 *
 * <p><b>Le GET ne consomme rien.</b> Il verifie le jeton et le place en champ cache.
 * Seul le POST le consomme. Les messageries et antivirus suivent les liens pour les
 * analyser : un jeton consomme a l ouverture serait brule avant que le titulaire n ait
 * clique.</p>
 *
 * <p><b>{@code Referrer-Policy: no-referrer} sur cette route seulement.</b> L adresse de
 * la page porte le jeton. La politique globale ({@code same-origin}) n envoie deja rien
 * a un tiers, mais transmettrait l URL complete aux requetes vers le site lui-meme, donc
 * a ses journaux d acces. Le gestionnaire d en-tetes de Spring Security n ecrit pas un
 * en-tete deja pose, ce qui rend la surcharge possible sans toucher a la configuration
 * globale.</p>
 *
 * <p>L ecran est celui du changement impose : dans les deux cas, le titulaire remplace un
 * secret qu il n a pas choisi.</p>
 */
@Controller
@RequestMapping("/compte/definition")
public class DefinitionMotDePasseController {

    static final String POLITIQUE_REFERENT = "no-referrer";

    private final PremierMotDePasseService service;
    private final MessageSource messages;

    public DefinitionMotDePasseController(PremierMotDePasseService service, MessageSource messages) {
        this.service = service;
        this.messages = messages;
    }

    @GetMapping
    public String afficher(@RequestParam(required = false) String jeton, Model modele,
                           HttpServletResponse reponse) {
        reponse.setHeader("Referrer-Policy", POLITIQUE_REFERENT);
        try {
            service.verifierJeton(jeton);
        } catch (RessourceIntrouvableException | RefusTraduit invalide) {
            return lienInvalide(modele);
        }
        NouveauMotDePasseForm formulaire = new NouveauMotDePasseForm();
        formulaire.setJeton(jeton);
        modele.addAttribute("formulaire", formulaire);
        preparer(modele, jeton);
        return EtapesConnexionController.VUE_MOT_DE_PASSE;
    }

    @PostMapping
    public String definir(@Valid @ModelAttribute("formulaire") NouveauMotDePasseForm formulaire,
                          BindingResult erreurs, Model modele, HttpServletResponse reponse) {
        reponse.setHeader("Referrer-Policy", POLITIQUE_REFERENT);
        if (!formulaire.concordent()) {
            erreurs.rejectValue("confirmation", "motsDePasse.differents",
                    msg("mot-de-passe-a-definir.erreur.differents"));
        }
        if (erreurs.hasErrors()) {
            preparer(modele, formulaire.getJeton());
            return EtapesConnexionController.VUE_MOT_DE_PASSE;
        }
        try {
            service.definir(formulaire.getJeton(), formulaire.getMotDePasse());
        } catch (RessourceIntrouvableException | RefusTraduit invalide) {
            return lienInvalide(modele);
        }
        modele.addAttribute("titre", msg("definition.terminee.titre"));
        return "identite/definition-terminee";
    }

    private void preparer(Model modele, String jeton) {
        modele.addAttribute("titre", msg("mot-de-passe-a-definir.titre"));
        modele.addAttribute("action", "/compte/definition");
        modele.addAttribute("jeton", jeton);
        modele.addAttribute("enConnexion", false);
    }

    /** Expire, consomme ou inconnu : un seul message, le lien n a pas a dire lequel. */
    private String lienInvalide(Model modele) {
        modele.addAttribute("titre", msg("definition.invalide.titre"));
        return "identite/definition-lien-invalide";
    }

    private String msg(String cle) {
        return messages.getMessage(cle, null, LocaleContextHolder.getLocale());
    }
}
