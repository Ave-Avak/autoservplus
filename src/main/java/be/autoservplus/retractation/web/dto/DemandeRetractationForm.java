package be.autoservplus.retractation.web.dto;

import jakarta.validation.constraints.Size;

/**
 * Déclaration de rétractation du membre (F30, CDE art. VI.61/2).
 *
 * <p>Le motif est <b>facultatif</b> et sans {@code @NotBlank} : le droit de
 * retractation est inconditionnel, le consommateur n a pas a se justifier (CDE,
 * art. VI.47). Le rendre obligatoire dans le formulaire poserait une condition la ou
 * la loi n en pose aucune, ce serait une entrave, pas une validation.</p>
 *
 * <p>Aucune case à cocher : la confirmation est une étape à part entière, avec son
 * bouton « Confirmer la rétractation », comme l'exige VI.61/2, §3.</p>
 */
public class DemandeRetractationForm {

    @Size(max = 1000, message = "{retractation.motif.trop-long}")
    private String motif;

    public String getMotif() { return motif; }
    public void setMotif(String motif) { this.motif = motif; }
}
