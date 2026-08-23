package be.autoservplus.avis.service;

import be.autoservplus.avis.domain.Avis;
import be.autoservplus.avis.repository.AvisRepository;
import be.autoservplus.avis.service.dto.AvisVue;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.reservation.repository.ParametreAtelierRepository;
import be.autoservplus.reservation.service.support.FormatageRdv;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Moderation des avis par le garage (BL-4).
 *
 * <p><b>A posteriori, jamais a priori.</b> {@code avis.publie} vaut {@code true} par
 * defaut en base : l avis est en ligne des son depot, et la moderation sert a retirer
 * ce qui est illicite, diffamatoire ou hors sujet. Un dispositif ou le garage
 * approuverait chaque avis avant publication ne produirait plus des avis mais une
 * selection, ce que la pratique commerciale loyale (livre VI du Code de droit
 * economique) n admet pas.</p>
 *
 * <p><b>Masquer n est pas supprimer.</b> Un avis retire de l affichage garde sa ligne,
 * son auteur et sa date : le garage doit pouvoir justifier un retrait, et une
 * suppression physique effacerait la preuve de ce qui a ete retire.</p>
 *
 * <p><b>Deux axes independants</b>, comme le schema du socle les a prevus :
 * {@code publie} dit ce que le public voit, {@code signale} marque un avis a examiner.
 * Un avis peut etre signale sans etre masque (un doute qui n a pas encore ete tranche)
 * ou masque sans etre signale (retrait assume). Le signalement <b>par le membre</b>
 * reste une evolution V2 : en V1 les deux axes sont poses par le garage.</p>
 *
 * <p>{@code @PreAuthorize} de classe en defense en profondeur : la protection d URL
 * {@code /admin/**} filtre deja le role, le service refuse en second.</p>
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasRole('ADMINISTRATEUR')")
public class AdminAvisService {

    private final AvisRepository avis;
    private final ParametreAtelierRepository parametres;

    public AdminAvisService(AvisRepository avis, ParametreAtelierRepository parametres) {
        this.avis = avis;
        this.parametres = parametres;
    }

    /** Tous les avis, masques compris : la moderation doit voir ce qu elle a retire. */
    public List<AvisVue> tous() {
        ZoneId zone = parametres.courants().zone();
        return avis.tousPourModeration().stream()
                .map(a -> AvisVue.de(a, FormatageRdv.jourLisible(a.getDateDepot(), zone)))
                .toList();
    }

    @Transactional
    public void masquer(UUID reference) {
        charger(reference).masquer();
    }

    @Transactional
    public void publier(UUID reference) {
        charger(reference).publier();
    }

    @Transactional
    public void signaler(UUID reference) {
        charger(reference).signaler();
    }

    @Transactional
    public void leverLeSignalement(UUID reference) {
        charger(reference).leverLeSignalement();
    }

    private Avis charger(UUID reference) {
        return avis.findByReference(reference)
                .orElseThrow(() -> new RessourceIntrouvableException("Avis", reference));
    }
}
