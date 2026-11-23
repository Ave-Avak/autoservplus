package be.autoservplus.pilotage.service;

import be.autoservplus.catalogue.service.AdminCatalogueService;
import be.autoservplus.i18n.LanguesSupportees;
import be.autoservplus.pilotage.repository.IndicateursRepository;
import be.autoservplus.pilotage.service.dto.Annulations;
import be.autoservplus.pilotage.service.dto.JourChiffre;
import be.autoservplus.pilotage.service.dto.PeriodeTableau;
import be.autoservplus.pilotage.service.dto.PieceEnAlerte;
import be.autoservplus.pilotage.service.dto.RepartitionStatut;
import be.autoservplus.pilotage.service.dto.TableauDeBord;
import be.autoservplus.reservation.domain.PlageOuverture;
import be.autoservplus.reservation.repository.ParametreAtelierRepository;
import be.autoservplus.reservation.repository.PlageOuvertureRepository;
import be.autoservplus.reservation.repository.PosteAtelierRepository;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tableau de bord du gérant (A25), en lecture seule.
 *
 * <p><b>Toute la logique d'assemblage vit ici</b>, pas dans le contrôleur : le calcul
 * des bornes de la période, la capacité théorique de l'atelier et la composition des
 * indicateurs sont des décisions métier, même si aucune n'écrit en base.</p>
 *
 * <p>{@code @PreAuthorize} de classe en défense en profondeur : la protection d'URL
 * {@code /admin/**} filtre déjà le rôle, le service refuse en second. Les chiffres
 * d'affaires d'un garage n'ont rien à faire devant un membre.</p>
 *
 * <p>Bornes calculées à partir de l'horloge injectée et du fuseau de l'atelier, jamais
 * de {@code LocalDate.now()} : un tableau de bord testé doit pouvoir se placer un jour
 * choisi.</p>
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasRole('ADMINISTRATEUR')")
public class TableauDeBordService {

    /** Longueur des classements. Au-delà, un tableau de bord cesse d'être lisible. */
    private static final int TAILLE_CLASSEMENT = 5;

    /** Longueur de la courbe d'évolution, fixée par le cahier des charges. */
    public static final int JOURS_DE_LA_COURBE = 30;

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

    /**
     * Période demandée par l'écran : un raccourci, un intervalle saisi, ou le mois en
     * cours par défaut. « Aujourd'hui » est le jour de l'atelier, pas celui du serveur.
     */
    public PeriodeTableau periode(String raccourci, LocalDate du, LocalDate au) {
        return PeriodeTableau.de(raccourci, du, au, aujourdhui());
    }

    /** Indicateurs de la période. */
    public TableauDeBord pour(PeriodeTableau periode) {
        ZoneId zone = parametres.courants().zone();
        Instant debut = periode.debut(zone);
        Instant fin = periode.finExclue(zone);

        // Classements et catégories nomment le catalogue dans la langue de l'écran de
        // l'administrateur, le français hors de toute requête.
        String langue = LanguesSupportees.plusProche(LocaleContextHolder.getLocale()).getLanguage();
        int postesActifs = postes.findByActifTrueOrderByOrdreAscLibelleAsc().size();
        List<RepartitionStatut> rendezVous = indicateurs.rendezVousParStatut(debut, fin);

        return new TableauDeBord(
                periode,
                indicateurs.chiffreAffaireFacture(debut, fin),
                indicateurs.avoirsEmis(debut, fin),
                indicateurs.commandesAEncaisser(),
                rendezVous,
                annulations(rendezVous, debut, fin),
                indicateurs.nouveauxClients(debut, fin),
                indicateurs.commandesPayees(debut, fin),
                indicateurs.minutesReservees(debut, fin),
                capaciteEnMinutes(periode, postesActifs),
                postesActifs,
                indicateurs.topPrestations(debut, fin, langue, TAILLE_CLASSEMENT),
                indicateurs.topPieces(debut, fin, langue, TAILLE_CLASSEMENT),
                indicateurs.chiffreAffaireParCategorie(debut, fin, langue),
                chiffreDesTrenteDerniersJours(),
                // Méthode écrite lors du back-office catalogue et restée sans appelant
                // de production jusqu'ici : c'est son écran.
                catalogue.piecesEnAlerteDeStock().stream().map(PieceEnAlerte::de).toList());
    }

    /**
     * Chiffre d'affaires facturé hors TVA de chacun des trente derniers jours, aujourd'hui
     * compris. Un jour sans facture vaut zéro : la courbe doit montrer les creux, pas
     * relier deux jours de vente par-dessus une semaine vide.
     */
    public List<JourChiffre> chiffreDesTrenteDerniersJours() {
        ZoneId zone = parametres.courants().zone();
        LocalDate dernier = aujourdhui();
        LocalDate premier = dernier.minusDays(JOURS_DE_LA_COURBE - 1L);
        Map<LocalDate, BigDecimal> factures = indicateurs.chiffreAffaireParJour(
                premier.atStartOfDay(zone).toInstant(),
                dernier.plusDays(1).atStartOfDay(zone).toInstant(), zone);
        List<JourChiffre> serie = new ArrayList<>(JOURS_DE_LA_COURBE);
        for (LocalDate jour = premier; !jour.isAfter(dernier); jour = jour.plusDays(1)) {
            serie.add(new JourChiffre(jour, factures.getOrDefault(jour, BigDecimal.ZERO)));
        }
        return serie;
    }

    private LocalDate aujourdhui() {
        return LocalDate.ofInstant(horloge.instant(), parametres.courants().zone());
    }

    /**
     * Le total et les statuts viennent de la répartition déjà lue. Seul le partage des
     * annulations entre le membre et le garage demande une requête de plus.
     */
    private Annulations annulations(List<RepartitionStatut> rendezVous, Instant debut, Instant fin) {
        long total = rendezVous.stream().mapToLong(RepartitionStatut::nombre).sum();
        long[] partage = indicateurs.annulationsMembreEtGarage(debut, fin);
        return new Annulations(total, partage[0], partage[1],
                nombre(rendezVous, "ABSENT"), nombre(rendezVous, "REFUSE"));
    }

    private static long nombre(List<RepartitionStatut> rendezVous, String statut) {
        return rendezVous.stream().filter(r -> r.statut().equals(statut))
                .mapToLong(RepartitionStatut::nombre).sum();
    }

    /**
     * Capacité théorique de la période : pour chaque jour, la durée des plages actives de
     * son jour de semaine, multipliée par le nombre de postes.
     *
     * <p><b>Approximation assumée</b> : les indisponibilités ponctuelles (congés,
     * panne d'un pont) ne sont pas déduites, faute d'être bornées à un poste dans le
     * modèle actuel. Le taux affiché est donc <b>minorant</b> : la capacité réelle est
     * inférieure ou égale à celle-ci, jamais supérieure. Un taux qui se tromperait dans
     * l'autre sens ferait croire à de la marge inexistante. Les jours fériés belges ne
     * sont pas exclus non plus.</p>
     */
    private long capaciteEnMinutes(PeriodeTableau periode, int postesActifs) {
        if (postesActifs == 0) {
            return 0L;
        }
        List<PlageOuverture> toutes = plages.findAllByOrderByJourSemaineAscHeureDebutAsc();
        long minutes = 0L;
        for (LocalDate date : periode.jours()) {
            for (PlageOuverture plage : toutes) {
                if (plage.isActif() && plage.jour() == date.getDayOfWeek()) {
                    minutes += Duration.between(plage.getHeureDebut(), plage.getHeureFin())
                            .toMinutes();
                }
            }
        }
        return minutes * postesActifs;
    }
}
