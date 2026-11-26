package be.autoservplus.api.web;

import be.autoservplus.api.web.dto.GaragePublic;
import be.autoservplus.api.web.dto.Lien;
import be.autoservplus.config.IdentiteGarage;
import be.autoservplus.reservation.service.DisponibilitesPubliquesService;
import be.autoservplus.reservation.service.dto.DisponibilitesPubliques;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Créneaux libres des 30 prochains jours, lisibles par une machine (F43).
 *
 * <p>Réponse en JSON-LD, vocabulaire schema.org : une {@code ItemList} de {@code Schedule},
 * un par heure de départ, avec début, fin, durée et fuseau du garage. Ses liens HAL
 * ({@code _links}) sont ceux du reste de l'API, et un lecteur JSON-LD ignore ce terme
 * qu'aucun contexte ne définit.</p>
 *
 * <p>{@code Cache-Control: public, max-age=60} et un {@code ETag} calculé sur le corps : un
 * client qui revient avec {@code If-None-Match} reçoit un 304 sans corps tant que rien n'a
 * changé. Le calcul lui-même est gardé une minute par le service.</p>
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Disponibilités", description = "Créneaux libres de l'atelier, en lecture seule et sans authentification.")
public class ApiDisponibilitesController {

    static final String JSON_LD = "application/ld+json";
    private static final String SCHEMA = "https://schema.org";

    private final DisponibilitesPubliquesService disponibilites;
    private final IdentiteGarage identite;
    private final ObjectMapper json;
    private final String base;

    public ApiDisponibilitesController(DisponibilitesPubliquesService disponibilites, IdentiteGarage identite,
                                       ObjectMapper json, @Value("${autoservplus.url-publique}") String urlPublique) {
        this.disponibilites = disponibilites;
        this.identite = identite;
        this.json = json;
        this.base = urlPublique.endsWith("/") ? urlPublique.substring(0, urlPublique.length() - 1) : urlPublique;
    }

    @Operation(summary = "Créneaux libres des 30 prochains jours",
            description = "Heures de départ réservables par un membre, selon les règles du "
                    + "formulaire de rendez-vous (délai minimal, horizon, jours fériés). Sans "
                    + "prestation, des créneaux de la durée du pas de l'atelier. Aucun poste "
                    + "n'est exposé.")
    @ApiResponse(responseCode = "200", description = "Liste de Schedule schema.org",
            content = @Content(mediaType = JSON_LD))
    @ApiResponse(responseCode = "304", description = "Inchangé depuis l'ETag envoyé dans If-None-Match")
    @ApiResponse(responseCode = "400", description = "Référence mal formée")
    @ApiResponse(responseCode = "404", description = "Prestation inconnue, retirée ou facturée à l'heure")
    @GetMapping(value = "/disponibilites", produces = {JSON_LD, MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<byte[]> disponibilites(
            @Parameter(description = "Référence d'une prestation à réserver, répétable : les durées s'additionnent.")
            @RequestParam(name = "prestation", required = false) List<UUID> prestations) {

        DisponibilitesPubliques resultat = disponibilites.prochainsJours(prestations == null ? List.of() : prestations);
        byte[] corps = serialiser(document(resultat));
        // HttpEntityMethodProcessor compare cet ETag à If-None-Match et répond 304 de
        // lui-même sur une requête GET.
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(JSON_LD + ";charset=UTF-8"))
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic())
                .eTag(empreinte(corps))
                .body(corps);
    }

    private Map<String, Object> document(DisponibilitesPubliques resultat) {
        DateTimeFormatter iso = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
        String duree = resultat.duree().toString();
        String fuseau = resultat.zone().getId();
        List<Map<String, Object>> elements = resultat.creneaux().stream().map(creneau -> {
            Map<String, Object> horaire = new LinkedHashMap<>();
            horaire.put("@type", "Schedule");
            horaire.put("startDate", creneau.debut().atZone(resultat.zone()).format(iso));
            horaire.put("endDate", creneau.fin().atZone(resultat.zone()).format(iso));
            horaire.put("duration", duree);
            horaire.put("scheduleTimezone", fuseau);
            return horaire;
        }).toList();

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("@context", SCHEMA);
        document.put("@type", "ItemList");
        document.put("_links", liens());
        document.put("startDate", resultat.premierJour().toString());
        document.put("endDate", resultat.dernierJour().toString());
        document.put("numberOfItems", elements.size());
        document.put("itemListElement", elements);
        return document;
    }

    private Map<String, Lien> liens() {
        UriComponents courante = ServletUriComponentsBuilder.fromCurrentRequest().build();
        Map<String, Lien> liens = new LinkedHashMap<>();
        liens.put("self", Lien.versApi(UriComponentsBuilder.fromUriString(base)
                .path(courante.getPath()).query(courante.getQuery()).build().toUriString()));
        liens.put("garage", Lien.versApi(base + "/api/v1/garages/" + GaragePublic.identifiantDe(identite)));
        liens.put("prestations", Lien.versApi(base + "/api/v1/prestations"));
        liens.put("reservation", Lien.versPage(base + "/mes-rendez-vous/nouveau"));
        return liens;
    }

    private byte[] serialiser(Map<String, Object> document) {
        try {
            return json.writeValueAsBytes(document);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Disponibilités non sérialisables", e);
        }
    }

    /** ETag fort : l'empreinte SHA-256 du corps, la même pour deux corps identiques. */
    private static String empreinte(byte[] corps) {
        try {
            return "\"" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(corps)) + "\"";
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 absent de la JVM", e);
        }
    }
}
