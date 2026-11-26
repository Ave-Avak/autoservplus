package be.autoservplus.reservation.service;

import be.autoservplus.reservation.domain.*;
import be.autoservplus.reservation.repository.*;
import be.autoservplus.reservation.service.dto.CreneauDisponible;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/**
 * Calcule les heures de depart reservables pour une duree donnee.
 *
 * <p>La disponibilite n est pas stockee : elle se deduit des plages d ouverture, des
 * jours fériés légaux, des
 * indisponibilites, des rendez-vous actifs et des parametres de l atelier. Un
 * candidat est retenu si l atelier est ouvert sur tout l intervalle, si le depart
 * respecte le delai minimal et l horizon, et si au moins un poste actif n a ni
 * indisponibilite ni rendez-vous sur l intervalle elargi du tampon. En mode de
 * confirmation manuelle, un jour n'est proposé que si l'échéance de réponse d'une
 * demande faite maintenant laisse au garage le temps de répondre ({@link EcheanceReponse}).</p>
 *
 * <p>Les plages sont exprimees en heure locale du garage. Tout le reste est en
 * instants UTC. La projection passe par le fuseau parametre, ce qui rend les
 * changements d heure corrects sans traitement particulier.</p>
 */
@Service
@Transactional(readOnly = true)
public class DisponibiliteService {

    private static final EnumSet<StatutRdv> STATUTS_OCCUPANTS =
            EnumSet.of(StatutRdv.EN_ATTENTE, StatutRdv.CONFIRME);

    private final ParametreAtelierRepository parametres;
    private final PlageOuvertureRepository plages;
    private final PosteAtelierRepository postes;
    private final IndisponibiliteRepository indisponibilites;
    private final RdvRepository rdvs;
    private final EcheanceReponse echeances;
    private final Clock horloge;

    public DisponibiliteService(ParametreAtelierRepository parametres,
                                PlageOuvertureRepository plages,
                                PosteAtelierRepository postes,
                                IndisponibiliteRepository indisponibilites,
                                RdvRepository rdvs,
                                EcheanceReponse echeances,
                                Clock horloge) {
        this.parametres = parametres;
        this.plages = plages;
        this.postes = postes;
        this.indisponibilites = indisponibilites;
        this.rdvs = rdvs;
        this.echeances = echeances;
        this.horloge = horloge;
    }

    /** Heures de depart reservables un jour donne, pour une prestation de la duree indiquee. */
    public List<CreneauDisponible> creneauxDuJour(LocalDate jour, int dureeMinutes) {
        Duration duree = Rdv.dureeArrondie(dureeMinutes, parametres.courants().pas());
        return creneaux(jour, duree, ReglesDeCreneau.MEMBRE, null);
    }

    /**
     * Heures de départ où déplacer un rendez-vous, sur sa durée exacte. Le rendez-vous
     * ne compte pas parmi les occupants : son ancien créneau est libéré par le
     * déplacement, un nouveau créneau peut donc le recouper.
     */
    public List<CreneauDisponible> creneauxPourDeplacer(Rdv deplace, LocalDate jour, ReglesDeCreneau regles) {
        return creneaux(jour, deplace.duree(), regles, deplace);
    }

