package be.autoservplus;

import be.autoservplus.avis.service.AvisService;
import be.autoservplus.catalogue.service.dto.ArticleVue;
import be.autoservplus.catalogue.web.DonneesStructurees;
import be.autoservplus.config.IdentiteGarage;
import be.autoservplus.galerie.service.GalerieService;
import be.autoservplus.vitrine.service.AccueilService;
import be.autoservplus.vitrine.service.HorairesOuvertureService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/** Pages generales : accueil public et espace membre. */
@Controller
public class AccueilController {

    /** Avis montrés à l'accueil (consigne de I3 : les trois derniers). */
    private static final int AVIS_MONTRES = 3;

    private final MessageSource messages;
    private final AccueilService accueil;
    private final AvisService avis;
    private final HorairesOuvertureService horaires;
    private final IdentiteGarage garage;
    private final GalerieService galerie;
    private final String urlPublique;

    public AccueilController(MessageSource messages, AccueilService accueil, AvisService avis,
                             HorairesOuvertureService horaires, IdentiteGarage garage, GalerieService galerie,
                             @Value("${autoservplus.url-publique}") String urlPublique) {
        this.messages = messages;
        this.accueil = accueil;
        this.avis = avis;
        this.horaires = horaires;
        this.garage = garage;
        this.galerie = galerie;
        this.urlPublique = urlPublique.endsWith("/") ? urlPublique : urlPublique + "/";
    }

    @GetMapping({"/", "/accueil"})
    public String accueil(Model modele) {
        List<ArticleVue> prestations = accueil.prestationsMontrees();
        modele.addAttribute("prestations", prestations);
        // Une photo par prestation montrée (étape 0, constat U2) : sa première, à défaut le
        // visuel de sa catégorie, en deux requêtes pour les six cartes.
        modele.addAttribute("vignettes", galerie.vignettesDePrestations(
                prestations.stream().map(ArticleVue::reference).toList()));
        modele.addAttribute("creneaux", accueil.prochainsCreneaux().orElse(null));
        modele.addAttribute("syntheseAvis", avis.syntheseGenerale());
        modele.addAttribute("derniersAvis", avis.derniersPublies(AVIS_MONTRES));
        modele.addAttribute("horaires", horaires.semaine());
        modele.addAttribute("donneesGarage", DonneesStructurees.duGarage(
                garage.pour(LocaleContextHolder.getLocale()), horaires.plagesActives(), urlPublique));
        return "accueil";
    }

    @GetMapping("/mon-compte")
    public String monCompte(@AuthenticationPrincipal UserDetails membre, Model modele) {
        modele.addAttribute("titre",
                messages.getMessage("compte.titre", null, LocaleContextHolder.getLocale()));
        modele.addAttribute("email", membre.getUsername());
        return "mon-compte";
    }
}
