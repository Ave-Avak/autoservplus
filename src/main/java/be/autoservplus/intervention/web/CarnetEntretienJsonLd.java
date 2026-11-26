package be.autoservplus.intervention.web;

import be.autoservplus.catalogue.web.DonneesStructurees;
import be.autoservplus.config.IdentiteGarage;
import be.autoservplus.intervention.domain.UniteLigne;
import be.autoservplus.intervention.service.dto.CarnetEntretien;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Carnet d'entretien en JSON-LD, vocabulaire schema.org seul (F42).
 *
 * <p>schema.org n'a ni type {@code MaintenanceRecord} ni propriété qui relie un véhicule à
 * son entretien. Le carnet compose donc des types existants : un {@code Vehicle}, et pour
 * chaque intervention une {@code Action} dont l'objet est le véhicule et l'agent le garage
 * ({@code AutoRepair}). Les travaux sont le résultat de l'action, un {@code Order} dont les
 * lignes désignent un {@code Service} ou un {@code Product}, et les totaux hors TVA et TVA
 * comprise sont deux {@code PriceSpecification} de la facture liée. Écarté : un terme
 * {@code MaintenanceRecord} inventé sous un espace de noms du projet, qu'aucun autre système
 * ne comprendrait, alors que l'export sert justement à quitter la plateforme.</p>
 *
 * <p>Les nœuds sont identifiés par l'URN de leur référence ({@code urn:uuid:...}) : stable,
 * unique, et sans adresse du site qui exigerait une connexion pour être suivie.</p>
 */
@Component
public class CarnetEntretienJsonLd {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SCHEMA = "https://schema.org";

    private final IdentiteGarage garage;
    private final MessageSource messages;
    private final String adresseDuSite;

    public CarnetEntretienJsonLd(IdentiteGarage garage, MessageSource messages,
                                 @Value("${autoservplus.url-publique}") String urlPublique) {
        this.garage = garage;
        this.messages = messages;
        this.adresseDuSite = urlPublique.endsWith("/") ? urlPublique : urlPublique + "/";
    }

    /** @param langue langue des seuls textes ajoutés par le carnet, les libellés restant ceux de l'intervention */
    public byte[] rediger(CarnetEntretien carnet, Locale langue) {
        String mentionPieceIncluse = messages.getMessage("carnet.piece-incluse", null, langue);
        String idVehicule = urn(carnet.vehiculeReference());

        Map<String, Object> organisation = new LinkedHashMap<>(DonneesStructurees.garage(garage));
        organisation.put("@id", adresseDuSite);
        organisation.put("url", adresseDuSite);
        poser(organisation, "vatID", garage.numeroTva());

        List<Object> graphe = new ArrayList<>();
        graphe.add(vehicule(carnet, idVehicule));
        graphe.add(organisation);
        for (CarnetEntretien.Entree entree : carnet.interventions()) {
            graphe.add(intervention(entree, idVehicule, mentionPieceIncluse));
        }

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("@context", SCHEMA);
        document.put("@graph", graphe);
        try {
            return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(document).getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Carnet d'entretien non sérialisable", e);
        }
    }

    /** Nom du fichier téléchargé, sans caractère qu'un système de fichiers refuserait. */
    public static String nomFichier(CarnetEntretien carnet) {
        String plaque = carnet.plaque() == null ? "" : carnet.plaque().replaceAll("[^A-Za-z0-9-]", "");
        return plaque.isEmpty() ? "carnet-entretien.jsonld" : "carnet-entretien-" + plaque + ".jsonld";
    }

