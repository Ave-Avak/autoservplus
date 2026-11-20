package be.autoservplus.navigation;

import org.springframework.context.MessageSource;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Fil d'Ariane d'une page, calculé au rendu.
 *
 * <p>L'advice qui le crée s'exécute avant le contrôleur : le modèle ne porte pas encore
 * le rendez-vous ou l'article dont le dernier niveau prend le numéro ou le libellé. Le fil
 * garde donc une référence au modèle de la requête et ne lit cette valeur qu'au moment où
 * le gabarit appelle {@link #niveaux()}, une fois le contrôleur passé.</p>
 */
public final class FilAriane {

    /**
     * Un niveau du fil. {@code adresse} est nulle pour la page courante, qui n'est pas un
     * lien et porte {@code aria-current}.
     */
    public record Niveau(String libelle, String adresse) {
    }

    private final TableFilAriane table;
    private final TableFilAriane.Entree page;
    private final Map<String, String> variables;
    private final Map<String, Object> modele;
    private final MessageSource messages;
    private final Locale langue;

    FilAriane(TableFilAriane table, TableFilAriane.Entree page, Map<String, String> variables,
              Map<String, Object> modele, MessageSource messages, Locale langue) {
        this.table = table;
        this.page = page;
        this.variables = variables == null ? Map.of() : variables;
        this.modele = modele;
        this.messages = messages;
        this.langue = langue;
    }

    public List<Niveau> niveaux() {
        List<TableFilAriane.Entree> chemin = table.chemin(page);
        String racine = chemin.get(0).parent();
        List<Niveau> niveaux = new ArrayList<>();
        niveaux.add(new Niveau(texte(TableFilAriane.RACINES.get(racine), null), racine));
        for (TableFilAriane.Entree entree : chemin) {
            boolean courante = entree == page;
            niveaux.add(new Niveau(libelle(entree, courante), courante ? null : adresse(entree.patron())));
        }
        return niveaux;
    }

    /**
     * Seule la page courante lit le modèle : c'est le seul niveau dont la valeur s'y
     * trouve. Une valeur absente, sur une page d'erreur de formulaire par exemple, laisse
     * le libellé générique plutôt qu'un {@code {0}} affiché tel quel.
     */
    private String libelle(TableFilAriane.Entree entree, boolean courante) {
        if (!entree.dynamique()) {
            return texte(entree.cle(), null);
        }
        Object valeur = courante ? entree.valeur().apply(modele) : null;
        return valeur == null ? texte("fil-ariane.detail", null) : texte(entree.cle(), new Object[]{valeur});
    }

    /** Un parent à variable reprend celles de la page, la seule source dont il dispose. */
    private String adresse(String patron) {
        return UriComponentsBuilder.fromPath(patron).buildAndExpand(variables).toUriString();
    }

    private String texte(String cle, Object[] arguments) {
        return messages.getMessage(cle, arguments, langue);
    }
}
