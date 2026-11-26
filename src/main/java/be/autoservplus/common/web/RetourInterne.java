package be.autoservplus.common.web;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Page où ramener le membre après un détour : l'ajout d'un véhicule depuis le formulaire
 * de rendez-vous ou le panier, l'adresse complétée depuis le récapitulatif de commande.
 * Sans ce retour, le membre devait retrouver seul le parcours qu'il avait quitté.
 *
 * <p>L'adresse vient d'un paramètre de la requête : elle n'est suivie que si son chemin
 * figure dans la liste fermée ci-dessous. Une adresse externe, relative au protocole
 * ({@code //exemple.org}) ou simplement inconnue est ignorée, et le contrôleur garde sa
 * redirection habituelle : aucune redirection ouverte. La seule requête admise est celle
 * qui garde la prestation choisie, une référence au format UUID.</p>
 */
public final class RetourInterne {

    private static final Set<String> CHEMINS = Set.of("/mes-rendez-vous/nouveau", "/commande", "/panier");

    private static final Pattern REQUETE = Pattern.compile("prestation=[0-9a-fA-F-]{36}");

    private RetourInterne() {
    }

    /** Le retour demandé, s'il est permis. */
    public static Optional<String> permis(String retour) {
        if (retour == null) {
            return Optional.empty();
        }
        int debutRequete = retour.indexOf('?');
        String chemin = debutRequete < 0 ? retour : retour.substring(0, debutRequete);
        if (!CHEMINS.contains(chemin)) {
            return Optional.empty();
        }
        if (debutRequete >= 0 && !REQUETE.matcher(retour.substring(debutRequete + 1)).matches()) {
            return Optional.empty();
        }
        return Optional.of(retour);
    }
}
