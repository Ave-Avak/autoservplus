package be.autoservplus.vitrine.service;

import be.autoservplus.reservation.domain.PlageOuverture;
import be.autoservplus.reservation.repository.PlageOuvertureRepository;
import be.autoservplus.vitrine.web.dto.JourOuvertureVue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Horaires d ouverture publies sur la vitrine, derives de la table
 * {@code plage_ouverture}.
 *
 * <p><b>Source unique, et c est tout l interet :</b> ces plages sont exactement
 * celles a partir desquelles {@code DisponibiliteService} calcule les creneaux
 * reservables. Une configuration d horaires « d affichage » separee aurait ete plus
 * simple a ecrire et fausse a la premiere modification : le garage aurait ferme le
 * samedi dans le moteur de reservation et serait reste ouvert sur sa page de
 * contact. Un horaire affiche qui contredit l agenda reel est pire qu un horaire
 * absent — le client se deplace.</p>
 *
 * <p>Le format d heure est {@code HH:mm}, volontairement <b>insensible a la
 * locale</b> d affichage : le neerlandais et l anglais britannique ne notent pas
 * l heure comme le francais, mais un horaire de garage se lit de la meme facon dans
 * les trois versions du site, et une bascule de langue ne doit pas donner
 * l impression que les horaires ont change.</p>
 */
@Service
@Transactional(readOnly = true)
public class HorairesOuvertureService {

    private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("HH:mm");

    /** Tiret demi-cadratin : ponctuation, pas un libelle — rien a traduire. */
    private static final String SEPARATEUR = " – ";

    private final PlageOuvertureRepository plages;

    public HorairesOuvertureService(PlageOuvertureRepository plages) {
        this.plages = plages;
    }

    /**
     * Les sept jours dans l ordre ISO (lundi ... dimanche), fermetures comprises.
     *
     * <p>La semaine est rendue <b>entiere</b> et non reduite aux jours ouverts :
     * afficher « Fermé » est une information, sauter la ligne n en est pas une —
     * le lecteur ne saurait pas distinguer un dimanche de fermeture d un oubli de
     * saisie.</p>
     */
    public List<JourOuvertureVue> semaine() {
        Map<DayOfWeek, List<String>> parJour = new EnumMap<>(DayOfWeek.class);
        for (PlageOuverture plage : plages.findAllByOrderByJourSemaineAscHeureDebutAsc()) {
            // Le repository ne filtre pas l activite : une plage desactivee reste en
            // base pour l historique mais ne doit pas etre annoncee au public.
            if (!plage.isActif()) {
                continue;
            }
            parJour.computeIfAbsent(plage.jour(), jour -> new ArrayList<>())
                    .add(HEURE.format(plage.getHeureDebut()) + SEPARATEUR + HEURE.format(plage.getHeureFin()));
        }

        List<JourOuvertureVue> semaine = new ArrayList<>(DayOfWeek.values().length);
        for (DayOfWeek jour : DayOfWeek.values()) {
            List<String> horaires = parJour.getOrDefault(jour, List.of());
            semaine.add(new JourOuvertureVue(cleI18n(jour), List.copyOf(horaires), horaires.isEmpty()));
        }
        return semaine;
    }

    /**
     * Cle de message du jour. Ecrite en toutes lettres plutot que derivee de
     * {@code DayOfWeek.name()} : la derivation produirait des cles anglaises
     * ({@code vitrine.jour.monday}) au milieu d un fichier de messages entierement
     * francais, et le jour ou une cle manquerait, l erreur serait illisible.
     */
    private static String cleI18n(DayOfWeek jour) {
        return switch (jour) {
            case MONDAY -> "vitrine.jour.lundi";
            case TUESDAY -> "vitrine.jour.mardi";
            case WEDNESDAY -> "vitrine.jour.mercredi";
            case THURSDAY -> "vitrine.jour.jeudi";
            case FRIDAY -> "vitrine.jour.vendredi";
            case SATURDAY -> "vitrine.jour.samedi";
            case SUNDAY -> "vitrine.jour.dimanche";
        };
    }
}
