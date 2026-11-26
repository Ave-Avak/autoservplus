package be.autoservplus.catalogue.web;

import be.autoservplus.catalogue.service.dto.ArticleVue;
import be.autoservplus.config.IdentiteGarage;
import be.autoservplus.reservation.service.dto.PlageVue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Données structurées schema.org d'une fiche du catalogue, en JSON-LD : elles disent aux
 * moteurs de recherche la nature de la page, son prix et sa disponibilité. Le prix exposé
 * est le prix TVAC (RM-30).
 *
 * <p>Le bloc est sérialisé ici et non composé par le gabarit. Écrit à la main, il insérait
 * le libellé et la description entre deux guillemets, sans échappement : un guillemet
 * saisi au back-office rendait le JSON invalide, et {@code </script>} dans une description
 * fermait le bloc, la suite du texte entrant alors dans la page comme du balisage.
 * Jackson échappe ce que JSON exige. Les chevrons et l'esperluette sont en plus écrits en
 * {@code \\uXXXX}, forme que JSON admet dans une chaîne : aucune valeur ne peut plus
 * contenir la séquence qui ferme une balise {@code script}.</p>
 *
 * <p>Le prestataire d'une prestation et le vendeur d'un produit sont le garage, lu dans sa
 * configuration comme l'en-tête des factures. Le nom du logiciel y figurait en dur : un
 * moteur de recherche attribuait alors les offres de Bielle Bleue à AutoServ+, qui ne vend
 * rien.</p>
 */
public final class DonneesStructurees {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SCHEMA = "https://schema.org";

    private DonneesStructurees() {
    }

    public static String dePrestation(ArticleVue article, IdentiteGarage garage) {
        Map<String, Object> offre = new LinkedHashMap<>();
        offre.put("@type", "Offer");
        offre.put("price", article.prixTvac().toPlainString());
        offre.put("priceCurrency", "EUR");
        offre.put("availability", SCHEMA + "/InStock");

        Map<String, Object> donnees = new LinkedHashMap<>();
        donnees.put("@context", SCHEMA);
        donnees.put("@type", "Service");
        donnees.put("name", article.libelle());
        poser(donnees, "description", article.description());
        poser(donnees, "serviceType", article.categorie());
        donnees.put("provider", garage(garage));
        donnees.put("offers", offre);
        return ecrire(donnees);
    }

    /**
     * Produit du catalogue. {@code category} suit le chemin de la page, rayon puis
     * catégorie, dans la langue affichée : « Accessoires > Intérieur ».
     */
    public static String dePiece(ArticleVue article, String nomDuRayon, IdentiteGarage garage) {
        Map<String, Object> offre = new LinkedHashMap<>();
        offre.put("@type", "Offer");
        offre.put("price", article.prixTvac().toPlainString());
        offre.put("priceCurrency", "EUR");
        offre.put("availability", SCHEMA + (article.disponible() ? "/InStock" : "/OutOfStock"));
        offre.put("seller", garage(garage));

        Map<String, Object> donnees = new LinkedHashMap<>();
        donnees.put("@context", SCHEMA);
        donnees.put("@type", "Product");
        donnees.put("name", article.libelle());
        poser(donnees, "description", article.description());
        poser(donnees, "sku", article.code());
        poser(donnees, "category", nomDuRayon + " > " + article.categorie());
        if (article.marque() != null && !article.marque().isBlank()) {
            donnees.put("brand", Map.of("@type", "Brand", "name", article.marque()));
        }
        donnees.put("offers", offre);
        return ecrire(donnees);
    }

    /**
     * Le garage lui-même, pour l'accueil et la page de contact : coordonnées et horaires
     * d'ouverture. Les horaires viennent des plages actives, celles dont sont tirés les
     * créneaux réservables. Les jours qui partagent les mêmes heures sont regroupés en une
     * seule spécification, comme schema.org le permet. Sans note moyenne : un
     * {@code aggregateRating} sur sa propre page n'est pas retenu par les moteurs pour
     * une entreprise locale, et il n'est pas demandé.
     *
     * @param adresse adresse absolue de la page d'accueil du site
     */
    public static String duGarage(IdentiteGarage garage, List<PlageVue> plagesActives, String adresse) {
        Map<String, Object> donnees = new LinkedHashMap<>();
        donnees.put("@context", SCHEMA);
        donnees.putAll(garage(garage));
        poser(donnees, "url", adresse);
        List<Map<String, Object>> horaires = horaires(plagesActives);
        if (!horaires.isEmpty()) {
            donnees.put("openingHoursSpecification", horaires);
        }
        return ecrire(donnees);
    }

    /**
     * Le garage en nœud schema.org {@code AutoRepair}, sans contexte, pour l'insérer dans
     * un autre document (le carnet d'entretien du membre, F42).
     *
     * <p>Le pays est écrit en code ISO et non repris de la configuration, qui le porte en
     * toutes lettres pour la facture : la V1 ne sert que des garages belges.</p>
     */
    public static Map<String, Object> garage(IdentiteGarage garage) {
        Map<String, Object> adresse = new LinkedHashMap<>();
        adresse.put("@type", "PostalAddress");
        poser(adresse, "streetAddress", joindre(garage.rue(), garage.numeroRue()));
        poser(adresse, "postalCode", garage.codePostal());
        poser(adresse, "addressLocality", garage.localite());
        adresse.put("addressCountry", "BE");

        Map<String, Object> organisation = new LinkedHashMap<>();
        organisation.put("@type", "AutoRepair");
        organisation.put("name", garage.enseigne());
        organisation.put("address", adresse);
        poser(organisation, "telephone", garage.telephone());
        poser(organisation, "email", garage.courriel());
        return organisation;
    }

    private static List<Map<String, Object>> horaires(List<PlageVue> plages) {
        Map<List<LocalTime>, List<DayOfWeek>> joursParHeures = new LinkedHashMap<>();
        for (PlageVue plage : plages) {
            joursParHeures.computeIfAbsent(List.of(plage.heureDebut(), plage.heureFin()), h -> new ArrayList<>())
                    .add(plage.jour());
        }
        List<Map<String, Object>> specifications = new ArrayList<>();
        joursParHeures.forEach((heures, jours) -> {
            Map<String, Object> specification = new LinkedHashMap<>();
            specification.put("@type", "OpeningHoursSpecification");
            specification.put("dayOfWeek", jours.stream().distinct()
                    .map(jour -> SCHEMA + "/" + nomAnglais(jour)).toList());
            specification.put("opens", heures.get(0).toString());
            specification.put("closes", heures.get(1).toString());
            specifications.add(specification);
        });
        return specifications;
    }

    /** schema.org nomme les jours en anglais, avec une capitale : « Monday ». */
    private static String nomAnglais(DayOfWeek jour) {
        String nom = jour.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(nom.charAt(0)) + nom.substring(1);
    }

    private static String joindre(String rue, String numero) {
        if (rue == null || rue.isBlank()) {
            return null;
        }
        return numero == null || numero.isBlank() ? rue : rue + " " + numero;
    }

    /** Une propriété absente vaut mieux qu'une propriété vide ou écrite « null ». */
    private static void poser(Map<String, Object> donnees, String cle, String valeur) {
        if (valeur != null && !valeur.isBlank()) {
            donnees.put(cle, valeur);
        }
    }

    private static String ecrire(Map<String, Object> donnees) {
        try {
            return JSON.writeValueAsString(donnees)
                    .replace("<", "\\u003c")
                    .replace(">", "\\u003e")
                    .replace("&", "\\u0026");
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Données structurées non sérialisables", e);
        }
    }
}
