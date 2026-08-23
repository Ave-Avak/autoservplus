package be.autoservplus.galerie.service;

import be.autoservplus.catalogue.repository.PieceRepository;
import be.autoservplus.catalogue.repository.PrestationRepository;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.galerie.domain.Photo;
import be.autoservplus.galerie.repository.PhotoRepository;
import be.autoservplus.intervention.repository.InterventionRepository;
import be.autoservplus.stockage.service.StockageMedia;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Alimentation des galeries par le garage (BL-9).
 *
 * <p>{@code @PreAuthorize} de classe en defense en profondeur : la protection d URL
 * {@code /admin/**} filtre deja le role, le service refuse en second.</p>
 *
 * <p><b>Ordre d ecriture : fichier puis base.</b> Le fichier est ecrit avant la ligne
 * pour qu un echec de stockage — disque plein, droits — fasse echouer la transaction
 * avant qu elle ne cree une ligne pointant un fichier absent. L inverse laisserait des
 * images cassees en base. Le cas residuel — transaction annulee apres ecriture du
 * fichier — laisse un fichier orphelin sur le disque : c est un cout de place, pas une
 * incoherence visible, et c est le sens dans lequel il vaut mieux se tromper.</p>
 *
 * <p><b>Suppression : base puis fichier.</b> Symetriquement, la ligne part d abord ;
 * si le fichier resiste, {@code StockageMedia.supprimer} journalise sans lever, et
 * l image a deja disparu de l ecran.</p>
 */
@Service
@Transactional
@PreAuthorize("hasRole('ADMINISTRATEUR')")
public class AdminGalerieService {

    private final PhotoRepository photos;
    private final PrestationRepository prestations;
    private final PieceRepository pieces;
    private final InterventionRepository interventions;
    private final StockageMedia stockage;

    public AdminGalerieService(PhotoRepository photos,
                               PrestationRepository prestations,
                               PieceRepository pieces,
                               InterventionRepository interventions,
                               StockageMedia stockage) {
        this.photos = photos;
        this.prestations = prestations;
        this.pieces = pieces;
        this.interventions = interventions;
        this.stockage = stockage;
    }

    /**
     * Ajoute plusieurs images a la galerie d une prestation.
     *
     * <p>Les fichiers vides sont ignores : un formulaire multi-fichiers envoie une
     * entree vide par champ non rempli, et les refuser ferait echouer un depot
     * partiel parfaitement valable.</p>
     */
    public int ajouterAPrestation(UUID reference, List<MultipartFile> fichiers, String texteAlt) {
        var prestation = prestations.findByReference(reference)
                .orElseThrow(() -> new RessourceIntrouvableException("Prestation", reference));
        short ordre = photos.prochainOrdre(reference, null, null);
        int ajoutees = 0;
        for (MultipartFile fichier : nonVides(fichiers)) {
            String chemin = stockage.enregistrer(fichier, "prestations");
            photos.save(Photo.pourPrestation(prestation, chemin, texteAlt, ordre++));
            ajoutees++;
        }
        return ajoutees;
    }

    public int ajouterAPiece(UUID reference, List<MultipartFile> fichiers, String texteAlt) {
        var piece = pieces.findByReference(reference)
                .orElseThrow(() -> new RessourceIntrouvableException("Piece", reference));
        short ordre = photos.prochainOrdre(null, reference, null);
        int ajoutees = 0;
        for (MultipartFile fichier : nonVides(fichiers)) {
            String chemin = stockage.enregistrer(fichier, "pieces");
            photos.save(Photo.pourPiece(piece, chemin, texteAlt, ordre++));
            ajoutees++;
        }
        return ajoutees;
    }

    /** Photos avant / apres d une intervention (V30). */
    public int ajouterAIntervention(UUID reference, List<MultipartFile> fichiers, String texteAlt) {
        var intervention = interventions.findByReference(reference)
                .orElseThrow(() -> new RessourceIntrouvableException("Intervention", reference));
        short ordre = photos.prochainOrdre(null, null, reference);
        int ajoutees = 0;
        for (MultipartFile fichier : nonVides(fichiers)) {
            String chemin = stockage.enregistrer(fichier, "interventions");
            photos.save(Photo.pourIntervention(intervention, chemin, texteAlt, ordre++));
            ajoutees++;
        }
        return ajoutees;
    }

    /** Retire une image de la galerie et efface son fichier. */
    public void supprimer(Long id) {
        Photo photo = photos.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Photo", id));
        String chemin = photo.getChemin();
        photos.delete(photo);
        stockage.supprimer(chemin);
    }

    private static List<MultipartFile> nonVides(List<MultipartFile> fichiers) {
        return fichiers == null ? List.of()
                : fichiers.stream().filter(f -> f != null && !f.isEmpty()).toList();
    }
}
