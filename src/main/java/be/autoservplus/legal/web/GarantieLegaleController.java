package be.autoservplus.legal.web;

import be.autoservplus.legal.service.NoticeGarantie;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Notice harmonisée de l'Union européenne sur la garantie légale de conformité (art.
 * VI.45 § 1er, 12° CDE, règlement d'exécution (UE) 2025/1960).
 *
 * <p>Une page publique plutôt qu'un bloc répété sur chaque écran : la notice se lit en
 * entier, et le parcours d'achat d'une pièce y renvoie par une phrase de lien, comme le
 * proposent les lignes directrices de la Commission pour la vente en ligne.</p>
 */
@Controller
public class GarantieLegaleController {

    private final NoticeGarantie notice;

    public GarantieLegaleController(NoticeGarantie notice) {
        this.notice = notice;
    }

    @GetMapping("/garantie-legale")
    public String garantieLegale(Model modele) {
        modele.addAttribute("imageNotice", notice.image(LocaleContextHolder.getLocale()));
        return "legal/garantie-legale";
    }
}
