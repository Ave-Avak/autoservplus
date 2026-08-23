package be.autoservplus.catalogue.web;

import be.autoservplus.catalogue.domain.TauxTvaBelge;
import be.autoservplus.catalogue.service.AdminCatalogueService;
import be.autoservplus.catalogue.service.DoublonCatalogueException;
import be.autoservplus.catalogue.service.SuppressionRefuseeException;
import be.autoservplus.catalogue.service.dto.ArticleVueAdmin;
import be.autoservplus.catalogue.service.dto.DonneesPrestation;
import be.autoservplus.catalogue.service.dto.PropositionSuppression;
import be.autoservplus.catalogue.web.dto.PrestationForm;
import be.autoservplus.common.exception.RegleMetierException;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

/**
 * Ecrans d administration des prestations (A1, A2, A3) sous
 * /admin/catalogue/prestations.
 *
 * <p>La protection d URL {@code /admin/**} filtre le role ADMINISTRATEUR ;
 * {@link AdminCatalogueService} redouble par {@code @PreAuthorize} en defense en
 * profondeur, comme les autres controleurs admin. Pattern PRG + flash ; les
 * messages passent par le {@code MessageSource} (cles {@code admin.catalogue.*}),
 * comme le panier. Les refus d unicite sont raccroches au champ fautif du
 * formulaire plutot que jetes en message global : l administrateur corrige sans
 * chercher. {@link RessourceIntrouvableException} n est pas catchee sur les URL a
 * reference : une reference inconnue remonte en 404.</p>
 *
 * <p>Le retrait passe par une page de confirmation qui applique le diagnostic
 * RM-29 : suppression definitive proposee seulement si aucun historique ne
 * reference la prestation, desactivation sinon — jamais les deux.</p>
 */
@Controller
@RequestMapping("/admin/catalogue/prestations")
public class AdminPrestationController {

    private static final String LISTE = "redirect:/admin/catalogue/prestations";
    private static final String FORMULAIRE = "admin/catalogue/prestation-formulaire";

    private final AdminCatalogueService service;
    private final MessageSource messages;

    public AdminPrestationController(AdminCatalogueService service, MessageSource messages) {
        this.service = service;
        this.messages = messages;
    }

    // --- liste ------------------------------------------------------------------------

    @GetMapping
    public String liste(Model modele) {
        modele.addAttribute("titre", msg("admin.catalogue.prestations.titre"));
        modele.addAttribute("prestations", service.prestationsPourAdmin());
        return "admin/catalogue/prestations";
    }

    // --- creation (A1) ----------------------------------------------------------------

    @GetMapping("/nouvelle")
    public String afficherCreation(Model modele) {
        return preparerFormulaire(new PrestationForm(), false, null, modele);
    }

    @PostMapping
    public String creer(@Valid @ModelAttribute("formulaire") PrestationForm formulaire,
                        BindingResult erreurs,
                        Model modele,
                        RedirectAttributes redirection) {
        if (erreurs.hasErrors()) {
            return preparerFormulaire(formulaire, false, null, modele);
        }
        try {
            service.creerPrestation(versDonnees(formulaire));
            redirection.addFlashAttribute("message",
                    msg("admin.catalogue.message.prestation-creee", formulaire.getLibelle()));
            return LISTE;
        } catch (DoublonCatalogueException e) {
            erreurs.rejectValue(e.getChamp(), "admin.catalogue.erreur.doublon");
        } catch (IllegalArgumentException e) {
            // garde du domaine : taux de TVA hors de la liste belge (POST forge)
            erreurs.rejectValue("tauxTva", "admin.catalogue.erreur.taux");
        } catch (RegleMetierException | RessourceIntrouvableException e) {
            // categorie inconnue ou du mauvais type : n arrive que par POST forge,
            // le select ne propose que des categories SERVICE actives
            erreurs.rejectValue("codeCategorie", "admin.catalogue.erreur.categorie");
        }
        return preparerFormulaire(formulaire, false, null, modele);
    }

    // --- modification (A2) ------------------------------------------------------------

    @GetMapping("/{reference}/modifier")
    public String afficherModification(@PathVariable UUID reference, Model modele) {
        return preparerFormulaire(versFormulaire(service.vuePrestation(reference)),
                true, reference, modele);
    }

