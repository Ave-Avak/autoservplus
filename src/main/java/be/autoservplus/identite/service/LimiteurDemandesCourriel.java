package be.autoservplus.identite.service;

import be.autoservplus.common.debit.SeauxParCle;
import be.autoservplus.common.debit.SeauxParCle.Decision;
import be.autoservplus.common.debit.SeauxParCle.Limite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * Plafonne les formulaires publics qui declenchent un envoi de courriel : renvoi du
 * lien de verification (F1) et demande de reinitialisation de mot de passe.
 *
 * <p>Les deux sont <b>anonymes</b> et sans plafond jusqu ici. Une adresse valide
 * pouvait etre inondee par un tiers, et le quota du fournisseur d envoi consomme par
 * n importe qui. {@code TentativesConnexionService} ne couvre que la connexion.</p>
 *
 * <h2>Pourquoi pas le mecanisme de verrouillage de compte</h2>
 *
 * <p>Il ne convenait pas, et pas seulement par commodite. Il n agit que sur un compte
 * existant ({@code findByEmail(...).ifPresent}), donc il ne compterait jamais
 * une adresse inconnue : la difference observable serait exactement l oracle que ces
 * formulaires doivent taire. Et il ecrit {@code verrouille_jusqu_a} sur
 * {@code utilisateur} : un tiers martelant le formulaire verrouillerait le compte de
 * sa victime, ce que le Javadoc de {@code TentativesConnexionService} interdit en
 * toutes lettres. Compter les adresses inconnues en base supposerait enfin de stocker
 * durablement des adresses de personnes non inscrites, sans finalite (art. 5.1.c).</p>
 *
 * <h2>Neutralite</h2>
 *
 * <p><b>Le decompte precede tout acces a la base.</b> C est la seule chose qui rend ce
 * limiteur compatible avec la neutralite anti-oracle : le compteur monte identiquement
 * pour une adresse servie et pour une adresse inconnue, donc comparer le comportement
 * entre deux adresses n apprend rien. Deplacer un jour ce decompte apres la recherche
 * du compte suffirait a transformer l ecran en oracle : un test le verrouille.</p>
 *
 * <h2>Deux cles, deux abus</h2>
 *
 * <p>Par <b>adresse</b> contre le harcelement d un titulaire precis depuis des IP
 * changeantes. Par <b>IP</b> contre un attaquant qui balaie beaucoup d adresses. Les
 * deux compteurs sont consommes a chaque demande, meme si le premier refuse deja :
 * sans cela, marteler une seule adresse ne couterait rien au budget de l IP.</p>
 *
 * <p>Une IPv6 est comptée par /64, comme à la connexion : un abonné en reçoit au moins un,
 * et changer d'adresse dans son propre réseau ne rouvre pas de budget ni ne remplit la carte.</p>
 *
 * <p>L adresse n est pas conservee en clair mais sous forme d empreinte : rien ne
 * justifie de tenir en memoire la liste des adresses soumises par des inconnus. Elle
 * est normalisee avant, pour qu une variante de casse ne rouvre pas un quota.</p>
 *
 * <h2>Saturation de la carte</h2>
 *
 * <p>Un limiteur en memoire mal borne <b>devient</b> le deni de service qu il combat :
 * des adresses toutes differentes feraient enfler la carte sans fin. D ou un plafond
 * dur, et surtout le choix de ce qui arrive une fois atteint.</p>
 *
 * <p>Carte des adresses pleine : elle sature en silence et le plafond par IP continue
 * seul de s'appliquer. Aucun visiteur légitime n'est bloqué, et rien n'est contourné, parce
 * que remplir cette carte exige d'épuiser d'abord des budgets d'IP : avec vingt demandes par
 * IP et par fenêtre, cent mille adresses distinctes demandent cinq mille réseaux sources
 * dans le même quart d'heure. Carte des IP pleine : la demande est refusée. Plus rien ne
 * plafonnerait derrière elle, et la remplir demande cent mille réseaux /64 dans le quart
 * d'heure. Les deux cartes sont séparées, saturer l'une n'évince aucune entrée de l'autre.
 * Le plafond de clés est un garde-fou contre la pathologie, pas la défense principale.</p>
 *
 * <h2>Comptage</h2>
 *
 * <p>Un seau Bucket4j par clé ({@link SeauxParCle}), rempli en continu : cinq demandes par
 * quart d'heure rendent une demande toutes les trois minutes. Le rythme tenu sur la durée
 * est celui de l'ancienne fenêtre glissante, un client patient peut obtenir au plus deux
 * fois la limite sur un quart d'heure pris à cheval.</p>
 *
 * <h2>Limites connues</h2>
 *
 * <p>Compteurs <b>en memoire, par instance, perdus au redemarrage</b>. Un redemarrage
 * rend donc un budget neuf. Il est rare et a la main de l exploitant. Les mettre en
 * base rouvrirait le point RGPD ci-dessus pour un gain theorique. Le deploiement ne
 * lance qu une instance d application ({@code docker-compose.prod.yml}) : une montee
 * en charge horizontale rendrait la limite valable par instance.</p>
 */
