package be.autoservplus.vente.web;

import be.autoservplus.common.web.PeriodeFiltre;
import be.autoservplus.facturation.service.AdminFacturationService;
import be.autoservplus.retractation.service.AdminRetractationService;
import be.autoservplus.vente.domain.StatutCommande;
import be.autoservplus.vente.service.AdminCommandeService;
import be.autoservplus.vente.web.dto.CommandeAdminDetail;
import be.autoservplus.vente.web.dto.CommandeAdminLigne;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Commandes consultées depuis le back-office.
 *
 * <p>Le détail rapproche trois modules, la vente, la facturation et la rétractation,
 * comme le détail du membre et pour la même raison : le faire dans un service inverserait
 * la dépendance entre eux.</p>
 */
@Controller
@RequestMapping("/admin/commandes")
public class AdminCommandeController {

    private final AdminCommandeService commandes;
    private final AdminFacturationService facturation;
    private final AdminRetractationService retractations;

    public AdminCommandeController(AdminCommandeService commandes,
                                   AdminFacturationService facturation,
                                   AdminRetractationService retractations) {
        this.commandes = commandes;
        this.facturation = facturation;
        this.retractations = retractations;
    }

    /** {@code page} commence à 1 dans l'adresse, à 0 pour le service. */
    @GetMapping
    public String liste(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
                        @RequestParam(required = false) StatutCommande statut,
                        @RequestParam(required = false) String recherche,
                        @RequestParam(defaultValue = "1") int page,
                        Model modele) {
        PeriodeFiltre periode = commandes.periode(du, au);
        Page<CommandeAdminLigne> resultats = commandes.rechercher(periode, statut, recherche, page - 1);
        modele.addAttribute("periode", periode);
        modele.addAttribute("statut", statut);
        modele.addAttribute("statuts", StatutCommande.values());
        modele.addAttribute("recherche", recherche);
        modele.addAttribute("resultats", resultats);
        return "admin/commandes";
    }

    @GetMapping("/{reference}")
    public String detail(@PathVariable UUID reference, Model modele) {
        CommandeAdminDetail commande = commandes.detail(reference);
        modele.addAttribute("commande", commande);
        modele.addAttribute("facture", facturation.factureDeLaCommande(reference).orElse(null));
        modele.addAttribute("avoir", facturation.avoirDeLaCommande(reference).orElse(null));
        modele.addAttribute("retractations", retractations.historiqueDeLaCommande(reference));
        return "admin/commande-detail";
    }
}
