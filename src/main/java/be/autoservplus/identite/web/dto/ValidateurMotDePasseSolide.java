package be.autoservplus.identite.web.dto;

import be.autoservplus.identite.service.MotsDePasseCourants;
import be.autoservplus.identite.service.VerificateurCompromission;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Applique {@link MotDePasseSolide}.
 *
 * <p><b>Un seul message pour tous les refus.</b> Longueur insuffisante, mot de passe
 * frequent ou apparu dans une fuite : l utilisateur lit la meme phrase. Distinguer les
 * cas apprendrait a un attaquant qu une chaine donnee figure dans une fuite connue —
 * ce formulaire deviendrait un oracle sur les bases de compromission, ce que la
 * verification par k-anonymat cherche justement a eviter.</p>
 *
 * <p>Le champ vide n est pas traite ici : {@code @NotBlank} s en charge, et rendre
 * deux messages pour une seule saisie manquante embrouillerait la page.</p>
 */
public class ValidateurMotDePasseSolide
        implements ConstraintValidator<MotDePasseSolide, String> {

    private final MotsDePasseCourants courants;
    private final VerificateurCompromission compromission;

    public ValidateurMotDePasseSolide(MotsDePasseCourants courants,
                                      VerificateurCompromission compromission) {
        this.courants = courants;
        this.compromission = compromission;
    }

    @Override
    public boolean isValid(String motDePasse, ConstraintValidatorContext contexte) {
        if (motDePasse == null || motDePasse.isBlank()) {
            return true;
        }
        if (motDePasse.length() < MotDePasseSolide.MINIMUM
                || motDePasse.length() > MotDePasseSolide.MAXIMUM) {
            return false;
        }
        // La liste embarquee d abord : elle tranche sans appel reseau, et ce qu elle
        // refuse n a pas besoin d etre soumis a un tiers.
        if (courants.estCourant(motDePasse)) {
            return false;
        }
        return !compromission.estCompromis(motDePasse);
    }
}
