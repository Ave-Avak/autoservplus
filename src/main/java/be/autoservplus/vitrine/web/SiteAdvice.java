package be.autoservplus.vitrine.web;

import be.autoservplus.config.IdentiteGarage;
import be.autoservplus.vitrine.service.HorairesOuvertureService;
import be.autoservplus.vitrine.service.EtatDuJourService;
import be.autoservplus.vitrine.web.dto.EtatDuJour;
import be.autoservplus.vitrine.web.dto.HorairesGroupesVue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataAccessException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.thymeleaf.context.LazyContextVariable;

import java.util.List;

/**
 * Ce que le gabarit du site public affiche sur chaque page : le garage, ses horaires en
 * résumé pour le pied, l'état du jour pour le bandeau et, sous les profils de démonstration, la mention qui dit que tout y est fictif.
 *
 * <p>Les horaires viennent de {@code plage_ouverture}, comme sur la page de contact, et
 * sont rendus en variable paresseuse : la requête ne part que si la page affiche le
 * bandeau ou le pied du site. Les réponses JSON, les redirections et les écrans encore
 * sous l'ancien gabarit ne la paient pas.</p>
 *
 * <p>L'identité est celle de la configuration, dans la variante de la langue affichée
 * (forme juridique, rue et localité en néerlandais quand elles sont renseignées).</p>
 *
 * <p>Injection par {@link ObjectProvider}, comme {@code PanierModelAdvice} : les tests
 * {@code @WebMvcTest} instancient tous les advices sans fournir ces deux beans, et le
 * gabarit commun n'y est pas rendu.</p>
 *
 * <p>Une panne de la base pendant le rendu ne casse pas la page : le bandeau et le pied
 * perdent les horaires et l'état du jour, la page s'affiche quand même. C'est d'abord la
 * page d'erreur qui en dépend, celle qui annonce justement la panne.</p>
 */
@ControllerAdvice
public class SiteAdvice {

    private static final Logger JOURNAL = LoggerFactory.getLogger(SiteAdvice.class);

    private final ObjectProvider<IdentiteGarage> garage;
    private final ObjectProvider<HorairesOuvertureService> horaires;
    private final ObjectProvider<EtatDuJourService> etatDuJour;
    private final boolean mentionDemonstration;

    public SiteAdvice(ObjectProvider<IdentiteGarage> garage, ObjectProvider<HorairesOuvertureService> horaires,
                      ObjectProvider<EtatDuJourService> etatDuJour,
                      @Value("${autoservplus.vitrine.mention-demonstration:false}") boolean mentionDemonstration) {
        this.garage = garage;
        this.horaires = horaires;
        this.etatDuJour = etatDuJour;
        this.mentionDemonstration = mentionDemonstration;
    }

    @ModelAttribute
    public void exposerLeSite(Model modele) {
        modele.addAttribute("mentionDemonstration", mentionDemonstration);
        IdentiteGarage identite = garage.getIfAvailable();
        if (identite != null) {
            modele.addAttribute("garageSite", identite.pour(LocaleContextHolder.getLocale()));
        }
        HorairesOuvertureService service = horaires.getIfAvailable();
        modele.addAttribute("horairesResume", new LazyContextVariable<List<HorairesGroupesVue>>() {
            @Override
            protected List<HorairesGroupesVue> loadValue() {
                if (service == null) {
                    return List.of();
                }
                try {
                    return service.resume();
                } catch (DataAccessException panne) {
                    JOURNAL.warn("Horaires du pied indisponibles, page rendue sans eux", panne);
                    return List.of();
                }
            }
        });
        EtatDuJourService etat = etatDuJour.getIfAvailable();
        modele.addAttribute("etatDuJour", new LazyContextVariable<EtatDuJour>() {
            @Override
            protected EtatDuJour loadValue() {
                if (etat == null) {
                    return null;
                }
                try {
                    return etat.maintenant();
                } catch (DataAccessException panne) {
                    JOURNAL.warn("État du jour indisponible, bandeau rendu sans lui", panne);
                    return null;
                }
            }
        });
    }
}
