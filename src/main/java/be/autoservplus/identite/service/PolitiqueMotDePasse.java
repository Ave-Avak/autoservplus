package be.autoservplus.identite.service;

import be.autoservplus.common.exception.RefusTraduit;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * La regle de mot de passe du projet, en un seul endroit, alignee sur le NIST
 * SP 800-63B-4.
 *
 * <p><b>Un seul porteur pour deux appelants.</b> Le validateur du formulaire
 * ({@code @MotDePasseSolide}) et les services qui posent un mot de passe l appliquent
 * tous deux : le formulaire pour afficher le refus a cote du champ, le service pour
 * qu aucun chemin — appel direct, futur ecran, test — ne pose un mot de passe hors
 * regle. Deux copies avaient diverge : les services gardaient un plancher de douze
 * caracteres quand le formulaire en exigeait quinze.</p>
 *
 * <p><b>Longueur, pas composition.</b> Quinze caracteres au minimum — le mot de passe
 * est l unique facteur des membres — et aucune regle de composition, qui produit des
 * mots de passe previsibles. Puis la liste embarquee des mots de passe frequents, puis
 * le service de compromission.</p>
 *
 * <p><b>Le maximum se compte en octets UTF-8 : 72.</b> BCrypt n en lit pas davantage,
 * et la version de Spring Security employee refuse au-dela par une exception — un mot
 * de passe de cent caracteres passait le formulaire puis echouait au hachage. Une
 * limite en caracteres ne suffit pas : une lettre accentuee occupe deux octets, certains
 * symboles trois ou quatre. Soixante-douze caracteres sans accent restent acceptes, au-
 * dessus des 64 que le NIST demande d admettre ; une phrase tres accentuee l est moins,
 * ecart assume plutot que de tronquer en silence ce que l utilisateur a choisi.</p>
 */
@Component
public class PolitiqueMotDePasse {

    public static final int MINIMUM_CARACTERES = 15;
    public static final int MAXIMUM_OCTETS = 72;

    /** Code de la regle CdC, porte par le refus pour la tracabilite. */
    private static final String REGLE = "RM-02";

    /**
     * {@code REFUSE} reunit trop court, frequent et compromis : un message unique, faute
     * de quoi l ecran dirait a un attaquant qu une chaine figure dans une fuite connue.
     * {@code TROP_LONG} a le sien — la longueur n apprend rien a personne.
     */
    public enum Verdict { CONFORME, REFUSE, TROP_LONG }

    private final MotsDePasseCourants courants;
    private final VerificateurCompromission compromission;

    public PolitiqueMotDePasse(MotsDePasseCourants courants,
                               VerificateurCompromission compromission) {
        this.courants = courants;
        this.compromission = compromission;
    }

    public Verdict evaluer(String motDePasse) {
        if (motDePasse == null || motDePasse.length() < MINIMUM_CARACTERES) {
            return Verdict.REFUSE;
        }
        if (motDePasse.getBytes(StandardCharsets.UTF_8).length > MAXIMUM_OCTETS) {
            return Verdict.TROP_LONG;
        }
        // La liste embarquee d abord : elle tranche sans appel reseau, et ce qu elle
        // refuse n a pas besoin d etre soumis a un tiers.
        if (courants.estCourant(motDePasse) || compromission.estCompromis(motDePasse)) {
            return Verdict.REFUSE;
        }
        return Verdict.CONFORME;
    }

    /**
     * Garde des services : leve le refus traduit si la regle n est pas respectee.
     *
     * @throws RefusTraduit mot de passe trop court, frequent, compromis, ou trop long
     */
    public void exiger(String motDePasse) {
        switch (evaluer(motDePasse)) {
            case CONFORME -> { }
            case TROP_LONG -> throw new RefusTraduit(REGLE, "validation.motDePasse.tropLong",
                    "Le mot de passe depasse %d octets.".formatted(MAXIMUM_OCTETS));
            case REFUSE -> throw new RefusTraduit(REGLE, "validation.motDePasse.politique",
                    "Le mot de passe ne respecte pas la politique : au moins %d caracteres, "
                            .formatted(MINIMUM_CARACTERES)
                            + "ni frequent ni apparu dans une fuite connue.");
        }
    }
}
