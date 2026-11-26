package be.autoservplus.reservation.service;

import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.repository.PrestationRepository;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.reservation.domain.ParametreAtelier;
import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.repository.ParametreAtelierRepository;
import be.autoservplus.reservation.service.dto.DisponibilitesPubliques;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Créneaux libres des 30 prochains jours pour l'API publique (F43), avec les règles du
 * membre : délai minimal, horizon et temps de réponse du garage, comme au formulaire de
 * rendez-vous. Un tiers ne voit jamais une heure que le membre ne pourrait pas réserver.
 *
 * <p><b>Mémoire de 60 secondes par durée.</b> Un appel calcule 30 journées, trois requêtes
 * chacune, sur une route anonyme. Le résultat est gardé une minute, la même durée que le
 * cache HTTP annoncé : au-delà, un client n'apprendrait rien de neuf. La clé est la durée
 * arrondie au pas, et non la liste des prestations demandées : deux demandes de même durée
 * ont les mêmes créneaux, et le nombre de clés reste borné par la somme des durées du
 * catalogue divisée par le pas. Écarté : Caffeine, une dépendance pour une seule table de
 * quelques entrées.</p>
 */
@Service
@Transactional(readOnly = true)
public class DisponibilitesPubliquesService {

    static final int JOURS = 30;
    static final Duration FRAICHEUR = Duration.ofSeconds(60);

    private final DisponibiliteService disponibilites;
    private final PrestationRepository prestations;
    private final ParametreAtelierRepository parametres;
    private final Clock horloge;

    private final ConcurrentMap<Duration, Calcul> memoire = new ConcurrentHashMap<>();

    private record Calcul(Instant calculeA, DisponibilitesPubliques resultat) {
        boolean perime(Instant maintenant) {
            return !maintenant.isBefore(calculeA.plus(FRAICHEUR));
        }
    }

    public DisponibilitesPubliquesService(DisponibiliteService disponibilites, PrestationRepository prestations,
                                          ParametreAtelierRepository parametres, Clock horloge) {
        this.disponibilites = disponibilites;
        this.prestations = prestations;
        this.parametres = parametres;
        this.horloge = horloge;
    }

    /**
     * @param references prestations à réserver ensemble, durées additionnées comme au
     *                   formulaire. Vide : un créneau de la durée du pas. Une référence
     *                   inconnue, retirée ou facturée à l'heure répond comme introuvable :
     *                   aucune de ces prestations ne se réserve en ligne.
     */
    public DisponibilitesPubliques prochainsJours(Collection<UUID> references) {
        ParametreAtelier p = parametres.courants();
        int minutes = references.isEmpty()
                ? (int) p.pas().toMinutes()
                : references.stream().distinct().mapToInt(this::dureeReservable).sum();
        Duration duree = Rdv.dureeArrondie(minutes, p.pas());
        Instant maintenant = horloge.instant();

        memoire.values().removeIf(calcul -> calcul.perime(maintenant));
        return memoire.compute(duree, (cle, existant) -> existant != null && !existant.perime(maintenant)
                ? existant
                : new Calcul(maintenant, calculer(duree, p.zone(), maintenant))).resultat();
    }

    private int dureeReservable(UUID reference) {
        return prestations.findByReference(reference)
                .filter(Prestation::isActif)
                .filter(prestation -> !prestation.estALHeure())
                .map(Prestation::getDureeMinutes)
                .orElseThrow(() -> new RessourceIntrouvableException("Prestation", reference));
    }

    private DisponibilitesPubliques calculer(Duration duree, ZoneId zone, Instant maintenant) {
        LocalDate premier = LocalDate.ofInstant(maintenant, zone);
        LocalDate dernier = premier.plusDays(JOURS - 1L);
        List<DisponibilitesPubliques.Creneau> creneaux = new ArrayList<>();
        for (LocalDate jour = premier; !jour.isAfter(dernier); jour = jour.plusDays(1)) {
            disponibilites.creneauxDuJour(jour, (int) duree.toMinutes()).forEach(creneau ->
                    creneaux.add(new DisponibilitesPubliques.Creneau(creneau.debut(), creneau.fin())));
        }
        return new DisponibilitesPubliques(duree, zone, premier, dernier, List.copyOf(creneaux));
    }
}