@Service
public class LimiteurDemandesCourriel {

    private static final Logger JOURNAL = LoggerFactory.getLogger(LimiteurDemandesCourriel.class);

    /**
     * Garde-fou memoire. Voir « Saturation de la carte » : il n est pas cense etre
     * atteint, le plafond par IP bornant de fait la croissance de cette carte.
     */
    private static final int ENTREES_MAX = 100_000;

    private final SeauxParCle parAdresse;
    private final SeauxParCle parIp;

    @Autowired
    public LimiteurDemandesCourriel(
            Clock horloge,
            @Value("${autoservplus.securite.debit.demandes-max-par-adresse:5}") int maxParAdresse,
            @Value("${autoservplus.securite.debit.demandes-max-par-ip:20}") int maxParIp,
            @Value("${autoservplus.securite.debit.fenetre-minutes:15}") long fenetreMinutes) {
        this(horloge, maxParAdresse, maxParIp, fenetreMinutes, ENTREES_MAX);
    }

    LimiteurDemandesCourriel(Clock horloge, int maxParAdresse, int maxParIp, long fenetreMinutes,
                             int entreesMax) {
        Duration fenetre = Duration.ofMinutes(fenetreMinutes);
        this.parAdresse = new SeauxParCle(List.of(new Limite(maxParAdresse, fenetre)), entreesMax, horloge);
        this.parIp = new SeauxParCle(List.of(new Limite(maxParIp, fenetre)), entreesMax, horloge);
    }

    /**
     * Enregistre une demande et dit si elle peut etre servie.
     *
     * <p>A appeler <b>avant</b> tout acces a la base : voir « Neutralite ». Les deux
     * compteurs sont consommes meme quand le premier refuse.</p>
     *
     * @param adresse adresse soumise, telle que saisie. Peut etre inconnue, vide ou
     *                nulle, elle est comptee dans tous les cas, c est le principe
     * @param ip      adresse de la requete, celle du visiteur et non celle du proxy
     *                ({@code server.forward-headers-strategy: native})
     */
    public boolean autoriser(String adresse, String ip) {
        boolean adresseSousPlafond = consommer(parAdresse, empreinte(adresse), true);
        boolean ipSousPlafond = consommer(parIp, GardeConnexion.cleIp(ip), false);
        if (!adresseSousPlafond || !ipSousPlafond) {
            // Ni l adresse ni son empreinte : une trace d exploitation n a pas besoin
            // de porter une donnee personnelle pour etre utile.
            JOURNAL.info("Demande de courriel refusee, plafond atteint (adresse : {}, ip : {}).",
                    !adresseSousPlafond, !ipSousPlafond);
            return false;
        }
        return true;
    }

    /**
     * Plafonne sur la seule adresse IP, sans consommer de quota d adresse.
     *
     * <p>Pour l inscription, ou l adresse soumise n est pas encore celle d un compte :
     * lui faire consommer le quota d adresse coupleraient l inscription au renvoi de
     * verification et a la reinitialisation, qui visent les comptes existants. Le
     * balayage d adresses depuis une meme source reste borne par le plafond d IP.</p>
     */
    public boolean autoriserParIp(String ip) {
        boolean sousPlafond = consommer(parIp, GardeConnexion.cleIp(ip), false);
        if (!sousPlafond) {
            JOURNAL.info("Inscription refusee, plafond d inscriptions par IP atteint.");
        }
        return sousPlafond;
    }

    /**
     * Plafond sur la seule adresse, pour une adresse dont le compte existe, quand l'appelant
     * a déjà consommé le budget de l'IP (l'inscription). Le consommer une seconde fois
     * l'épuiserait deux fois plus vite pour une adresse connue que pour une inconnue :
     * l'oracle que ce limiteur doit fermer.
     */
    public boolean autoriserAdresse(String adresse) {
        boolean sousPlafond = consommer(parAdresse, empreinte(adresse), true);
        if (!sousPlafond) {
            JOURNAL.info("Avis au titulaire refusé, plafond de l'adresse atteint.");
        }
        return sousPlafond;
    }

    /**
     * Voir « Saturation de la carte » : la carte des adresses pleine laisse l'IP décider,
     * la carte des IP pleine refuse, faute d'un autre plafond derrière elle.
     */
    private static boolean consommer(SeauxParCle compteurs, String cle, boolean passeSiSaturee) {
        Decision decision = compteurs.consommer(cle);
        return decision.acceptee() || (decision.saturee() && passeSiSaturee);
    }

    /**
     * Empreinte SHA-256 de l adresse normalisee. La normalisation precede le hachage,
     * sinon « Alex@Exemple.be » et « alex@exemple.be » ouvriraient deux quotas pour un
     * seul destinataire.
     */
    static String empreinte(String adresse) {
        String normalisee = adresse == null ? "" : adresse.trim().toLowerCase(Locale.ROOT);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(normalisee.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            // SHA-256 est exige de toute implementation Java depuis toujours.
            throw new IllegalStateException("SHA-256 indisponible", impossible);
        }
    }
}
