package be.autoservplus.identite.web.dto;

import be.autoservplus.identite.service.PolitiqueMotDePasse;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Applique {@link MotDePasseSolide} en deleguant a {@link PolitiqueMotDePasse}, que les
 * services appellent aussi : une seule regle, deux points d application.
 *
 * <p><b>Un seul message pour les refus qui renseigneraient un attaquant.</b> Longueur
 * insuffisante, mot de passe frequent ou apparu dans une fuite : l utilisateur lit la
 * meme phrase. Distinguer les cas apprendrait qu une chaine donnee figure dans une fuite
 * connue. Le depassement de longueur, lui, a son propre message : il n apprend rien, et
 * « trop court ou compromis » serait incomprehensible pour une phrase trop longue.</p>
 *
 * <p>Le champ vide n est pas traite ici : {@code @NotBlank} s en charge, et rendre
 * deux messages pour une seule saisie manquante embrouillerait la page.</p>
 */
public class ValidateurMotDePasseSolide
        implements ConstraintValidator<MotDePasseSolide, String> {

    private final PolitiqueMotDePasse politique;

    public ValidateurMotDePasseSolide(PolitiqueMotDePasse politique) {
        this.politique = politique;
    }

    @Override
    public boolean isValid(String motDePasse, ConstraintValidatorContext contexte) {
        if (motDePasse == null || motDePasse.isBlank()) {
            return true;
        }
        return switch (politique.evaluer(motDePasse)) {
            case CONFORME -> true;
            case REFUSE -> false;
            case TROP_LONG -> {
                contexte.disableDefaultConstraintViolation();
                contexte.buildConstraintViolationWithTemplate("{validation.motDePasse.tropLong}")
                        .addConstraintViolation();
                yield false;
            }
        };
    }
}
