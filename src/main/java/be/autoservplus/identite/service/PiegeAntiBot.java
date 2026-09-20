package be.autoservplus.identite.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Deux controles passifs sur les formulaires publics : un champ piege et un delai
 * minimal de soumission.
 *
 * <h2>Ce qu ils valent, et ce qu ils ne valent pas</h2>
 *
 * <p>Ils arretent les robots simples — ceux qui remplissent tous les champs d un
 * formulaire et le postent aussitot — et <b>rien d autre</b>. Un robot ecrit pour ce
 * site les contourne en une ligne. Ils sont la premiere couche d un dispositif dont
 * Turnstile est la seconde, et la limitation de debit la troisieme ; aucune ne suffit
 * seule.</p>
 *
 * <p>Leur interet tient a ce qu ils ne coutent rien au visiteur : ni epreuve, ni
 * tiers, ni donnee supplementaire. Une couche invisible qui elimine le bruit de fond
 * laisse aux couches suivantes moins de travail.</p>
 *
 * <h2>Le champ piege</h2>
 *
 * <p>Un champ de formulaire ordinaire, masque par le CSS et retire du parcours
 * d accessibilite. Un humain ne le voit pas et ne le remplit pas ; un robot qui
 * remplit tout le trahit. Le nom du champ evite deliberement {@code honeypot} et
 * consorts, qu un robot pourrait reconnaitre : {@code societe} passe pour un champ
 * metier plausible sur un site de garage.</p>
 *
 * <p><b>Accessibilite</b> : le champ porte {@code autocomplete="off"},
 * {@code tabindex="-1"} et {@code aria-hidden="true"}. Sans cela un lecteur d ecran
 * l annoncerait et un gestionnaire de mots de passe pourrait le remplir — le piege se
 * refermerait sur les utilisateurs qu il est cense servir.</p>
 *
 * <h2>Le delai minimal</h2>
 *
 * <p>Le formulaire porte l horodatage de son affichage. Une soumission plus rapide que
 * {@link #DELAI_MINIMAL} n a pas ete saisie par un humain. Le seuil est volontairement
 * bas : il s agit d ecarter la soumission instantanee, pas de mesurer une vitesse de
 * frappe — un utilisateur rapide, ou qui colle des valeurs depuis un gestionnaire de
 * mots de passe, ne doit jamais etre refuse.</p>
 *
 * <p>L horodatage n est ni signe ni chiffre : le falsifier est trivial. Le signer
 * donnerait une fausse impression de solidite pour une couche qui n en revendique
 * aucune — c est Turnstile qui porte la resistance a un adversaire determine.</p>
 */
@Service
public class PiegeAntiBot {

    private static final Logger JOURNAL = LoggerFactory.getLogger(PiegeAntiBot.class);

    /** Nom du champ piege, employe par les gabarits et par les tests. */
    public static final String CHAMP_PIEGE = "societe";

    /** Nom du champ portant l horodatage d affichage. */
    public static final String CHAMP_HORODATAGE = "affiche";

    /**
     * Seuil sous lequel une soumission n est pas humaine. Trois secondes : de quoi
     * ecarter la soumission instantanee sans jamais gener quelqu un qui colle ses
     * valeurs depuis un gestionnaire de mots de passe.
     */
    public static final Duration DELAI_MINIMAL = Duration.ofSeconds(3);

    private final Clock horloge;
    private final boolean actif;

    public PiegeAntiBot(Clock horloge,
                        @Value("${autoservplus.securite.antibot.passif-actif:true}") boolean actif) {
        this.horloge = horloge;
        this.actif = actif;
    }

    /** Horodatage a poser dans le formulaire au moment de l afficher. */
    public long horodatageActuel() {
        return horloge.instant().toEpochMilli();
    }

    /**
     * Dit si la soumission a l allure d un envoi automatique.
     *
     * @param piege      valeur du champ piege ; toute valeur non vide condamne
     * @param horodatage valeur du champ d horodatage, telle que recue — illisible ou
     *                   absente, elle est traitee comme suspecte : un formulaire
     *                   legitime la porte toujours
     */
    public boolean soumissionAutomatique(String piege, String horodatage) {
        if (!actif) {
            return false;
        }
        if (piege != null && !piege.isBlank()) {
            JOURNAL.info("Soumission ecartee : champ piege rempli.");
            return true;
        }
        if (tropRapide(horodatage)) {
            JOURNAL.info("Soumission ecartee : formulaire renvoye en moins de {} secondes.",
                    DELAI_MINIMAL.toSeconds());
            return true;
        }
        return false;
    }

    private boolean tropRapide(String horodatage) {
        if (horodatage == null || horodatage.isBlank()) {
            return true;
        }
        long affiche;
        try {
            affiche = Long.parseLong(horodatage.trim());
        } catch (NumberFormatException illisible) {
            return true;
        }
        // Un horodatage dans le futur ne vient pas d un formulaire servi par ce site :
        // il a ete fabrique. Le traiter comme « assez ancien » laisserait passer la
        // falsification la plus evidente.
        Instant maintenant = horloge.instant();
        Instant affichage = Instant.ofEpochMilli(affiche);
        return affichage.isAfter(maintenant)
                || affichage.isAfter(maintenant.minus(DELAI_MINIMAL));
    }
}
