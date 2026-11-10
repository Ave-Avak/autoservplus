package be.autoservplus.intervention.web;

import be.autoservplus.intervention.service.HistoriqueEntretienService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

/**
 * Historique d'entretien complet d'un véhicule au back-office (F21), atteint depuis le
 * détail d'une intervention. Une référence inconnue répond 404.
 */
@Controller
@RequestMapping("/admin/vehicules")
public class AdminHistoriqueVehiculeController {

    private final HistoriqueEntretienService service;

    public AdminHistoriqueVehiculeController(HistoriqueEntretienService service) {
        this.service = service;
    }

    @GetMapping("/{reference}/historique")
    public String historique(@PathVariable UUID reference, Model modele) {
        modele.addAttribute("historique", service.pourGarage(reference));
        return "admin/vehicule-historique";
    }
}