    private static Map<String, Object> vehicule(CarnetEntretien carnet, String id) {
        Map<String, Object> vehicule = new LinkedHashMap<>();
        vehicule.put("@type", "Vehicle");
        vehicule.put("@id", id);
        vehicule.put("name", carnet.marque() + " " + carnet.modele());
        vehicule.put("brand", Map.of("@type", "Brand", "name", carnet.marque()));
        vehicule.put("model", carnet.modele());
        if (carnet.annee() != null) {
            vehicule.put("vehicleModelDate", carnet.annee().toString());
        }
        poser(vehicule, "vehicleIdentificationNumber", carnet.numeroChassis());
        poser(vehicule, "identifier", carnet.plaque());
        if (carnet.kilometrage() != null) {
            vehicule.put("mileageFromOdometer", Map.of(
                    "@type", "QuantitativeValue", "value", carnet.kilometrage(), "unitCode", "KMT"));
        }
        return vehicule;
    }

    private Map<String, Object> intervention(CarnetEntretien.Entree entree, String idVehicule,
                                             String mentionPieceIncluse) {
        Map<String, Object> commande = new LinkedHashMap<>();
        commande.put("@type", "Order");
        commande.put("orderNumber", entree.numero());
        commande.put("orderDate", entree.debut().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        commande.put("seller", Map.of("@id", adresseDuSite));
        commande.put("orderStatus", SCHEMA + (entree.fin() != null ? "/OrderDelivered" : "/OrderProcessing"));
        commande.put("orderedItem", entree.lignes().stream().map(l -> ligne(l, mentionPieceIncluse)).toList());
        commande.put("partOfInvoice", Map.of(
                "@type", "Invoice",
                "totalPaymentDue", List.of(montant(entree.totalHtva(), false), montant(entree.totalTvac(), true))));

        Map<String, Object> action = new LinkedHashMap<>();
        action.put("@type", "Action");
        action.put("@id", urn(entree.reference()));
        action.put("name", entree.numero());
        action.put("actionStatus", SCHEMA + statut(entree));
        action.put("object", Map.of("@id", idVehicule));
        action.put("agent", Map.of("@id", adresseDuSite));
        action.put("startTime", entree.debut().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        if (entree.fin() != null) {
            action.put("endTime", entree.fin().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        }
        action.put("result", commande);
        return action;
    }

    private static String statut(CarnetEntretien.Entree entree) {
        return switch (entree.statut()) {
            case TERMINEE -> "/CompletedActionStatus";
            case PLANIFIEE -> "/PotentialActionStatus";
            default -> "/ActiveActionStatus";
        };
    }

    private static Map<String, Object> ligne(CarnetEntretien.Ligne ligne, String mentionPieceIncluse) {
        Map<String, Object> article = new LinkedHashMap<>();
        article.put("@type", ligne.nature() == CarnetEntretien.Nature.PRESTATION ? "Service" : "Product");
        article.put("name", ligne.libelle());
        poser(article, "mpn", ligne.referenceFabricant());
        if (ligne.nature() == CarnetEntretien.Nature.PIECE_INCLUSE) {
            article.put("description", mentionPieceIncluse);
        }

        Map<String, Object> quantite = new LinkedHashMap<>();
        quantite.put("@type", "QuantitativeValue");
        quantite.put("value", ligne.quantite());
        // Codes d'unité UN/CEFACT, ceux qu'attend schema.org : HUR pour une heure, C62
        // pour une unité (pièce ou forfait).
        quantite.put("unitCode", ligne.unite() == UniteLigne.HEURE ? "HUR" : "C62");

        Map<String, Object> element = new LinkedHashMap<>();
        element.put("@type", "OrderItem");
        element.put("orderQuantity", quantite);
        element.put("orderedItem", article);
        return element;
    }

    private static Map<String, Object> montant(BigDecimal valeur, boolean tvaComprise) {
        Map<String, Object> prix = new LinkedHashMap<>();
        prix.put("@type", "PriceSpecification");
        prix.put("price", valeur);
        prix.put("priceCurrency", "EUR");
        prix.put("valueAddedTaxIncluded", tvaComprise);
        return prix;
    }

    private static String urn(UUID reference) {
        return "urn:uuid:" + reference;
    }

    private static void poser(Map<String, Object> noeud, String cle, String valeur) {
        if (valeur != null && !valeur.isBlank()) {
            noeud.put(cle, valeur);
        }
    }
}
