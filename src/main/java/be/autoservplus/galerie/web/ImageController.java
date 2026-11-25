package be.autoservplus.galerie.web;

import be.autoservplus.galerie.service.GalerieService;
import be.autoservplus.stockage.service.StockageMedia;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.Duration;
import java.util.UUID;

/**
 * Service des images du catalogue (BL-9), sous {@code /images/galerie}.
 *
 * <p><b>C'est ce contrôleur qui permet à la CSP de rester {@code img-src 'self'}.</b>
 * Les fichiers vivent hors du webroot et ne sont atteignables par aucun chemin
 * statique. Ils sont servis ici, depuis notre propre domaine. Aucune source externe
 * n'a donc à être autorisée.</p>
 *
 * <p><b>L'adresse porte la référence de la photo, jamais son chemin ni sa clé.</b> Un
 * chemin en paramètre inviterait à le manipuler, une clé séquentielle se devine en
 * comptant (V68). La référence ne désigne que ce que la base connaît, et le chemin réel
 * est résolu côté serveur.</p>
 *
 * <p>Sous {@code /images/**}, en {@code permitAll} : ces images illustrent des fiches
 * publiques et se mettent en cache public. Une photo d'intervention n'est jamais servie
 * ici, elle répond 404 comme une référence inconnue : elle passe par
 * {@link PhotoInterventionController}, réservé au titulaire et au back-office.</p>
 */
@Controller
@RequestMapping("/images/galerie")
public class ImageController {

    private final GalerieService galerie;
    private final StockageMedia stockage;

    public ImageController(GalerieService galerie, StockageMedia stockage) {
        this.galerie = galerie;
        this.stockage = stockage;
    }

    @GetMapping("/{reference}")
    public ResponseEntity<byte[]> servir(@PathVariable UUID reference) {
        // Le contenu d'une image ne change jamais : le nom de fichier est un UUID posé
        // au dépôt, et une modification crée une nouvelle ligne.
        return ReponseImage.de(stockage, galerie.cheminPublic(reference), reference,
                CacheControl.maxAge(Duration.ofDays(30)).cachePublic());
    }

    /** Version de 480 pixels de large, proposée par {@code srcset} aux écrans étroits (V79). */
    @GetMapping("/{reference}/vignette")
    public ResponseEntity<byte[]> servirVignette(@PathVariable UUID reference) {
        return ReponseImage.de(stockage, galerie.cheminVignettePublic(reference), reference,
                CacheControl.maxAge(Duration.ofDays(30)).cachePublic());
    }
}
