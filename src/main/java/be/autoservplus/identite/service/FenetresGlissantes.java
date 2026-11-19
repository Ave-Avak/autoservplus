package be.autoservplus.identite.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Horodatages par clé sur une fenêtre glissante, en mémoire et en nombre de clés borné.
 *
 * <p>Fenêtre réellement glissante : les horodatages sortis de la fenêtre sont purgés à
 * chaque passage sur leur clé, et la clé disparaît quand elle se vide. Une fenêtre fixe
 * remise à zéro d'un coup laisserait passer deux fois la limite à cheval sur deux
 * périodes.</p>
 *
 * <p>Le nombre de clés est plafonné : un compteur en mémoire mal borné devient le déni de
 * service qu'il combat. Une fois le plafond atteint, les clés expirées de toute la carte
 * sont purgées avant de conclure à la saturation. Ce que l'appelant fait d'une carte
 * saturée dépend de ce qu'il protège : le limiteur des courriels laisse son autre plafond
 * prendre le relais, la connexion exige la vérification humaine.</p>
 */
final class FenetresGlissantes {

    private final Map<String, Deque<Instant>> horodatages = new ConcurrentHashMap<>();
    private final Duration fenetre;
    private final int clesMax;

    FenetresGlissantes(Duration fenetre, int clesMax) {
        this.fenetre = fenetre;
        this.clesMax = clesMax;
    }

    /**
     * Vrai si la clé est absente et que la carte, purgée de ses clés expirées, est pleine :
     * un horodatage de plus pour cette clé ne serait pas retenu.
     */
    boolean saturee(String cle, Instant maintenant) {
        if (horodatages.size() < clesMax || horodatages.containsKey(cle)) {
            return false;
        }
        horodatages.keySet().forEach(existante -> purger(existante, maintenant));
        return horodatages.size() >= clesMax && !horodatages.containsKey(cle);
    }

    /**
     * Ajoute un horodatage si la clé en porte moins que la limite, en une seule opération
     * atomique sur la clé. Une carte saturée n'accepte aucune clé nouvelle.
     *
     * @return vrai si l'horodatage a été ajouté
     */
    boolean ajouterSousLimite(String cle, int limite, Instant maintenant) {
        if (saturee(cle, maintenant)) {
            return false;
        }
        AtomicBoolean ajoute = new AtomicBoolean();
        horodatages.compute(cle, (ignore, existants) -> {
            Deque<Instant> fenetreCle = sansExpires(existants, maintenant);
            if (fenetreCle.size() < limite) {
                fenetreCle.addLast(maintenant);
                ajoute.set(true);
            }
            return fenetreCle.isEmpty() ? null : fenetreCle;
        });
        return ajoute.get();
    }

    /** Ajoute un horodatage sans limite par clé. Une carte saturée n'accepte aucune clé nouvelle. */
    void ajouter(String cle, Instant maintenant) {
        ajouterSousLimite(cle, Integer.MAX_VALUE, maintenant);
    }

    /** Nombre d'horodatages de la clé encore dans la fenêtre. */
    int compter(String cle, Instant maintenant) {
        AtomicInteger nombre = new AtomicInteger();
        horodatages.computeIfPresent(cle, (ignore, existants) -> {
            Deque<Instant> fenetreCle = sansExpires(existants, maintenant);
            nombre.set(fenetreCle.size());
            return fenetreCle.isEmpty() ? null : fenetreCle;
        });
        return nombre.get();
    }

    void retirer(String cle) {
        horodatages.remove(cle);
    }

    int nombreDeCles() {
        return horodatages.size();
    }

    private void purger(String cle, Instant maintenant) {
        horodatages.computeIfPresent(cle, (ignore, existants) -> {
            Deque<Instant> fenetreCle = sansExpires(existants, maintenant);
            // null retire la clé : une clé vidée qui survivrait ne serait jamais purgée.
            return fenetreCle.isEmpty() ? null : fenetreCle;
        });
    }

    private Deque<Instant> sansExpires(Deque<Instant> existants, Instant maintenant) {
        Deque<Instant> fenetreCle = existants == null ? new ArrayDeque<>() : existants;
        Instant debut = maintenant.minus(fenetre);
        while (!fenetreCle.isEmpty() && fenetreCle.peekFirst().isBefore(debut)) {
            fenetreCle.pollFirst();
        }
        return fenetreCle;
    }
}
