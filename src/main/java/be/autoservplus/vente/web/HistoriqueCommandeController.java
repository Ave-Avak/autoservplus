package be.autoservplus.vente.web;

import be.autoservplus.facturation.service.FactureService;
import be.autoservplus.facturation.service.dto.FactureVue;
import be.autoservplus.vente.service.CommandeService;
import be.autoservplus.vente.web.dto.CommandeHistoriqueVue;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Historique des commandes du membre connecte, et point d acces a ses factures
 * (F31 ; F32 restreint a la liste, le detail d une commande reste a faire).
 *
 * <p>Sans cet ecran, F31 ne serait atteignable que dans la minute suivant le
 * paiement, depuis la page de confirmation : une facture doit rester accessible des
 * annees apres l achat.</p>
 *
 * <p>Le rapprochement commande / facture se fait <b>ici</b> et non dans un service :
 * chaque module repond sur son propre domaine — la vente ignore la facturation, la
 * facturation connait la commande dont elle est issue — et le controleur assemble
 * les deux vues. Faire descendre ce rapprochement dans {@code CommandeService}
 * inverserait la dependance entre les deux modules.</p>
 */
@Controller
@RequestMapping("/commandes")
public class HistoriqueCommandeController {

    private final CommandeService commandes;
    private final FactureService factures;
    private final MessageSource messages;

    public HistoriqueCommandeController(CommandeService commandes, FactureService factures,
                                        MessageSource messages) {
        this.commandes = commandes;
        this.factures = factures;
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
        return "vente/commandes";
    }

    private String msg(String cle, Object... arguments) {
        return messages.getMessage(cle, arguments, LocaleContextHolder.getLocale());
    }
}
