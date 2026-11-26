package be.autoservplus.vitrine.service;

import be.autoservplus.common.temps.FuseauGarage;
import be.autoservplus.reservation.domain.Indisponibilite;
import be.autoservplus.reservation.domain.JoursFeries;
import be.autoservplus.reservation.domain.PlageOuverture;
import be.autoservplus.reservation.repository.IndisponibiliteRepository;
import be.autoservplus.reservation.repository.PlageOuvertureRepository;
import be.autoservplus.vitrine.web.dto.EtatDuJour;
import be.autoservplus.vitrine.web.dto.EtatDuJour.Etat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Ouvert ou fermé, à l'instant de la page, pour le bandeau du site.
 *
 * <p>Mêmes sources que les créneaux réservables : les plages d'ouverture actives, les
 * jours fériés et les fermetures de tout l'atelier (indisponibilité sans poste). Une plage
 * qu'une fermeture recouvre, même en partie, compte pour fermée : annoncer « ouvert
 * jusqu'à 18:00 » un jour de congé ferait venir un client devant une porte close. Une
 * indisponibilité d'un seul poste ne ferme pas le garage.</p>
 *
 * <p>La recherche de la prochaine ouverture s'arrête à quinze jours : au-delà, le bandeau
 * renvoie aux horaires, qui disent la semaine entière.</p>
 */
@Service
@Transactional(readOnly = true)
public class EtatDuJourService {

    static final int HORIZON_JOURS = 15;

    private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("HH:mm");

    private final PlageOuvertureRepository plages;
    private final IndisponibiliteRepository indisponibilites;
    private final FuseauGarage fuseau;
    private final Clock horloge;

    public EtatDuJourService(PlageOuvertureRepository plages, IndisponibiliteRepository indisponibilites,
                             FuseauGarage fuseau, Clock horloge) {
        this.plages = plages;
        this.indisponibilites = indisponibilites;
        this.fuseau = fuseau;
        this.horloge = horloge;
    }

    public EtatDuJour maintenant() {
        ZoneId zone = fuseau.zone();
        Instant instant = horloge.instant();
        LocalDateTime ici = LocalDateTime.ofInstant(instant, zone);
        LocalDate aujourdhui = ici.toLocalDate();
        List<PlageOuverture> actives = plages.findAllByOrderByJourSemaineAscHeureDebutAsc().stream()
                .filter(PlageOuverture::isActif).toList();
        List<Indisponibilite> fermetures = indisponibilites.chevauchant(
                        aujourdhui.atStartOfDay(zone).toInstant(),
                        aujourdhui.plusDays(HORIZON_JOURS + 1L).atStartOfDay(zone).toInstant()).stream()
                .filter(i -> i.getPoste() == null).toList();

        boolean ouvertAujourdhuiDHabitude = actives.stream().anyMatch(p -> p.jour() == aujourdhui.getDayOfWeek());
        boolean fermeAujourdhui = true;
        boolean plageDejaPassee = false;
        for (int decalage = 0; decalage <= HORIZON_JOURS; decalage++) {
            LocalDate jour = aujourdhui.plusDays(decalage);
            for (PlageOuverture plage : actives) {
                if (plage.jour() != jour.getDayOfWeek()
                        || JoursFeries.estFerie(jour) || recouverte(plage, jour, zone, fermetures)) {
                    continue;
                }
                LocalDateTime debut = jour.atTime(plage.getHeureDebut());
                LocalDateTime fin = jour.atTime(plage.getHeureFin());
                if (decalage == 0) {
                    fermeAujourdhui = false;
                    if (!ici.isBefore(fin)) {
                        plageDejaPassee = true;
                        continue;
                    }
                    if (!ici.isBefore(debut)) {
                        return new EtatDuJour(Etat.OUVERT, HEURE.format(fin), null, 0, false);
                    }
                    Etat etat = plageDejaPassee ? Etat.PAUSE : Etat.FERME;
                    return new EtatDuJour(etat, HEURE.format(debut), jour.getDayOfWeek(), 0, false);
                }
                return new EtatDuJour(Etat.FERME, HEURE.format(debut), jour.getDayOfWeek(), decalage,
                        ouvertAujourdhuiDHabitude && fermeAujourdhui);
            }
        }
        return new EtatDuJour(Etat.FERME_LONGTEMPS, null, null, 0, ouvertAujourdhuiDHabitude && fermeAujourdhui);
    }

    private static boolean recouverte(PlageOuverture plage, LocalDate jour, ZoneId zone,
                                      List<Indisponibilite> fermetures) {
        Instant debut = jour.atTime(plage.getHeureDebut()).atZone(zone).toInstant();
        Instant fin = jour.atTime(plage.getHeureFin()).atZone(zone).toInstant();
        return fermetures.stream().anyMatch(f -> f.getDebut().isBefore(fin) && f.getFin().isAfter(debut));
    }
}
