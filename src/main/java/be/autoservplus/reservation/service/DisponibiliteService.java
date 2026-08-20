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
 * indisponibilites, des rendez-vous actifs et des parametres de l atelier. Un
 * candidat est retenu si l atelier est ouvert sur tout l intervalle, si le depart
 * respecte le delai minimal et l horizon, et si au moins un poste actif n a ni
 * indisponibilite ni rendez-vous sur l intervalle elargi du tampon.</p>
 *
 * <p>Les plages sont exprimees en heure locale du garage ; tout le reste est en
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
    private final Clock horloge;

    public DisponibiliteService(ParametreAtelierRepository parametres,
                                PlageOuvertureRepository plages,
                                PosteAtelierRepository postes,
                                IndisponibiliteRepository indisponibilites,
                                RdvRepository rdvs,
                                Clock horloge) {
        this.parametres = parametres;
        this.plages = plages;
        this.postes = postes;
        this.indisponibilites = indisponibilites;
        this.rdvs = rdvs;
        this.horloge = horloge;
    }

    /** Heures de depart reservables un jour donne, pour une prestation de la duree indiquee. */
    public List<CreneauDisponible> creneauxDuJour(LocalDate jour, int dureeMinutes) {
        ParametreAtelier p = parametres.courants();
        ZoneId zone = p.zone();
        Instant maintenant = horloge.instant();
        Instant plusTot = maintenant.plus(p.delaiMinimal());
        Instant plusTard = maintenant.plus(p.horizon());
        Duration duree = Rdv.dureeArrondie(dureeMinutes, p.pas());

        Instant debutJour = jour.atStartOfDay(zone).toInstant();
        Instant finJour = jour.plusDays(1).atStartOfDay(zone).toInstant();
        if (!finJour.isAfter(plusTot) || !debutJour.isBefore(plusTard)) {
            return List.of();
        }

        List<PosteAtelier> postesActifs = postes.findByActifTrueOrderByOrdreAscLibelleAsc();
        if (postesActifs.isEmpty()) {
            return List.of();
        }

        // Une requete par source pour toute la journee ; le filtrage par candidat se
        // fait en memoire, sur quelques dizaines de lignes au plus.
        List<Indisponibilite> fermetures = indisponibilites.chevauchant(debutJour, finJour);
        List<Rdv> occupes = rdvs.actifsChevauchant(
                debutJour.minus(p.tampon()), finJour.plus(p.tampon()), STATUTS_OCCUPANTS);

        List<CreneauDisponible> resultat = new ArrayList<>();
        for (PlageOuverture plage : plages.findByJourSemaineAndActifTrueOrderByHeureDebut(
                (short) jour.getDayOfWeek().getValue())) {

            Instant ouverture = jour.atTime(plage.getHeureDebut()).atZone(zone).toInstant();
            Instant fermeture = jour.atTime(plage.getHeureFin()).atZone(zone).toInstant();

            for (Instant debut = ouverture; !debut.plus(duree).isAfter(fermeture); debut = debut.plus(p.pas())) {
                Instant fin = debut.plus(duree);
                if (debut.isBefore(plusTot) || fin.isAfter(plusTard)) {
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
        ParametreAtelier p = parametres.courants();
        List<Indisponibilite> fermetures = indisponibilites.chevauchant(debut, fin);
        if (atelierFerme(fermetures, debut, fin)) {
            return Optional.empty();
        }
        List<Rdv> occupes = rdvs.actifsChevauchant(
                debut.minus(p.tampon()), fin.plus(p.tampon()), STATUTS_OCCUPANTS);
        return postesLibres(postes.findByActifTrueOrderByOrdreAscLibelleAsc(),
                fermetures, occupes, debut, fin, p.tampon()).stream().findFirst();
    }

    /** Vrai si l intervalle tombe dans les plages d ouverture et respecte delai et horizon. */
    public boolean estReservable(Instant debut, Instant fin) {
        ParametreAtelier p = parametres.courants();
        Instant maintenant = horloge.instant();
        if (debut.isBefore(maintenant.plus(p.delaiMinimal())) || fin.isAfter(maintenant.plus(p.horizon()))) {
            return false;
        }
        ZonedDateTime local = debut.atZone(p.zone());
        LocalTime heureDebut = local.toLocalTime();
        LocalTime heureFin = fin.atZone(p.zone()).toLocalTime();
        if (!fin.atZone(p.zone()).toLocalDate().equals(local.toLocalDate())) {
            return false; // un rendez-vous ne chevauche pas minuit
        }
        return plages.findByJourSemaineAndActifTrueOrderByHeureDebut((short) local.getDayOfWeek().getValue())
                .stream()
                .anyMatch(pl -> !heureDebut.isBefore(pl.getHeureDebut()) && !heureFin.isAfter(pl.getHeureFin()));
    }

    // --- regles pures, sans acces aux donnees ---------------------------------------

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