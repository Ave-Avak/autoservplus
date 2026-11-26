package be.autoservplus.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Identifiants d'accès au prestataire de paiement. Aucune valeur n'est livrée :
 * les trois champs sont vides par défaut, et l'application se rabat alors sur le
 * prestataire bouchonné (voir {@code SiAucunPrestataireConfigure}).
 *
 * <p><b>Deux familles d'identifiant</b>, qui ne se comportent pas de la même façon :</p>
 * <ul>
 *   <li>une <b>clé API</b> {@code test_...} ou {@code live_...} : elle est créée POUR
 *       un profil de site et porte le mode dans son préfixe. Elle désigne donc à elle
 *       seule où imputer le paiement et s'il est de test. Mollie refuse d'ailleurs
 *       qu'on le lui répète. Rien d'autre à fournir.</li>
 *   <li>tout <b>jeton d'accès</b>, jeton d'organisation {@code access_...}, jeton
 *       d'accès avancé créé depuis le tableau de bord, jeton d'application OAuth : il
 *       authentifie une organisation, pas un site. Mollie ne peut alors déduire ni à
 *       quel profil rattacher un paiement, ni si l'on travaille en test ou en réel :
 *       il faut le lui dire, d'où {@link #profilId} et {@link #modeTest}.</li>
 * </ul>
 *
 * <p><b>La reconnaissance se fait par EXCLUSION, et c'est le point à défendre.</b>
 * Le premier jet énumérait les préfixes acceptés ({@code access_}), ce qui traitait
 * en clé API tout ce qui n'y figurait pas, donc sans {@code profileId} ni
 * {@code testmode}. Mollie a depuis unifié ses pages de jetons et propose des
 * « jetons d'accès avancés » dont la documentation publique ne donne aucun préfixe :
 * une liste ne peut pas être tenue à jour contre une valeur qui n'est pas publiée.
 * La documentation est en revanche nette sur le comportement : la clé API est la
 * SEULE forme qui porte profil et mode. C'est donc cette forme-là qui est reconnue,
 * à son préfixe documenté et stable, et tout le reste qui exige le contexte.</p>
 *
 * <p>L'erreur résiduelle change ainsi de camp, ce qui est tout l'intérêt : une forme
 * future scopée à un profil serait refusée au démarrage par
 * {@link #verifierCoherence()}, panne bruyante que l'exploitant corrige en
 * renseignant une variable. Dans l'autre sens, elle aurait été prise pour une clé API
 * et aurait encaissé dans le mauvais mode sans que rien ne le signale.</p>
 *
 * <p><b>Pourquoi le profil est fourni plutôt que découvert.</b> Un jeton
 * d'organisation permettrait de lire le profil via {@code GET /v2/profiles/me}, mais
 * cet appel exige la permission {@code profiles.read}. Exiger cette permission pour
 * une donnée que l'exploitant lit dans son tableau de bord élargirait le jeton sans
 * nécessité : l'application n'a besoin de savoir ni combien de sites possède
 * l'organisation, ni lesquels. Le moindre privilège se défend mieux qu'il ne se
 * rattrape : les quatre permissions de paiement et de remboursement suffisent.</p>
 *
 * @param cleApi   clé API ou jeton d'accès. JAMAIS journalisé, ni en clair ni
 *                 tronqué : un identifiant partiel dans un journal reste un
 *                 identifiant partiel de trop.
 * @param profilId identifiant du profil de site ({@code pfl_...}), obligatoire avec
 *                 un jeton d'organisation, inutile avec une clé API
 * @param modeTest mode test explicite, requis avec un jeton d'organisation. Sans
 *                 objet avec une clé API, dont le préfixe porte déjà le mode.
 */
@ConfigurationProperties(prefix = "autoservplus.paiement.mollie")
public record MollieProprietes(
        String cleApi,
        String profilId,
        @DefaultValue("true") boolean modeTest) {

    /**
     * Préfixes des clés API, seule forme qui porte à elle seule un profil et un mode.
     * Les deux sont documentés par Mollie et stables depuis l'origine de l'API : à la
     * différence des préfixes de jetons, dont certains ne sont pas publiés.
     */
    private static final Set<String> PREFIXES_CLE_API = Set.of("test_", "live_");

    /** Un identifiant a-t-il été fourni ? C'est ce qui décide de la passerelle active. */
    public boolean estConfigure() {
        return cleApi != null && !cleApi.isBlank();
    }

    /**
     * Faut-il préciser à Mollie le profil et le mode, parce que l'identifiant ne les
     * porte pas ? Vrai pour toute forme de jeton d'accès, faux pour une clé API.
     *
     * <p>Nommé par ce qu'il commande et non par ce qu'il croit reconnaître : la
     * méthode ne sait plus dire de quelle sorte de jeton il s'agit (la documentation
     * ne le permet plus) elle sait seulement que ce n'est pas une clé API, et c'est
     * exactement ce dont les appelants ont besoin.</p>
     */
    public boolean exigeContexteOrganisation() {
        if (!estConfigure()) {
            return false;
        }
        String jeton = cleApi.strip();
        return PREFIXES_CLE_API.stream().noneMatch(jeton::startsWith);
    }

    /** Identifiant débarrassé des espaces qu'une variable d'environnement traîne souvent. */
    public String jeton() {
        return cleApi == null ? null : cleApi.strip();
    }

    /**
     * Refuse une configuration qui échouerait au premier paiement.
     *
     * <p>Un jeton d'accès sans profil produit un rejet de Mollie à la création du
     * paiement, c'est-à-dire au moment où un client tente d'acheter. Le constater au
     * démarrage vaut mieux : l'exploitant le voit tout de suite, et surtout
     * l'alternative serait pire que l'arrêt. Se rabattre silencieusement sur le
     * bouchon parce que la configuration est incohérente ferait croire à un garage
     * qu'il encaisse alors qu'il simule : un défaut qui ne se découvre qu'au moment
     * de compter la caisse.</p>
     *
     * @throws IllegalStateException si le couple identifiant / profil est incohérent
     */
    public void verifierCoherence() {
        if (exigeContexteOrganisation() && (profilId == null || profilId.isBlank())) {
            throw new IllegalStateException("""
                    Configuration du prestataire de paiement incomplete : l identifiant \
                    fourni n est pas une cle API (test_/live_), c est donc un jeton \
                    d acces (jeton d organisation access_..., ou jeton d acces avance \
                    cree depuis le tableau de bord). Un jeton de ce type ne designe aucun \
                    profil de site et Mollie exige profileId a la creation d un paiement. \
                    Renseignez autoservplus.paiement.mollie.profil-id \
                    (variable MOLLIE_PROFILE_ID), lisible dans le tableau de bord Mollie \
                    sous la forme pfl_..., ou employez une cle API test_/live_, qui \
                    porte le profil elle-meme.""");
        }
    }

    /**
     * Refuse sous {@code prod} tout ce qui laisserait un membre obtenir une facture sans
     * encaissement réel (RM-20) : aucun identifiant, et le repli simulé prendrait la main,
     * une clé de test, ou un jeton d'accès en mode test. Dans les deux derniers cas, la page
     * de test de Mollie laisse le payeur choisir lui-même l'issue du paiement.
     *
     * @throws IllegalStateException avec toutes les fautes relevées
     */
    public void verifierPourProduction() {
        List<String> fautes = new ArrayList<>();
        if (!estConfigure()) {
            fautes.add("MOLLIE_API_KEY absente ou vide : le paiement simule prendrait le relais");
        } else if (jeton().startsWith("test_")) {
            fautes.add("MOLLIE_API_KEY est une cle de test (test_...)");
        } else if (exigeContexteOrganisation() && modeTest) {
            fautes.add("MOLLIE_MODE_TEST doit valoir false avec un jeton d acces");
        }
        if (!fautes.isEmpty()) {
            throw new IllegalStateException("Paiement refuse sous le profil prod : "
                    + String.join(", ", fautes) + ".");
        }
    }
}
