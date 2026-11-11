package be.autoservplus.reservation.service;

import be.autoservplus.reservation.domain.Indisponibilite;
import be.autoservplus.reservation.domain.JoursFeries;
import be.autoservplus.reservation.domain.ParametreAtelier;
import be.autoservplus.reservation.domain.PlageOuverture;
import be.autoservplus.reservation.repository.IndisponibiliteRepository;
import be.autoservplus.reservation.repository.PlageOuvertureRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Échéance de réponse d'une demande de rendez-vous, en mode de confirmation manuelle.
 *
 * <p>L'échéance est la plus proche de deux limites : la création augmentée du délai de
 * réponse, et 12:00 du dernier jour d'ouverture de l'atelier qui précède le
 * rendez-vous, heure de l'atelier. La seconde laisse au membre le temps de s'organiser
 * autrement si la demande expire : une échéance la veille au soir d'un rendez-vous du
 * lundi tomberait un dimanche où il ne peut joindre personne. Un jour est ouvert s'il
 * porte au moins une plage active, n'est pas férié, et si les indisponibilités de tout
 * l'atelier ne couvrent pas toutes ses plages.</p>
 *
 * <p>Un créneau n'est proposé que si cette échéance tombe au moins
 * {@link #MARGE_DE_REPONSE} après l'instant de la demande : en deçà, le garage n'aurait
 * pas le temps de répondre et la demande expirerait sans avoir été lue.</p>
 */
@Component
public class EcheanceReponse {

    public static final Duration MARGE_DE_REPONSE = Duration.ofHours(2);
    private static final LocalTime MIDI = LocalTime.NOON;

    private final PlageOuvertureRepository plages;
    private final IndisponibiliteRepository indisponibilites;

    public EcheanceReponse(PlageOuvertureRepository plages, IndisponibiliteRepository indisponibilites) {
        this.plages = plages;
        this.indisponibilites = indisponibilites;
    }

    /** Échéance d'une demande créée à {@code creation} pour un rendez-vous du jour {@code jourRdv}. */
    public Instant pour(Instant creation, LocalDate jourRdv, ParametreAtelier p) {
        Instant parDelai = creation.plus(p.delaiReponse());
        Optional<Instant> midi = midiDuDernierJourOuvert(jourRdv, creation, p.zone());
        if (midi.isEmpty()) {
            // Aucun jour ouvert avant le rendez-vous : le garage ne pourrait pas répondre
            // à temps, la demande serait échue dès sa création.
            return creation;
        }
        return midi.get().isBefore(parDelai) ? midi.get() : parDelai;
    }

    /** Vrai si une demande faite maintenant pour ce jour laisse au garage la marge de réponse. */
    public boolean laisseLeTempsDeRepondre(LocalDate jourRdv, Instant maintenant, ParametreAtelier p) {
        return !pour(maintenant, jourRdv, p).isBefore(maintenant.plus(MARGE_DE_REPONSE));
    }

    /**
     * 12:00 du dernier jour ouvert strictement avant {@code jourRdv}, en remontant au plus
     * jusqu'au jour de {@code creation}. Vide si aucun jour ouvert n'existe entre les deux.
     */
    Optional<Instant> midiDuDernierJourOuvert(LocalDate jourRdv, Instant creation, ZoneId zone) {
        LocalDate premier = LocalDate.ofInstant(creation, zone);
        for (LocalDate jour = jourRdv.minusDays(1); !jour.isBefore(premier); jour = jour.minusDays(1)) {
            if (estOuvert(jour, zone)) {
                return Optional.of(jour.atTime(MIDI).atZone(zone).toInstant());
            }
        }
        return Optional.empty();
    }

    boolean estOuvert(LocalDate jour, ZoneId zone) {
        if (JoursFeries.estFerie(jour)) {
            return false;
        }
        List<PlageOuverture> duJour = plages.findByJourSemaineAndActifTrueOrderByHeureDebut(
                (short) jour.getDayOfWeek().getValue());
        if (duJour.isEmpty()) {
            return false;
        }
        List<Indisponibilite> fermetures = indisponibilites.chevauchant(
                        jour.atStartOfDay(zone).toInstant(), jour.plusDays(1).atStartOfDay(zone).toInstant())
                .stream()
                .filter(Indisponibilite::concerneToutLAtelier)
                .sorted(Comparator.comparing(Indisponibilite::getDebut))
                .toList();
        return duJour.stream().anyMatch(plage -> !couverte(
                jour.atTime(plage.getHeureDebut()).atZone(zone).toInstant(),
                jour.atTime(plage.getHeureFin()).atZone(zone).toInstant(),
                fermetures));
    }

    /** Vrai si les fermetures, triées par début, couvrent [debut, fin) sans trou. */
    static boolean couverte(Instant debut, Instant fin, List<Indisponibilite> fermetures) {
        Instant atteint = debut;
        for (Indisponibilite fermeture : fermetures) {
            if (fermeture.getDebut().isAfter(atteint)) {
                break;
            }
            if (fermeture.getFin().isAfter(atteint)) {
                atteint = fermeture.getFin();
            }
            if (!atteint.isBefore(fin)) {
                return true;
            }
        }
        return !atteint.isBefore(fin);
    }
}
