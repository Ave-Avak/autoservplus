package be.autoservplus.messagerie.web.dto;

import be.autoservplus.messagerie.domain.Conversation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Ouverture d un fil par le membre (BL-5). */
public class FormulaireFil {

    /** Garde-fou de saisie ; la colonne {@code corps} est un {@code text} sans limite. */
    public static final int LONGUEUR_MAXIMALE_CORPS = 4000;

    @NotBlank
    @Size(max = Conversation.LONGUEUR_SUJET)
    private String sujet;

    @NotBlank
    @Size(max = LONGUEUR_MAXIMALE_CORPS)
    private String corps;

    /**
     * Travaux concernes, ou {@code null} pour un fil libre. Le socle ne prevoit pas
     * d autre accroche : une question sur une commande passe par un fil libre.
     */
    private UUID intervention;

    public String getSujet() { return sujet; }
    public void setSujet(String sujet) { this.sujet = sujet; }

    public String getCorps() { return corps; }
    public void setCorps(String corps) { this.corps = corps; }

    public UUID getIntervention() { return intervention; }
    public void setIntervention(UUID intervention) { this.intervention = intervention; }
}
