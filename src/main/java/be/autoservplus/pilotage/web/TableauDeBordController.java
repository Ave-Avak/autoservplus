package be.autoservplus.pilotage.web;

import be.autoservplus.pilotage.service.ExportTableauDeBordService;
import be.autoservplus.pilotage.service.TableauDeBordService;
import be.autoservplus.pilotage.service.dto.PeriodeTableau;
import be.autoservplus.pilotage.service.dto.TableauDeBord;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * Tableau de bord du gérant (A25), à la racine du back-office.
 *
 * <p>Servi sur {@code /admin} : c'est la première page que voit le garage, et elle
 * donne accès aux autres écrans d'administration.</p>
 *
 * <p>La protection d'URL {@code /admin/**} filtre le rôle ADMINISTRATEUR, et les
 * services redoublent par {@code @PreAuthorize}. <b>Aucun calcul ici</b> : le contrôleur
 * lit la période dans l'adresse et rend la vue. La période voyage en paramètres de
 * requête, pour qu'une vue se garde en favori et que l'export reprenne celle de l'écran.</p>
 */
@Controller
public class TableauDeBordController {

    private final TableauDeBordService tableauDeBord;
    private final ExportTableauDeBordService export;

    public TableauDeBordController(TableauDeBordService tableauDeBord, ExportTableauDeBordService export) {
        this.tableauDeBord = tableauDeBord;
        this.export = export;
    }

    @GetMapping("/admin")
    public String afficher(@RequestParam(required = false) String periode,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
                           Model modele) {
        TableauDeBord bord = tableauDeBord.pour(tableauDeBord.periode(periode, du, au));
        modele.addAttribute("bord", bord);
        modele.addAttribute("courbe", Graphiques.courbe(bord.trenteJours()));
        modele.addAttribute("barres", Graphiques.barres(bord.parCategorie()));
        modele.addAttribute("epaisseur", Graphiques.epaisseur());
        return "admin/tableau-de-bord";
    }

    @GetMapping("/admin/tableau-de-bord/indicateurs.csv")
    public ResponseEntity<byte[]> indicateurs(
            @RequestParam(required = false) String periode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au) {
        PeriodeTableau retenue = tableauDeBord.periode(periode, du, au);
        return fichier(export.indicateursEnCsv(retenue),
                "tableau-de-bord-%s-%s.csv".formatted(retenue.du(), retenue.au()));
    }

    @GetMapping("/admin/tableau-de-bord/chiffre-quotidien.csv")
    public ResponseEntity<byte[]> chiffreQuotidien() {
        return fichier(export.chiffreQuotidienEnCsv(), "chiffre-affaires-30-jours.csv");
    }

    /**
     * {@code text/csv} en UTF-8 et {@code attachment} : le navigateur propose
     * l'enregistrement au lieu d'afficher le contenu dans la page. Les chiffres d'un
     * garage ne restent dans aucun cache partagé.
     */
    private static ResponseEntity<byte[]> fichier(String csv, String nom) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(nom, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}
