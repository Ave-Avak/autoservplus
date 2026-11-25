package be.autoservplus.galerie.web;

import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.stockage.service.StockageMedia;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

/** Réponse commune aux deux contrôleurs qui servent une image, publique ou privée. */
final class ReponseImage {

    private ReponseImage() {
    }

    static ResponseEntity<byte[]> de(StockageMedia stockage, String chemin, UUID photo,
                                     CacheControl cache) {
        // La ligne peut survivre au fichier (volume restauré, effacement manuel) : un
        // 404 vaut mieux qu'une erreur serveur sur une image manquante.
        if (!stockage.existe(chemin)) {
            throw new RessourceIntrouvableException("Photo", photo);
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(stockage.typeMimeDe(chemin)))
                .cacheControl(cache)
                .body(stockage.lire(chemin));
    }
}
