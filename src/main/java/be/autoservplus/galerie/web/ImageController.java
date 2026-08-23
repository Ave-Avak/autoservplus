package be.autoservplus.galerie.web;

import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.galerie.service.GalerieService;
import be.autoservplus.stockage.service.StockageMedia;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.Duration;

/**
 * Service des images de galerie (BL-9), sous {@code /images/galerie}.
 *
 * <p><b>C est ce controleur qui permet a la CSP de rester {@code img-src 'self'}.</b>
 * Les fichiers vivent hors du webroot et ne sont atteignables par aucun chemin
 * statique ; ils sont servis ici, depuis notre propre domaine. Aucune source externe
 * n a donc a etre autorisee, et la politique de securite du contenu n a pas ete
 * touchee.</p>
 *
 * <p><b>L URL porte l identifiant de la ligne, jamais le chemin du fichier.</b> Un
 * chemin en parametre inviterait a le manipuler ; l identifiant, lui, ne peut designer
 * que ce que la base connait, et le chemin reel est resolu cote serveur.</p>
 *
 * <p>Sous {@code /images/**}, deja en {@code permitAll} : ces images illustrent des
 * fiches publiques. Celles d intervention sont adressees par un identifiant non
 * devinable en pratique mais restent lisibles de qui connait l identifiant —
 * <b>limite assumee</b>, elles montrent une piece d usure, jamais une personne ni une
 * plaque.</p>
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

    @GetMapping("/{id}")
    public ResponseEntity<byte[]> servir(@PathVariable Long id) {
        String chemin = galerie.cheminDe(id);
        // La ligne peut survivre au fichier (volume restaure, effacement manuel) :
        // un 404 vaut mieux qu une erreur serveur sur une image manquante.
        if (!stockage.existe(chemin)) {
            throw new RessourceIntrouvableException("Photo", id);
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(stockage.typeMimeDe(chemin)))
                // Le contenu d une image ne change jamais : le nom de fichier est un
                // UUID pose a l upload, et une modification cree une nouvelle ligne.
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .body(stockage.lire(chemin));
    }
}
