package be.autoservplus.reservation.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Formulaire d'un poste d'atelier (A24). L'activation se fait depuis la liste. */
public class PosteForm {

    @NotBlank(message = "{admin.planning.validation.libelle}")
    @Size(max = 80, message = "{admin.planning.validation.libelle}")
    private String libelle;

    @Size(max = 1000, message = "{admin.planning.validation.description}")
    private String description;

    @NotNull(message = "{admin.planning.validation.ordre}")
    @Min(value = 0, message = "{admin.planning.validation.ordre}")
    @Max(value = 999, message = "{admin.planning.validation.ordre}")
    private Integer ordre = 0;

    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getOrdre() { return ordre; }
    public void setOrdre(Integer ordre) { this.ordre = ordre; }
}
