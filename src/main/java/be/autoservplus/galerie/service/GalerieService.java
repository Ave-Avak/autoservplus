package be.autoservplus.galerie.service;

import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.galerie.repository.PhotoRepository;
import be.autoservplus.galerie.domain.Photo;
import be.autoservplus.galerie.service.dto.PhotoVue;
import be.autoservplus.i18n.LanguesSupportees;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
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
        return vues(photos.dePrestation(reference));
    }

    /** Photos propres d'une pièce, telles que le back-office les gère. */
    public List<PhotoVue> dePiece(UUID reference) {
        return vues(photos.dePiece(reference));
    }

    /**
     * Illustrations de la fiche publique d'une pièce : ses photos propres, ou, à défaut, le
     * visuel de sa catégorie (V79). Un visuel commun évite une ligne de photo par article
     * qui pointerait le même fichier, et qu'un retrait au back-office effacerait pour tous.
     */
    public List<PhotoVue> illustrationsDePiece(UUID reference) {
        List<Photo> propres = photos.dePiece(reference);
        return vues(propres.isEmpty() ? photos.deLaCategorieDeLaPiece(reference) : propres);
    }

    /** Photos avant / après d'une intervention. L'appelant a vérifié l'appartenance. */
    public List<PhotoVue> dIntervention(UUID reference) {
        return vues(photos.dIntervention(reference));
    }

    private static List<PhotoVue> vues(List<Photo> liste) {
        Locale langue = LanguesSupportees.plusProche(LocaleContextHolder.getLocale());
        return liste.stream().map(photo -> PhotoVue.de(photo, langue)).toList();
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
     * Chemin de la vignette d'une photo du catalogue. Une photo sans vignette répond comme
     * une référence inconnue : le gabarit ne propose la vignette que si elle existe.
     */
    public String cheminVignettePublic(UUID photo) {
        String vignette = photos.duCatalogue(photo)
                .orElseThrow(() -> new RessourceIntrouvableException("Photo", photo))
                .getCheminVignette();
        if (vignette == null) {
            throw new RessourceIntrouvableException("Photo", photo);
        }
        return vignette;
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
