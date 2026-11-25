package be.autoservplus.galerie.service;

import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.galerie.repository.PhotoRepository;
import be.autoservplus.galerie.service.dto.PhotoVue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Consultation des galeries (BL-9).
 *
 * <p><b>Sans garde de sécurité, et c'est voulu</b> : les images de prestations et de
 * pièces illustrent des fiches publiques ({@code /services/**} et {@code /pieces/**}
 * sont en {@code permitAll}). Seules celles d'intervention sont privées, et leur
 * cloisonnement se fait chez l'appelant, qui a déjà vérifié à qui appartient
 * l'intervention : le refaire ici demanderait de repasser le courriel du membre à
 * chaque lecture d'image. Ce service garantit seulement qu'une photo d'intervention ne
 * sort jamais par la lecture publique, et qu'une photo n'est lue que dans
 * l'intervention à laquelle elle appartient.</p>
 */
@Service
@Transactional(readOnly = true)
public class GalerieService {

    private final PhotoRepository photos;

    public GalerieService(PhotoRepository photos) {
        this.photos = photos;
    }

    public List<PhotoVue> dePrestation(UUID reference) {
        return photos.dePrestation(reference).stream().map(PhotoVue::de).toList();
    }

    public List<PhotoVue> dePiece(UUID reference) {
        return photos.dePiece(reference).stream().map(PhotoVue::de).toList();
    }

    /** Photos avant / après d'une intervention. L'appelant a vérifié l'appartenance. */
    public List<PhotoVue> dIntervention(UUID reference) {
        return photos.dIntervention(reference).stream().map(PhotoVue::de).toList();
    }

    /**
     * Chemin de stockage d'une photo du catalogue, pour la servir à tous. Une photo
     * d'intervention répond comme une référence inconnue : dire qu'elle existe
     * confirmerait la référence à qui la devine.
     */
    public String cheminPublic(UUID photo) {
        return photos.duCatalogue(photo)
                .orElseThrow(() -> new RessourceIntrouvableException("Photo", photo))
                .getChemin();
    }

    /**
     * Chemin de stockage d'une photo de cette intervention. L'appelant a vérifié que le
     * lecteur a droit à l'intervention, ce service vérifie que la photo en fait partie.
     */
    public String cheminDIntervention(UUID intervention, UUID photo) {
        return photos.dUneIntervention(intervention, photo)
                .orElseThrow(() -> new RessourceIntrouvableException("Photo", photo))
                .getChemin();
    }
}
