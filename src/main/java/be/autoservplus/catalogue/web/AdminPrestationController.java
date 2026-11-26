package be.autoservplus.catalogue.web;

import be.autoservplus.catalogue.domain.CompositionRefuseeException;
import be.autoservplus.catalogue.domain.DureePrestationALHeureException;
import be.autoservplus.catalogue.domain.PrixPoseSeuleRefuseException;
import be.autoservplus.catalogue.domain.TauxTvaBelge;
import be.autoservplus.catalogue.domain.UnitePrestation;
import be.autoservplus.catalogue.domain.UnitePrestationFixeeException;
import be.autoservplus.catalogue.service.AdminCatalogueService;
import be.autoservplus.catalogue.service.DoublonCatalogueException;
import be.autoservplus.catalogue.service.SuppressionRefuseeException;
import be.autoservplus.catalogue.service.dto.ArticleVueAdmin;
import be.autoservplus.catalogue.service.dto.DonneesPrestation;
import be.autoservplus.catalogue.service.dto.FiltreArticlesAdmin;
import be.autoservplus.catalogue.service.dto.PrixPoseSeule;
import be.autoservplus.catalogue.service.dto.PropositionSuppression;
import be.autoservplus.catalogue.web.dto.PrestationForm;
import be.autoservplus.common.exception.RegleMetierException;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.common.web.PagesBackOffice;
import be.autoservplus.common.web.PaginationVue;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
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

import java.util.UUID;

/**
 * Ecrans d administration des prestations (A1, A2, A3) sous
 * /admin/catalogue/prestations.
 *
 * <p>La protection d URL {@code /admin/**} filtre le role ADMINISTRATEUR.
 * {@link AdminCatalogueService} redouble par {@code @PreAuthorize} en defense en
 * profondeur, comme les autres controleurs admin. Pattern PRG + flash. Les
 * messages passent par le {@code MessageSource} (cles {@code admin.catalogue.*}),
 * comme le panier. Les refus d unicite sont raccroches au champ fautif du
 * formulaire plutot que jetes en message global : l administrateur corrige sans
 * chercher. {@link RessourceIntrouvableException} n est pas catchee sur les URL a
 * reference : une reference inconnue remonte en 404.</p>
 *
 * <p>Le retrait passe par une page de confirmation qui applique le diagnostic
 * RM-29 : suppression definitive proposee seulement si aucun historique ne
 * reference la prestation, desactivation sinon, jamais les deux.</p>
 */
@Controller
@RequestMapping("/admin/catalogue/prestations")
public class AdminPrestationController {

    private static final String LISTE = "redirect:/admin/catalogue/prestations";
    private static final String FORMULAIRE = "admin/catalogue/prestation-formulaire";

    private final AdminCatalogueService service;
    private final MessageSource messages;
    private final PagesBackOffice pages;

    public AdminPrestationController(AdminCatalogueService service, MessageSource messages,
                                     PagesBackOffice pages) {
        this.service = service;
        this.messages = messages;
        this.pages = pages;
    }

    // --- liste ------------------------------------------------------------------------

