package be.autoservplus.rgpd.service;

import be.autoservplus.rgpd.service.dto.ExportDonnees;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Serialisation du fichier d export en JSON (F22).
 *
 * <p>Le {@link ObjectMapper} est <b>dedie</b>, construit ici, et non celui que
 * Spring Boot auto-configure : le format d un document remis a une personne au
 * titre de l article 15 ne doit pas dependre d un reglage global qu une autre
 * fonctionnalite pourrait modifier un jour. Quatre choix, tous voulus :
 * <ul>
 *   <li><b>snake_case</b> : le fichier se lit hors de Java, ses noms de champs
 *       suivent la convention JSON et non celle du code ;</li>
 *   <li><b>dates ISO 8601</b> plutot qu horodatages numeriques : un
 *       {@code 2026-08-22T09:15:00Z} se lit sans outil, un epoch non ;</li>
 *   <li><b>indentation</b> : l article 12 exige une forme intelligible — le
 *       destinataire est une personne, pas un programme ;</li>
 *   <li><b>UTF-8 explicite</b> : les libelles portent des accents, et la
 *       plateforme est trilingue.</li>
 * </ul>
 *
 * <p>Seuls des {@code record} d export lui sont soumis : aucune entite JPA ne
 * traverse ce composant.
 */
@Component
public class SerialiseurExportJson {

    private final ObjectMapper mapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .build();

    /**
     * @return le document JSON encode en UTF-8, pret a etre servi en telechargement
     * @throws IllegalStateException si la serialisation echoue — un export
     *         partiellement ecrit ne doit jamais atteindre le membre
     */
    public byte[] enJson(ExportDonnees export) {
        try {
            return mapper.writeValueAsString(export).getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Serialisation de l export RGPD impossible.", e);
        }
    }
}
