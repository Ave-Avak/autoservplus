package be.autoservplus.retractation.web.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

/**
 * Formulaire de demande de retractation du membre (F30).
 *
 * <p>Le motif est <b>facultatif</b> et sans {@code @NotBlank} : le droit de
 * retractation est inconditionnel, le consommateur n a pas a se justifier (CDE,
 * art. VI.47). Le rendre obligatoire dans le formulaire poserait une condition la ou
 * la loi n en pose aucune — ce serait une entrave, pas une validation.</p>
 *
 * <p>{@code confirmation} en revanche est obligatoire : l annulation est irreversible
 * une fois validee par le garage, et un POST forge sans la case est refuse ici, cote
 * serveur, et pas seulement par le navigateur.</p>
 */
public class DemandeRetractationForm {

    @Size(max = 1000, message = "{retractation.motif.trop-long}")
    private String motif;

    @AssertTrue(message = "{retractation.erreur.confirmation}")
    private boolean confirmation;

    public String getMotif() { return motif; }
    public void setMotif(String motif) { this.motif = motif; }

    public boolean isConfirmation() { return confirmation; }
    public void setConfirmation(boolean confirmation) { this.confirmation = confirmation; }
}
