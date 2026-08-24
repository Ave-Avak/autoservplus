package be.autoservplus.legal.web;

import be.autoservplus.config.IdentiteGarage;
import be.autoservplus.retractation.service.RetractationService;
import be.autoservplus.rgpd.service.CatalogueTraitements;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Documents legaux publics : conditions generales de vente, mentions legales et
 * politique de confidentialite.
 *
 * <p>Ces trois adresses etaient <b>declarees ouvertes</b> dans la configuration de
 * securite et referencees par treize liens de gabarits — dont celui qui accompagne
 * la case d acceptation obligatoire du recapitulatif de commande — sans qu aucun
 * controleur ne les serve : elles repondaient 404. Faire accepter des conditions
 * generales qu on ne peut pas lire n est pas une maladresse d ergonomie, c est un
 * defaut de formation du contrat.</p>
 *
 * <p><b>Aucune logique metier ici, et aucun service intermediaire non plus.</b> Les
 * pages n assemblent que des donnees deja produites ailleurs : l identite legale
 * vient de la configuration {@code autoservplus.garage.*}, qui alimente aussi
 * l en-tete des factures, et le registre des traitements vient de
 * {@link CatalogueTraitements}, qui alimente l export RGPD de l article 15.
 * Interposer un service ne ferait que recopier ces deux dependances. Le point qui
 * compte est la <b>source unique</b> : la politique de confidentialite affichee au
 * public et le registre joint a l export d un membre ne peuvent pas diverger,
 * puisqu ils sont resolus par le meme composant.</p>
 *
 * <p>Le contenu redactionnel est <b>complet</b> : les onze clauses laissees en blanc
 * a la livraison initiale sont redigees. La <b>banniere de brouillon</b> reste
 * pourtant en tete de chaque page, et ce n est pas un oubli — elle n a jamais
 * annonce qu il manquait du texte, mais qu <b>aucun juriste n a relu celui-ci</b>.
 * Les deux etats sont distincts, et le second est le plus dangereux : un document
 * inacheve se voit, un document complet mais non valide se lit comme definitif.
 * La banniere tombera sur validation juridique, pas sur completude redactionnelle.</p>
 *
 * <p>Le principe qui a preside aux blancs vaut toujours pour ce qui les a remplaces :
 * aucune affirmation n est inventee. L identite vient de la configuration, l hebergeur
 * et les sous-traitants sont nommes parce qu ils sont des faits de deploiement, et les
 * regles citees renvoient a des textes existants. Sur un document qui engage le garage,
 * une phrase plausible est pire qu un blanc, parce qu elle ne se voit pas.</p>
 */
@Controller
public class DocumentsLegauxController {

    private final IdentiteGarage garage;
    private final CatalogueTraitements traitements;

    public DocumentsLegauxController(IdentiteGarage garage, CatalogueTraitements traitements) {
        this.garage = garage;
        this.traitements = traitements;
    }

    @GetMapping("/cgv")
    public String conditionsGenerales(Model modele) {
        modele.addAttribute("garage", garage);
        // Le delai est lu sur la constante qui l applique reellement (F30) : si la
        // regle change, le texte affiche change avec elle plutot que de mentir.
        modele.addAttribute("delaiRetractationJours", RetractationService.DELAI_LEGAL.toDays());
        return "legal/cgv";
    }

    @GetMapping("/mentions-legales")
    public String mentionsLegales(Model modele) {
        modele.addAttribute("garage", garage);
        return "legal/mentions-legales";
    }

    @GetMapping("/confidentialite")
    public String politiqueDeConfidentialite(Model modele) {
        modele.addAttribute("garage", garage);
        modele.addAttribute("registre",
                traitements.informationsTraitement(LocaleContextHolder.getLocale()));
        return "legal/confidentialite";
    }
}
