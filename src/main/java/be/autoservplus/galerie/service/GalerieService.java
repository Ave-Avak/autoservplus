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
 * <p><b>Sans garde de securite, et c est voulu</b> : les images de prestations et de
 * pieces illustrent des fiches publiques ({@code /services/**} et {@code /pieces/**}
 * sont en {@code permitAll}). Seules celles d intervention sont privees, et leur
 * cloisonnement se fait chez l appelant, qui a deja verifie a qui appartient
 * l intervention — le refaire ici demanderait de repasser le courriel du membre a
 * chaque lecture d image.</p>
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

    /** Photos avant / apres d une intervention. L appelant a verifie l appartenance. */
    public List<PhotoVue> dIntervention(UUID reference) {
        return photos.dIntervention(reference).stream().map(PhotoVue::de).toList();
    }

    /** Chemin de stockage d une image, pour la servir. */
    public String cheminDe(Long id) {
        return photos.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Photo", id))
                .getChemin();
    }
}
