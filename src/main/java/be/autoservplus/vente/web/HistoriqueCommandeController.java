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
 * Historique des commandes du membre connecté et détail de chacune d'elles, point
 * d'accès à ses factures (F31, F32).
 *
 * <p>Sans cet écran, F31 ne serait atteignable que dans la minute suivant le
 * paiement, depuis la page de confirmation : une facture doit rester accessible des
 * années après l'achat.</p>
 *
 * <p>Le rapprochement commande / facture / rétractation se fait <b>ici</b> et non
 * dans un service : chaque module répond sur son propre domaine (la vente ignore la
 * facturation et la rétractation, celles-ci connaissent la commande dont elles
 * découlent) et le contrôleur assemble les vues. Faire descendre ce rapprochement
 * dans {@code CommandeService} inverserait la dépendance entre les modules.</p>
 *
 * <p>L'état de rétractation arrive dans une <b>carte à part</b>, indexée par
 * référence de commande, plutôt qu'en champs supplémentaires de
 * {@code CommandeHistoriqueVue} : celle-ci devrait alors connaître le statut d'une
 * demande d'annulation, c'est-à-dire faire dépendre la vente de la rétractation pour
 * l'affichage d'un bouton. Le gabarit fait la jointure, le modèle reste propre.</p>
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
     * Détail d'une commande passée (F32, CdC P384).
     *
     * <p>Même assemblage que la liste, et pour les mêmes raisons : la vente fournit
     * la commande et ses lignes, la facturation le lien de téléchargement, la
     * rétractation l'état d'annulation. Les trois vues se rejoignent ici, pas dans un
     * service, faire descendre ce rapprochement dans {@code CommandeService}
     * inverserait la dépendance entre les modules.</p>
     *
     * <p>L'éligibilité à la rétractation vient de
     * {@code RetractationService.etatDeLaCommande}, qui partage son calcul avec la
     * liste : le bouton ne peut pas apparaître ici et manquer là-bas.</p>
     *
     * <p>Aucun {@code try/catch} : {@code RessourceIntrouvableException} porte déjà
     * {@code @ResponseStatus(NOT_FOUND)}, donc une commande inconnue (ou celle d'un
     * autre membre) produit un 404 sans code supplémentaire. En catcher une pour la
     * retraduire en 404 n'ajouterait qu'une occasion de se tromper de code.</p>
     */
    @GetMapping("/{reference}")
    public String detail(@AuthenticationPrincipal UserDetails membre,
                         @PathVariable UUID reference,
                         Model modele) {
        String email = membre.getUsername();
        // L'appartenance est vérifiée ici, avant tout le reste : les appels suivants
        // ne doivent pas s'exécuter pour une commande qui n'est pas au membre.
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
