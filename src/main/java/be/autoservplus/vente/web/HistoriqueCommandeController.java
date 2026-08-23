package be.autoservplus.vente.web;

import be.autoservplus.facturation.service.FactureService;
import be.autoservplus.facturation.service.dto.FactureVue;
import be.autoservplus.retractation.service.RetractationService;
import be.autoservplus.vente.service.CommandeService;
import be.autoservplus.vente.web.dto.CommandeDetailVue;
import be.autoservplus.vente.web.dto.CommandeHistoriqueVue;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Historique des commandes du membre connecte et detail de chacune d elles, point
 * d acces a ses factures (F31, F32).
 *
 * <p>Sans cet ecran, F31 ne serait atteignable que dans la minute suivant le
 * paiement, depuis la page de confirmation : une facture doit rester accessible des
 * annees apres l achat.</p>
 *
 * <p>Le rapprochement commande / facture / retractation se fait <b>ici</b> et non
 * dans un service : chaque module repond sur son propre domaine — la vente ignore la
 * facturation et la retractation, celles-ci connaissent la commande dont elles
 * decoulent — et le controleur assemble les vues. Faire descendre ce rapprochement
 * dans {@code CommandeService} inverserait la dependance entre les modules.</p>
 *
 * <p>L etat de retractation arrive dans une <b>carte a part</b>, indexee par
 * reference de commande, plutot qu en champs supplementaires de
 * {@code CommandeHistoriqueVue} : celle-ci devrait alors connaitre le statut d une
 * demande d annulation, c est-a-dire faire dependre la vente de la retractation pour
 * l affichage d un bouton. Le gabarit fait la jointure, le modele reste propre.</p>
 */
@Controller
@RequestMapping("/commandes")
public class HistoriqueCommandeController {

    private final CommandeService commandes;
    private final FactureService factures;
    private final RetractationService retractations;
    private final MessageSource messages;

    public HistoriqueCommandeController(CommandeService commandes, FactureService factures,
                                        RetractationService retractations,
                                        MessageSource messages) {
        this.commandes = commandes;
        this.factures = factures;
        this.retractations = retractations;
        this.messages = messages;
    }

    @GetMapping
    public String historique(@AuthenticationPrincipal UserDetails membre, Model modele) {
        String email = membre.getUsername();
        Map<UUID, FactureVue> parCommande = factures.facturesDuMembre(email).stream()
                .filter(facture -> facture.referenceCommande() != null)
                .collect(java.util.stream.Collectors.toMap(
                        FactureVue::referenceCommande, Function.identity()));

        List<CommandeHistoriqueVue> lignes = commandes.historiqueDuMembre(email).stream()
                .map(commande -> {
                    FactureVue facture = parCommande.get(commande.reference());
                    return facture == null ? commande
                            : commande.avecFacture(facture.reference(), facture.numero());
                })
                .toList();

        modele.addAttribute("titre", msg("commandes.titre"));
        modele.addAttribute("commandes", lignes);
        modele.addAttribute("retractations", retractations.etatsDuMembre(email));
        return "vente/commandes";
    }

    /**
     * Detail d une commande passee (F32, CdC P384).
     *
     * <p>Meme assemblage que la liste, et pour les memes raisons : la vente fournit
     * la commande et ses lignes, la facturation le lien de telechargement, la
     * retractation l etat d annulation. Les trois vues se rejoignent ici, pas dans un
     * service — faire descendre ce rapprochement dans {@code CommandeService}
     * inverserait la dependance entre les modules.</p>
     *
     * <p>L eligibilite a la retractation vient de
     * {@code RetractationService.etatDeLaCommande}, qui partage son calcul avec la
     * liste : le bouton ne peut pas apparaitre ici et manquer la-bas.</p>
     *
     * <p>Aucun {@code try/catch} : {@code RessourceIntrouvableException} porte deja
     * {@code @ResponseStatus(NOT_FOUND)}, donc une commande inconnue — ou celle d un
     * autre membre — produit un 404 sans code supplementaire. En catcher une pour la
     * retraduire en 404 n ajouterait qu une occasion de se tromper de code.</p>
     */
    @GetMapping("/{reference}")
    public String detail(@AuthenticationPrincipal UserDetails membre,
                         @PathVariable UUID reference,
                         Model modele) {
        String email = membre.getUsername();
        // L appartenance est verifiee ici, avant tout le reste : les appels suivants
        // ne doivent pas s executer pour une commande qui n est pas au membre.
        CommandeDetailVue commande = commandes.detail(reference, email);

        CommandeDetailVue vue = factures.factureDe(reference)
                .map(facture -> commande.avecFacture(facture.getReference(), facture.getNumero()))
                .orElse(commande);

        modele.addAttribute("titre", msg("commande.detail.titre", vue.numero()));
        modele.addAttribute("commande", vue);
        modele.addAttribute("retractation", retractations.etatDeLaCommande(email, reference));
        return "vente/commande-detail";
    }

    private String msg(String cle, Object... arguments) {
        return messages.getMessage(cle, arguments, LocaleContextHolder.getLocale());
    }
}