    @PostMapping("/{reference}/modifier")
    public String modifier(@PathVariable UUID reference,
                           @Valid @ModelAttribute("formulaire") PrestationForm formulaire,
                           BindingResult erreurs,
                           Model modele,
                           RedirectAttributes redirection) {
        if (erreurs.hasErrors()) {
            return preparerFormulaire(formulaire, true, reference, modele);
        }
        try {
            service.modifierPrestation(reference, versDonnees(formulaire));
            redirection.addFlashAttribute("message",
                    msg("admin.catalogue.message.prestation-modifiee", formulaire.getLibelle()));
            return LISTE;
        } catch (DoublonCatalogueException e) {
            erreurs.rejectValue(e.getChamp(), "admin.catalogue.erreur.doublon");
        } catch (IllegalArgumentException e) {
            erreurs.rejectValue("tauxTva", "admin.catalogue.erreur.taux");
        } catch (RegleMetierException e) {
            erreurs.rejectValue("codeCategorie", "admin.catalogue.erreur.categorie");
        }
        return preparerFormulaire(formulaire, true, reference, modele);
    }

    // --- retrait (A3, RM-29) ----------------------------------------------------------

    /** Page de confirmation : le diagnostic RM-29 decide de l action proposee. */
    @GetMapping("/{reference}/supprimer")
    public String afficherSuppression(@PathVariable UUID reference, Model modele) {
        modele.addAttribute("titre", msg("admin.catalogue.supprimer.titre"));
        modele.addAttribute("proposition", service.propositionSuppressionPrestation(reference));
        return "admin/catalogue/prestation-supprimer";
    }

    @PostMapping("/{reference}/supprimer")
    public String supprimer(@PathVariable UUID reference, RedirectAttributes redirection) {
        PropositionSuppression proposition = service.propositionSuppressionPrestation(reference);
        try {
            service.supprimerDefinitivementPrestation(reference);
            redirection.addFlashAttribute("message",
                    msg("admin.catalogue.message.prestation-supprimee", proposition.libelle()));
        } catch (SuppressionRefuseeException e) {
            redirection.addFlashAttribute("erreur",
                    msg("admin.catalogue.erreur.suppression-referencee", e.getNombreReferences()));
        }
        return LISTE;
    }

    @PostMapping("/{reference}/desactiver")
    public String desactiver(@PathVariable UUID reference, RedirectAttributes redirection) {
        ArticleVueAdmin vue = service.vuePrestation(reference);
        service.desactiverPrestation(reference);
        redirection.addFlashAttribute("message",
                msg("admin.catalogue.message.prestation-desactivee", vue.libelle()));
        return LISTE;
    }

    @PostMapping("/{reference}/activer")
    public String activer(@PathVariable UUID reference, RedirectAttributes redirection) {
        ArticleVueAdmin vue = service.vuePrestation(reference);
        service.activerPrestation(reference);
        redirection.addFlashAttribute("message",
                msg("admin.catalogue.message.prestation-activee", vue.libelle()));
        return LISTE;
    }

    // --- helpers ----------------------------------------------------------------------

    private String preparerFormulaire(PrestationForm formulaire, boolean edition,
                                      UUID reference, Model modele) {
        modele.addAttribute("titre", msg(edition
                ? "admin.catalogue.prestation.modifier.titre"
                : "admin.catalogue.prestation.creer.titre"));
        modele.addAttribute("formulaire", formulaire);
        modele.addAttribute("edition", edition);
        modele.addAttribute("reference", reference);
        modele.addAttribute("categories", service.categoriesDePrestations());
        modele.addAttribute("tauxAdmis", TauxTvaBelge.TAUX_ADMIS);
        // BL-7 : historique des modifications de CET article. La methode existait depuis
        // l historisation A2/A5 (V25) sans aucun appelant de production ; c est ici
        // qu elle sert, au plus pres de ce qu elle decrit.
        if (edition) {
            modele.addAttribute("historique", service.historiquePrestation(reference));
        }
        return FORMULAIRE;
    }

    private DonneesPrestation versDonnees(PrestationForm f) {
        return new DonneesPrestation(f.getCodeCategorie(), f.getCode(), f.getLibelle(),
                f.getDescription(), f.getPrixHtva(), f.getTauxTva(), f.getDureeMinutes(),
                f.isActif());
    }

    private PrestationForm versFormulaire(ArticleVueAdmin vue) {
        PrestationForm f = new PrestationForm();
        f.setCodeCategorie(vue.categorieCode());
        f.setCode(vue.identifiant());
        f.setLibelle(vue.libelle());
        f.setDescription(vue.description());
        f.setPrixHtva(vue.prixHtva());
        f.setTauxTva(vue.tauxTva());
        f.setDureeMinutes(vue.dureeMinutes());
        f.setActif(vue.actif());
        return f;
    }

    private String msg(String cle, Object... arguments) {
        return messages.getMessage(cle, arguments, LocaleContextHolder.getLocale());
    }
}
