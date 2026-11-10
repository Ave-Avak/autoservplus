package be.autoservplus.reservation.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Formulaire d'une indisponibilité (A24). Sans poste, elle ferme tout l'atelier. Les
 * bornes sont saisies en heure locale du garage, au format de
 * {@code <input type="datetime-local">}, et converties en instants par le service.
 */
public class IndisponibiliteForm {

    /** Format ISO de {@code <input type="datetime-local">}, indépendant de la langue. */
    public static final String FORMAT_SAISIE = "yyyy-MM-dd'T'HH:mm";

    private UUID posteReference;

    @NotNull(message = "{admin.planning.validation.date}")
    @DateTimeFormat(pattern = FORMAT_SAISIE)
    private LocalDateTime debut;

    @NotNull(message = "{admin.planning.validation.date}")
    @DateTimeFormat(pattern = FORMAT_SAISIE)
    private LocalDateTime fin;

    @NotBlank(message = "{admin.planning.validation.motif}")
    @Size(max = 200, message = "{admin.planning.validation.motif}")
    private String motif;

    public UUID getPosteReference() { return posteReference; }
    public void setPosteReference(UUID posteReference) { this.posteReference = posteReference; }
    public LocalDateTime getDebut() { return debut; }
    public void setDebut(LocalDateTime debut) { this.debut = debut; }
    public LocalDateTime getFin() { return fin; }
    public void setFin(LocalDateTime fin) { this.fin = fin; }
    public String getMotif() { return motif; }
    public void setMotif(String motif) { this.motif = motif; }
}