    private List<CreneauDisponible> creneaux(LocalDate jour, Duration duree, ReglesDeCreneau regles, Rdv deplace) {
        ParametreAtelier p = parametres.courants();
        ZoneId zone = p.zone();
        Instant maintenant = horloge.instant();

        Instant debutJour = jour.atStartOfDay(zone).toInstant();
        Instant finJour = jour.plusDays(1).atStartOfDay(zone).toInstant();
        if (JoursFeries.estFerie(jour) || !jourDansLaFenetre(regles, p, maintenant, debutJour, finJour)) {
            return List.of();
        }
        if (regles == ReglesDeCreneau.MEMBRE && !p.isConfirmationAutomatique()
                && !echeances.laisseLeTempsDeRepondre(jour, maintenant, p)) {
            return List.of();
        }

        List<PosteAtelier> postesActifs = postes.findByActifTrueOrderByOrdreAscLibelleAsc();
        if (postesActifs.isEmpty()) {
            return List.of();
        }

        // Une requete par source pour toute la journee. Le filtrage par candidat se
        // fait en memoire, sur quelques dizaines de lignes au plus.
        List<Indisponibilite> fermetures = indisponibilites.chevauchant(debutJour, finJour);
        List<Rdv> occupes = sauf(deplace, rdvs.actifsChevauchant(
                debutJour.minus(p.tampon()), finJour.plus(p.tampon()), STATUTS_OCCUPANTS));

        List<CreneauDisponible> resultat = new ArrayList<>();
        for (PlageOuverture plage : plages.findByJourSemaineAndActifTrueOrderByHeureDebut(
                (short) jour.getDayOfWeek().getValue())) {

            Instant ouverture = jour.atTime(plage.getHeureDebut()).atZone(zone).toInstant();
            Instant fermeture = jour.atTime(plage.getHeureFin()).atZone(zone).toInstant();

            for (Instant debut = ouverture; !debut.plus(duree).isAfter(fermeture); debut = debut.plus(p.pas())) {
                Instant fin = debut.plus(duree);
                if (!dansLaFenetre(regles, p, maintenant, debut, fin)) {
                    continue;
                }
                if (atelierFerme(fermetures, debut, fin)) {
                    continue;
                }
                List<PosteAtelier> libres = postesLibres(postesActifs, fermetures, occupes,
                        debut, fin, p.tampon());
                if (!libres.isEmpty()) {
                    resultat.add(new CreneauDisponible(debut, fin, libres.get(0), libres.size()));
                }
            }
        }
        return resultat;
    }

    /**
     * Premier poste libre sur [debut, fin), ou vide. Utilise par la reservation pour
     * attribuer le poste. La contrainte d exclusion en base reste le juge final si deux
     * demandes concurrentes passent ce filtre en meme temps.
     */
    public Optional<PosteAtelier> premierPosteLibre(Instant debut, Instant fin) {
        return premierPosteLibre(debut, fin, null);
    }

    /** Même attribution, le rendez-vous déplacé ne comptant pas parmi les occupants. */
    public Optional<PosteAtelier> premierPosteLibre(Instant debut, Instant fin, Rdv deplace) {
        ParametreAtelier p = parametres.courants();
        List<Indisponibilite> fermetures = indisponibilites.chevauchant(debut, fin);
        if (atelierFerme(fermetures, debut, fin)) {
            return Optional.empty();
        }
        List<Rdv> occupes = sauf(deplace, rdvs.actifsChevauchant(
                debut.minus(p.tampon()), fin.plus(p.tampon()), STATUTS_OCCUPANTS));
        return postesLibres(postes.findByActifTrueOrderByOrdreAscLibelleAsc(),
                fermetures, occupes, debut, fin, p.tampon()).stream().findFirst();
    }

    /** Vrai si l intervalle tombe dans les plages d ouverture et respecte delai et horizon. */
    public boolean estReservable(Instant debut, Instant fin) {
        return estReservable(debut, fin, ReglesDeCreneau.MEMBRE);
    }

