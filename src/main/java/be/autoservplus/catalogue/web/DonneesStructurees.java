package be.autoservplus.catalogue.web;

import be.autoservplus.catalogue.service.dto.ArticleVue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
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
 */
public final class DonneesStructurees {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SCHEMA = "https://schema.org";

    private DonneesStructurees() {
    }

    public static String dePrestation(ArticleVue article) {
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
        donnees.put("provider", Map.of(
                "@type", "AutoRepair",
                "name", "AutoServ+",
                "address", Map.of("@type", "PostalAddress", "addressCountry", "BE")));
        donnees.put("offers", offre);
        return ecrire(donnees);
    }

    /**
     * Produit du catalogue. {@code category} suit le chemin de la page, rayon puis
     * catégorie, dans la langue affichée : « Accessoires > Intérieur ».
     */
    public static String dePiece(ArticleVue article, String nomDuRayon) {
        Map<String, Object> offre = new LinkedHashMap<>();
        offre.put("@type", "Offer");
        offre.put("price", article.prixTvac().toPlainString());
        offre.put("priceCurrency", "EUR");
        offre.put("availability", SCHEMA + (article.disponible() ? "/InStock" : "/OutOfStock"));
        offre.put("seller", Map.of("@type", "AutoRepair", "name", "AutoServ+"));

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
