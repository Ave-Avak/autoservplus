package be.autoservplus.catalogue.web;

import be.autoservplus.catalogue.domain.TauxTvaBelge;
import be.autoservplus.catalogue.service.AdminCatalogueService;
import be.autoservplus.catalogue.service.DoublonCatalogueException;
import be.autoservplus.catalogue.service.SuppressionRefuseeException;
import be.autoservplus.catalogue.service.dto.ArticleVueAdmin;
import be.autoservplus.catalogue.service.dto.DonneesPiece;
import be.autoservplus.catalogue.service.dto.PropositionSuppression;
import be.autoservplus.catalogue.web.dto.PieceForm;
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
 * Ecrans d administration des pieces (A4, A5, A6) sous /admin/catalogue/pieces.
 *
 * <p>Structure symetrique d {@link AdminPrestationController} : memes patterns
 * (PRG + flash i18n, erreurs raccrochees au champ, diagnostic RM-29 sur la page
 * de confirmation de retrait), champs propres aux pieces (marque, stock, seuil
 * d alerte, reference fabricant immuable a la place du code).</p>
 */
@Controller
@RequestMapping("/admin/catalogue/pieces")
public class AdminPieceController {

    private static final String LISTE = "redirect:/admin/catalogue/pieces";
    private static final String FORMULAIRE = "admin/catalogue/piece-formulaire";

    private final AdminCatalogueService service;
    private final MessageSource messages;

    public AdminPieceController(AdminCatalogueService service, MessageSource messages) {
        this.service = service;
        this.messages = messages;
    }

    // --- liste ------------------------------------------------------------------------

    @GetMapping
    public String liste(Model modele) {
        modele.addAttribute("titre", msg("admin.catalogue.pieces.titre"));
        modele.addAttribute("pieces", service.piecesPourAdmin());
        return "admin/catalogue/pieces";
    }

    // --- creation (A4) ----------------------------------------------------------------

    @GetMapping("/nouvelle")
    public String afficherCreation(Model modele) {
        return preparerFormulaire(new PieceForm(), false, null, modele);
    }

    @PostMapping
    public String creer(@Valid @ModelAttribute("formulaire") PieceForm formulaire,
                        BindingResult erreurs,
                        Model modele,
                        RedirectAttributes redirection) {
        if (erreurs.hasErrors()) {
            return preparerFormulaire(formulaire, false, null, modele);
        }
        try {
            service.creerPiece(versDonnees(formulaire));
            redirection.addFlashAttribute("message",
                    msg("admin.catalogue.message.piece-creee", formulaire.getLibelle()));
            return LISTE;
        } catch (DoublonCatalogueException e) {
            erreurs.rejectValue(e.getChamp(), "admin.catalogue.erreur.doublon");
        } catch (IllegalArgumentException e) {
            erreurs.rejectValue("tauxTva", "admin.catalogue.erreur.taux");
        } catch (RegleMetierException | RessourceIntrouvableException e) {
            erreurs.rejectValue("codeCategorie", "admin.catalogue.erreur.categorie");
        }
        return preparerFormulaire(formulaire, false, null, modele);
    }

    // --- modification (A5) ------------------------------------------------------------

    @GetMapping("/{reference}/modifier")
    public String afficherModification(@PathVariable UUID reference, Model modele) {
        return preparerFormulaire(versFormulaire(service.vuePiece(reference)),
                true, reference, modele);
    }

    @PostMapping("/{reference}/modifier")
    public String modifier(@PathVariable UUID reference,
                           @Valid @ModelAttribute("formulaire") PieceForm formulaire,
                           BindingResult erreurs,
                           Model modele,
                           RedirectAttributes redirection) {
        if (erreurs.hasErrors()) {
            return preparerFormulaire(formulaire, true, reference, modele);
        }
        try {
            service.modifierPiece(reference, versDonnees(formulaire));
            redirection.addFlashAttribute("message",
                    msg("admin.catalogue.message.piece-modifiee", formulaire.getLibelle()));
            return LISTE;
        } catch (IllegalArgumentException e) {
            erreurs.rejectValue("tauxTva", "admin.catalogue.erreur.taux");
        } catch (RegleMetierException e) {
            erreurs.rejectValue("codeCategorie", "admin.catalogue.erreur.categorie");
        }
        return preparerFormulaire(formulaire, true, reference, modele);
    }

