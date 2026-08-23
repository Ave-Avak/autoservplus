package be.autoservplus.messagerie.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Reponse dans un fil existant (BL-5), cote membre comme cote garage. */
public class FormulaireReponse {

    @NotBlank
    @Size(max = FormulaireFil.LONGUEUR_MAXIMALE_CORPS)
    private String corps;

    public String getCorps() { return corps; }
    public void setCorps(String corps) { this.corps = corps; }
}
