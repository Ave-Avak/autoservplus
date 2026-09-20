package be.autoservplus.identite.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Liste embarquee des mots de passe les plus frequents, premiere des deux couches de
 * refus. La seconde est {@link VerificateurCompromission}, qui interroge un service
 * distant.
 *
 * <p><b>Filtree a quinze caracteres et plus</b> : en deca, la longueur minimale refuse
 * deja. Le filtrage ramene le million d entrees de la source a 10 908, soit 188 Ko —
 * assez petit pour tenir en memoire sans qu il faille y reflechir. Source et licence :
 * {@code THIRD-PARTY.md}.</p>
 *
 * <p>Comparaison <b>insensible a la casse</b>. Une liste de mots de passe frequents
 * decrit des habitudes humaines, pas des chaines exactes : accepter
 * {@code Motdepasse123456} parce que la liste porte {@code motdepasse123456} ne
 * protegerait de rien.</p>
 *
 * <p>Chargee une fois au demarrage dans un {@link Set}. Une lecture du fichier a
 * chaque verification couterait un acces disque sur le chemin d inscription, pour une
 * ressource qui ne change jamais entre deux deploiements.</p>
 */
@Service
public class MotsDePasseCourants {

    private static final Logger JOURNAL = LoggerFactory.getLogger(MotsDePasseCourants.class);
    private static final String RESSOURCE = "securite/mots-de-passe-courants.txt";

    private final Set<String> courants;

    public MotsDePasseCourants() {
        this.courants = charger();
        JOURNAL.info("Liste des mots de passe courants chargee : {} entrees.", courants.size());
    }

    /** Vrai si le mot de passe figure dans la liste, quelle qu en soit la casse. */
    public boolean estCourant(String motDePasse) {
        return motDePasse != null
                && courants.contains(motDePasse.toLowerCase(Locale.ROOT));
    }

    public int taille() {
        return courants.size();
    }

    /**
     * Echoue bruyamment si la ressource manque. Un demarrage silencieux avec une liste
     * vide laisserait croire que la couche fonctionne alors qu elle accepterait tout —
     * exactement le genre de panne qu on ne decouvre jamais.
     */
    private static Set<String> charger() {
        Set<String> lues = new HashSet<>(16_384);
        try (BufferedReader lecteur = new BufferedReader(new InputStreamReader(
                new ClassPathResource(RESSOURCE).getInputStream(), StandardCharsets.UTF_8))) {
            String ligne;
            while ((ligne = lecteur.readLine()) != null) {
                String nettoyee = ligne.trim();
                if (!nettoyee.isEmpty()) {
                    lues.add(nettoyee.toLowerCase(Locale.ROOT));
                }
            }
        } catch (IOException echec) {
            throw new UncheckedIOException(
                    "Liste des mots de passe courants introuvable : " + RESSOURCE, echec);
        }
        if (lues.isEmpty()) {
            throw new IllegalStateException(
                    "Liste des mots de passe courants vide : " + RESSOURCE);
        }
        return Set.copyOf(lues);
    }
}