    // --- retrait (A6, RM-29) ----------------------------------------------------------

    /** Page de confirmation : le diagnostic RM-29 decide de l action proposee. */
    @GetMapping("/{reference}/supprimer")
    public String afficherSuppression(@PathVariable UUID reference, Model modele) {
        modele.addAttribute("titre", msg("admin.catalogue.supprimer.titre"));
        modele.addAttribute("proposition", service.propositionSuppressionPiece(reference));
        return "admin/catalogue/piece-supprimer";
    }

    @PostMapping("/{reference}/supprimer")
    public String supprimer(@PathVariable UUID reference, RedirectAttributes redirection) {
        PropositionSuppression proposition = service.propositionSuppressionPiece(reference);
        try {
            service.supprimerDefinitivementPiece(reference);
            redirection.addFlashAttribute("message",
                    msg("admin.catalogue.message.piece-supprimee", proposition.libelle()));
        } catch (SuppressionRefuseeException e) {
            redirection.addFlashAttribute("erreur",
                    msg("admin.catalogue.erreur.suppression-referencee", e.getNombreReferences()));
        }
        return LISTE;
    }

    @PostMapping("/{reference}/desactiver")
    public String desactiver(@PathVariable UUID reference, RedirectAttributes redirection) {
        ArticleVueAdmin vue = service.vuePiece(reference);
        service.desactiverPiece(reference);
        redirection.addFlashAttribute("message",
                msg("admin.catalogue.message.piece-desactivee", vue.libelle()));
        return LISTE;
    }

    @PostMapping("/{reference}/activer")
    public String activer(@PathVariable UUID reference, RedirectAttributes redirection) {
        ArticleVueAdmin vue = service.vuePiece(reference);
        service.activerPiece(reference);
        redirection.addFlashAttribute("message",
                msg("admin.catalogue.message.piece-activee", vue.libelle()));
        return LISTE;
    }

    // --- helpers ----------------------------------------------------------------------

    private String preparerFormulaire(PieceForm formulaire, boolean edition,
                                      UUID reference, Model modele) {
        modele.addAttribute("titre", msg(edition
                ? "admin.catalogue.piece.modifier.titre"
                : "admin.catalogue.piece.creer.titre"));
        modele.addAttribute("formulaire", formulaire);
        modele.addAttribute("edition", edition);
        modele.addAttribute("reference", reference);
        modele.addAttribute("categories", service.categoriesDePieces());
        modele.addAttribute("tauxAdmis", TauxTvaBelge.TAUX_ADMIS);
        // BL-7 : voir AdminPrestationController, meme raison.
        if (edition) {
            modele.addAttribute("historique", service.historiquePiece(reference));
        }
        return FORMULAIRE;
    }

    private DonneesPiece versDonnees(PieceForm f) {
        return new DonneesPiece(f.getCodeCategorie(), f.getReferenceFabricant(), f.getLibelle(),
                f.getMarque(), f.getDescription(), f.getPrixHtva(), f.getTauxTva(),
                f.getQuantiteStock(), f.getSeuilAlerte(), f.isActif());
    }

    private PieceForm versFormulaire(ArticleVueAdmin vue) {
        PieceForm f = new PieceForm();
        f.setCodeCategorie(vue.categorieCode());
        f.setReferenceFabricant(vue.identifiant());
        f.setLibelle(vue.libelle());
        f.setMarque(vue.marque());
        f.setDescription(vue.description());
        f.setPrixHtva(vue.prixHtva());
        f.setTauxTva(vue.tauxTva());
        f.setQuantiteStock(vue.quantiteStock());
        f.setSeuilAlerte(vue.seuilAlerte());
        f.setActif(vue.actif());
        return f;
    }

    private String msg(String cle, Object... arguments) {
        return messages.getMessage(cle, arguments, LocaleContextHolder.getLocale());
    }
}
