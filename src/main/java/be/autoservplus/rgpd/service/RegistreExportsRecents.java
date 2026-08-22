package be.autoservplus.rgpd.service;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Memoire des derniers exports produits, une entree par membre : elle porte la
 * limite d un export par vingt-quatre heures (F22).
 *
 * <p><b>Pourquoi en memoire et non en base.</b> Un export est une <i>lecture</i> :
 * exercer un droit d acces ne doit rien ecrire dans le dossier de la personne, et
 * ajouter une colonne « date du dernier export » a la table {@code utilisateur}
 * demanderait une migration pour stocker un compteur de service. La table
 * {@code utilisateur} porte deja le seul precedent de limitation du projet
 * ({@code tentatives_echouees}, {@code verrouille_jusqu_a}) — mais celui-la
 * protege le compte lui-meme et survit volontairement au redemarrage, ce qui n est
 * pas le cas ici.
 *
 * <p><b>Limites assumees</b>, a documenter au rapport ecrit :
 * <ul>
 *   <li>la memoire est perdue au redemarrage — un membre pourrait obtenir un
 *       second export apres un deploiement. Le risque est celui d une lecture
 *       supplementaire de son propre dossier, sans consequence sur la
 *       confidentialite ;</li>
 *   <li>elle n est pas partagee entre instances. La V1 est mono-instance ; une
 *       execution repartie demanderait un magasin commun (cache distribue ou
 *       table dediee, donc migration).</li>
 * </ul>
 *
 * <p>L horloge est injectee : la limite est une regle temporelle, elle doit se
 * tester sans attendre vingt-quatre heures.
 *
 * <p>Les adresses sont normalisees en minuscules — {@code Locale.ROOT} et non la
 * locale par defaut, dont la casse turque transformerait un {@code I} en
 * {@code ı} et ferait diverger la clef.
 */
@Component
public class RegistreExportsRecents {

    /** Un export par periode glissante de 24 heures et par membre (F22). */
    public static final Duration DELAI_ENTRE_EXPORTS = Duration.ofHours(24);

    private final Map<String, Instant> derniersExports = new ConcurrentHashMap<>();
    private final Clock horloge;

    public RegistreExportsRecents(Clock horloge) {
        this.horloge = horloge;
    }

    /** Instant du dernier export du membre, s il en existe un encore dans la fenetre. */
    public Optional<Instant> dernierExport(String email) {
        Instant dernier = derniersExports.get(clef(email));
        if (dernier == null || !estDansLaFenetre(dernier)) {
            return Optional.empty();
        }
        return Optional.of(dernier);
    }

    /**
     * Temps restant avant qu un nouvel export soit possible.
     *
     * @return vide si le membre peut exporter maintenant
     */
    public Optional<Duration> attenteRestante(String email) {
        return dernierExport(email)
                .map(dernier -> Duration.between(Instant.now(horloge),
                        dernier.plus(DELAI_ENTRE_EXPORTS)));
    }

    /**
     * Enregistre un export qui vient d aboutir.
     *
     * <p>Purge au passage les entrees sorties de la fenetre : sans elle, la carte
     * grandirait indefiniment avec la population des membres. Le nettoyage a lieu
     * ici plutot que dans une tache planifiee — l ecriture est rare, et une tache
     * de plus pour quelques entrees perimees serait disproportionnee.
     */
    public void enregistrer(String email) {
        derniersExports.entrySet().removeIf(entree -> !estDansLaFenetre(entree.getValue()));
        derniersExports.put(clef(email), Instant.now(horloge));
    }

    private boolean estDansLaFenetre(Instant export) {
        return Instant.now(horloge).isBefore(export.plus(DELAI_ENTRE_EXPORTS));
    }

    private static String clef(String email) {
        return email.toLowerCase(Locale.ROOT);
    }
}
