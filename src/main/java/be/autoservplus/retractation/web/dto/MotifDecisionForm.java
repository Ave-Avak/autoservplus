package be.autoservplus.retractation.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Motif saisi par l administrateur pour refuser une retractation (F30).
 *
 * <p>Jumeau de {@code MotifForm} du module reservation, mais distinct de lui :
 * celui-la porte des messages francais en dur, alors que tout cet ecran passe par
 * l i18n. Le reutiliser aurait fait apparaitre deux phrases francaises dans un
 * formulaire neerlandais — c est la seule raison de la duplication, et elle
 * disparaitra quand les ecrans plus anciens seront migres.</p>
 *
 * <p>Le motif est <b>obligatoire</b> ici, a l inverse de celui du membre : c est le
 * professionnel qui doit se justifier quand il oppose une exception au droit de
 * retractation, pas le consommateur quand il l exerce. L entite le refuse aussi,
 * le formulaire ne fait que rendre le message lisible avant d y arriver.</p>
 */
public class MotifDecisionForm {

    @NotBlank(message = "{admin.retractations.validation.motif}")
    @Size(max = 1000, message = "{admin.retractations.validation.motif}")
    private String motif;

    public String getMotif() { return motif; }
    public void setMotif(String motif) { this.motif = motif; }
}
