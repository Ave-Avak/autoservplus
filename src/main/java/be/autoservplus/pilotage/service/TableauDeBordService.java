package be.autoservplus.pilotage.service;

import be.autoservplus.catalogue.service.AdminCatalogueService;
import be.autoservplus.pilotage.repository.IndicateursRepository;
import be.autoservplus.pilotage.service.dto.PieceEnAlerte;
import be.autoservplus.pilotage.service.dto.TableauDeBord;
import be.autoservplus.reservation.domain.PlageOuverture;
import be.autoservplus.reservation.repository.ParametreAtelierRepository;
import be.autoservplus.reservation.repository.PlageOuvertureRepository;
import be.autoservplus.reservation.repository.PosteAtelierRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

/**
 * Tableau de bord du gerant (BL-1), en lecture seule.
 *
 * <p><b>Toute la logique d assemblage vit ici</b>, pas dans le controleur : le calcul
 * des bornes du mois, la capacite theorique de l atelier et la composition des
 * indicateurs sont des decisions metier, meme si aucune n ecrit en base.</p>
 *
 * <p>{@code @PreAuthorize} de classe en defense en profondeur : la protection d URL
 * {@code /admin/**} filtre deja le role, le service refuse en second. Les chiffres
 * d affaires d un garage n ont rien a faire devant un membre.</p>
 *
 * <p>Bornes calculees a partir de l horloge injectee et du fuseau de l atelier, jamais
 * de {@code LocalDate.now()} : un tableau de bord teste doit pouvoir se placer dans un
 * mois choisi.</p>
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasRole('ADMINISTRATEUR')")
public class TableauDeBordService {

    /** Longueur des classements. Au-dela, un tableau de bord cesse d etre lisible. */
    private static final int TAILLE_CLASSEMENT = 5;

    private final IndicateursRepository indicateurs;
    private final PlageOuvertureRepository plages;
    private final PosteAtelierRepository postes;
    private final ParametreAtelierRepository parametres;
    private final AdminCatalogueService catalogue;
    private final java.time.Clock horloge;

    public TableauDeBordService(IndicateursRepository indicateurs,
                                PlageOuvertureRepository plages,
                                PosteAtelierRepository postes,
                                ParametreAtelierRepository parametres,
                                AdminCatalogueService catalogue,
                                java.time.Clock horloge) {
        this.indicateurs = indicateurs;
        this.plages = plages;
        this.postes = postes;
        this.parametres = parametres;
        this.catalogue = catalogue;
        this.horloge = horloge;
    }

    /** Instantane du mois en cours. */
    public TableauDeBord duMois(Locale langue) {
        ZoneId zone = parametres.courants().zone();
        YearMonth mois = YearMonth.from(LocalDate.ofInstant(horloge.instant(), zone));
        Instant debut = mois.atDay(1).atStartOfDay(zone).toInstant();
        Instant fin = mois.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();

        int postesActifs = postes.findByActifTrueOrderByOrdreAscLibelleAsc().size();

        return new TableauDeBord(
                libelleMois(mois, langue),
                indicateurs.chiffreAffaireFacture(debut, fin),
                indicateurs.avoirsEmis(debut, fin),
                indicateurs.commandesAEncaisser(),
                indicateurs.rendezVousParStatut(debut, fin),
                indicateurs.minutesReservees(debut, fin),
                capaciteEnMinutes(mois, postesActifs),
                postesActifs,
                indicateurs.topPrestations(debut, fin, TAILLE_CLASSEMENT),
                indicateurs.topPieces(debut, fin, TAILLE_CLASSEMENT),
                // Methode ecrite lors du back-office catalogue et restee sans appelant
                // de production jusqu ici : c est son ecran.
                catalogue.piecesEnAlerteDeStock().stream().map(PieceEnAlerte::de).toList());
    }

    /**
     * Capacite theorique du mois : pour chaque jour, la duree des plages actives de son
     * jour de semaine, multipliee par le nombre de postes.
     *
     * <p><b>Approximation assumee</b> : les indisponibilites ponctuelles (conges,
     * panne d un pont) ne sont pas deduites, faute d etre bornees a un poste dans le
     * modele actuel. Le taux affiche est donc <b>minorant</b> — la capacite reelle est
     * inferieure ou egale a celle-ci, jamais superieure. Un taux qui se tromperait dans
     * l autre sens ferait croire a de la marge inexistante. Les jours feries belges ne
     * sont pas exclus non plus, meme limite que {@code dernierJourReservable}.</p>
     */
    private long capaciteEnMinutes(YearMonth mois, int postesActifs) {
        if (postesActifs == 0) {
            return 0L;
        }
        List<PlageOuverture> toutes = plages.findAllByOrderByJourSemaineAscHeureDebutAsc();
        long minutesParJour = 0L;
        for (int jour = 1; jour <= mois.lengthOfMonth(); jour++) {
            LocalDate date = mois.atDay(jour);
            for (PlageOuverture plage : toutes) {
                if (plage.isActif() && plage.jour() == date.getDayOfWeek()) {
                    minutesParJour += Duration.between(plage.getHeureDebut(), plage.getHeureFin())
                            .toMinutes();
                }
            }
        }
        return minutesParJour * postesActifs;
    }

    private static String libelleMois(YearMonth mois, Locale langue) {
        return mois.getMonth().getDisplayName(TextStyle.FULL, langue) + " "
                + mois.format(DateTimeFormatter.ofPattern("yyyy"));
    }
}