    /**
     * Même contrôle selon qui choisit le créneau. Pour le garage, ni délai minimal, ni
     * horizon, ni temps de réponse : le créneau doit seulement être à venir.
     *
     * <p>Le départ doit aussi tomber sur la grille de la plage (son ouverture plus un
     * multiple du pas), comme les candidats que proposent les écrans. Sans ce contrôle,
     * une requête forgée posait un rendez-vous à 09:07 et morcelait la journée du poste.</p>
     */
    public boolean estReservable(Instant debut, Instant fin, ReglesDeCreneau regles) {
        ParametreAtelier p = parametres.courants();
        Instant maintenant = horloge.instant();
        if (!dansLaFenetre(regles, p, maintenant, debut, fin)) {
            return false;
        }
        ZonedDateTime local = debut.atZone(p.zone());
        LocalTime heureDebut = local.toLocalTime();
        LocalTime heureFin = fin.atZone(p.zone()).toLocalTime();
        if (!fin.atZone(p.zone()).toLocalDate().equals(local.toLocalDate())) {
            return false; // un rendez-vous ne chevauche pas minuit
        }
        if (JoursFeries.estFerie(local.toLocalDate())) {
            return false;
        }
        if (regles == ReglesDeCreneau.MEMBRE && !p.isConfirmationAutomatique()
                && !echeances.laisseLeTempsDeRepondre(local.toLocalDate(), maintenant, p)) {
            return false;
        }
        return plages.findByJourSemaineAndActifTrueOrderByHeureDebut((short) local.getDayOfWeek().getValue())
                .stream()
                .anyMatch(pl -> !heureDebut.isBefore(pl.getHeureDebut()) && !heureFin.isAfter(pl.getHeureFin())
                        && surLaGrille(pl.getHeureDebut(), heureDebut, p.pas()));
    }

    // --- regles pures, sans acces aux donnees ---------------------------------------

    /** Le départ est-il l'ouverture de la plage plus un multiple du pas ? */
    static boolean surLaGrille(LocalTime ouverture, LocalTime depart, Duration pas) {
        return Duration.between(ouverture, depart).toSeconds() % pas.toSeconds() == 0;
    }

    static boolean dansLaFenetre(ReglesDeCreneau regles, ParametreAtelier p, Instant maintenant,
                                 Instant debut, Instant fin) {
        return switch (regles) {
            case MEMBRE -> !debut.isBefore(maintenant.plus(p.delaiMinimal()))
                    && !fin.isAfter(maintenant.plus(p.horizon()));
            case GARAGE -> debut.isAfter(maintenant);
        };
    }

    /** Un jour peut-il porter au moins un créneau dans la fenêtre ? */
    private static boolean jourDansLaFenetre(ReglesDeCreneau regles, ParametreAtelier p, Instant maintenant,
                                             Instant debutJour, Instant finJour) {
        return switch (regles) {
            case MEMBRE -> finJour.isAfter(maintenant.plus(p.delaiMinimal()))
                    && debutJour.isBefore(maintenant.plus(p.horizon()));
            case GARAGE -> finJour.isAfter(maintenant);
        };
    }

    static List<Rdv> sauf(Rdv deplace, List<Rdv> occupes) {
        if (deplace == null) {
            return occupes;
        }
        return occupes.stream().filter(r -> !deplace.equals(r)).toList();
    }

    static boolean atelierFerme(List<Indisponibilite> fermetures, Instant debut, Instant fin) {
        return fermetures.stream()
                .anyMatch(i -> i.concerneToutLAtelier() && i.chevauche(debut, fin));
    }

    static List<PosteAtelier> postesLibres(List<PosteAtelier> candidats,
                                           List<Indisponibilite> fermetures,
                                           List<Rdv> occupes,
                                           Instant debut, Instant fin, Duration tampon) {
        Instant debutElargi = debut.minus(tampon);
        Instant finElargie = fin.plus(tampon);
        List<PosteAtelier> libres = new ArrayList<>();
        for (PosteAtelier poste : candidats) {
            boolean bloque = fermetures.stream()
                    .anyMatch(i -> poste.equals(i.getPoste()) && i.chevauche(debut, fin));
            boolean occupe = occupes.stream()
                    .anyMatch(r -> poste.equals(r.getPoste())
                            && r.getDebut().isBefore(finElargie) && r.getFin().isAfter(debutElargi));
            if (!bloque && !occupe) {
                libres.add(poste);
            }
        }
        return libres;
    }
}