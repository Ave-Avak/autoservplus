package be.autoservplus.vitrine.web;

import be.autoservplus.config.IdentiteGarage;
import be.autoservplus.vitrine.service.HorairesOuvertureService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Page de contact publique du garage.
 *
 * <p>Elle repond a une obligation, pas a un confort : l article VI.45 du Code de
 * droit economique impose au professionnel qui vend a distance de rendre
 * accessibles son identite, son adresse geographique, son telephone et son adresse
 * de courriel <b>avant</b> que le consommateur ne soit lie. Le site les portait
 * jusqu ici sur les seules mentions legales, atteignables depuis le pied de page ;
 * une page de contact nommee comme telle est la forme sous laquelle un visiteur les
 * cherche reellement.</p>
 *
 * <p><b>Rien n est ecrit en dur ici.</b> L identite vient de la configuration
 * {@code autoservplus.garage.*} — la meme qui imprime l en-tete des factures et
 * alimente les pages legales — et les horaires viennent de {@code plage_ouverture},
 * la table dont sont deduits les creneaux reservables. Les deux blocs de cette page
 * sont donc, par construction, ceux que le client rencontrera ensuite sur son devis
 * et dans l agenda de reservation.</p>
 *
 * <p>Aucun service intermediaire pour l identite, meme choix qu aux pages legales :
 * le controleur n assemble que des donnees deja produites ailleurs. Les horaires,
 * eux, demandent un regroupement et une notion de fermeture : cela vaut un service
 * ({@link HorairesOuvertureService}), pas une boucle dans le gabarit.</p>
 */
@Controller
public class VitrineController {

    private final IdentiteGarage garage;
    private final HorairesOuvertureService horaires;

    public VitrineController(IdentiteGarage garage, HorairesOuvertureService horaires) {
        this.garage = garage;
        this.horaires = horaires;
    }

    @GetMapping("/contact")
    public String contact(Model modele) {
        modele.addAttribute("garage", garage);
        modele.addAttribute("horaires", horaires.semaine());
        return "vitrine/contact";
    }
}