    @GetMapping
    public String liste(@RequestParam(required = false) String categorie,
                        @RequestParam(required = false) Boolean actif,
                        @RequestParam(required = false) String recherche,
                        @RequestParam(defaultValue = "1") int page,
                        HttpServletRequest requete, Model modele) {
        FiltreArticlesAdmin filtre = new FiltreArticlesAdmin(categorie, actif, recherche);
        Page<ArticleVueAdmin> resultats = service.prestationsPourAdmin(filtre, pages.demande(page));
        modele.addAttribute("titre", msg("admin.catalogue.prestations.titre"));
        modele.addAttribute("prestations", resultats.getContent());
        modele.addAttribute("pagination", PaginationVue.de(resultats, requete));
        modele.addAttribute("filtre", filtre);
        modele.addAttribute("filtreSaisi", filtre.saisi());
        modele.addAttribute("categories", service.categoriesDePrestations());
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
        } catch (DureePrestationALHeureException e) {
            erreurs.rejectValue("dureeMinutes", e.getCleMessage());
        } catch (PrixPoseSeuleRefuseException e) {
            erreurs.rejectValue("prixPoseSeuleHtva", e.getCleMessage());
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
        } catch (UnitePrestationFixeeException e) {
            // Champ en lecture seule : n'arrive que par un envoi forgé ou une page périmée.
            erreurs.rejectValue("unite", e.getCleMessage());
        } catch (DureePrestationALHeureException e) {
            erreurs.rejectValue("dureeMinutes", e.getCleMessage());
        } catch (PrixPoseSeuleRefuseException e) {
            erreurs.rejectValue("prixPoseSeuleHtva", e.getCleMessage());
        } catch (IllegalArgumentException e) {
            erreurs.rejectValue("tauxTva", "admin.catalogue.erreur.taux");
        } catch (RegleMetierException e) {
            erreurs.rejectValue("codeCategorie", "admin.catalogue.erreur.categorie");
        }
        return preparerFormulaire(formulaire, true, reference, modele);
    }

    // --- composition (V76) ------------------------------------------------------------

    /**
     * Ajoute une pièce à la composition, ou change sa quantité. Formulaire distinct de celui
     * de la prestation : une composition se modifie pièce par pièce, et chaque changement
     * s'écrit à l'historique.
     */
    @PostMapping("/{reference}/composition")
    public String fixerComposant(@PathVariable UUID reference, @RequestParam UUID piece,
                                 @RequestParam(required = false) Integer quantite,
                                 RedirectAttributes redirection) {
        try {
            service.fixerComposant(reference, piece, quantite == null ? 0 : quantite);
            redirection.addFlashAttribute("message", msg("admin.catalogue.composition.message.fixee"));
        } catch (CompositionRefuseeException e) {
            redirection.addFlashAttribute("erreur", msg(e.getCleMessage()));
        }
        return versComposition(reference);
    }

    @PostMapping("/{reference}/composition/{piece}/retirer")
    public String retirerComposant(@PathVariable UUID reference, @PathVariable UUID piece,
                                   RedirectAttributes redirection) {
        service.retirerComposant(reference, piece);
        redirection.addFlashAttribute("message", msg("admin.catalogue.composition.message.retiree"));
        return versComposition(reference);
    }

    private static String versComposition(UUID reference) {
        return "redirect:/admin/catalogue/prestations/" + reference + "/modifier#composition";
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
        // l historisation A2/A5 (V25) sans aucun appelant de production. C est ici
        // qu elle sert, au plus pres de ce qu elle decrit.
        if (edition) {
            modele.addAttribute("historique", service.historiquePrestation(reference));
            modele.addAttribute("composition", service.compositionDe(reference));
            modele.addAttribute("piecesComposables", service.piecesPourAdmin().stream()
                    .filter(ArticleVueAdmin::actif).toList());
        }
        return FORMULAIRE;
    }

    private DonneesPrestation versDonnees(PrestationForm f) {
        return new DonneesPrestation(f.getCodeCategorie(), f.getCode(), f.getLibelle(),
                f.getDescription(), f.getPrixHtva(), f.getTauxTva(), f.getDureeMinutes(),
                f.isActif(), f.traductions(), f.getUnite(), PrixPoseSeule.de(f.getPrixPoseSeuleHtva()));
    }

    private PrestationForm versFormulaire(ArticleVueAdmin vue) {
        PrestationForm f = new PrestationForm();
        f.setCodeCategorie(vue.categorieCode());
        f.setCode(vue.identifiant());
        f.setLibelle(vue.libelle());
        f.setDescription(vue.description());
        f.setPrixHtva(vue.prixHtva());
        f.setPrixPoseSeuleHtva(vue.prixPoseSeuleHtva());
        f.setTauxTva(vue.tauxTva());
        f.setDureeMinutes(vue.dureeMinutes());
        f.setUnite(UnitePrestation.valueOf(vue.unite()));
        f.setActif(vue.actif());
        f.reprendre(vue.traductions());
        return f;
    }

    private String msg(String cle, Object... arguments) {
        return messages.getMessage(cle, arguments, LocaleContextHolder.getLocale());
    }
}
