package be.autoservplus.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Identifiants d acces au prestataire de paiement. Aucune valeur n est livree :
 * les trois champs sont vides par defaut, et l application se rabat alors sur le
 * prestataire bouchonne (voir {@code SiAucunPrestataireConfigure}).
 *
 * <p><b>Deux formes d identifiant sont acceptees</b>, et elles ne se comportent pas
 * de la meme facon :</p>
 * <ul>
 *   <li>une <b>cle API</b> {@code test_...} ou {@code live_...} : elle designe a elle
 *       seule un profil de site et un mode. Rien d autre a fournir ;</li>
 *   <li>un <b>jeton d acces organisation</b> {@code access_...} : il authentifie une
 *       organisation, pas un site. Mollie ne peut donc pas deduire a quel profil
 *       rattacher un paiement, ni si l on travaille en test ou en reel — il faut le
 *       lui dire, d ou {@link #profilId} et {@link #modeTest}.</li>
 * </ul>
 *
 * <p><b>Pourquoi le profil est fourni plutot que decouvert.</b> Un jeton
 * d organisation permettrait de lire le profil via {@code GET /v2/profiles/me}, mais
 * cet appel exige la permission {@code profiles.read}. Exiger cette permission pour
 * une donnee que l exploitant lit dans son tableau de bord elargirait le jeton sans
 * necessite : l application n a besoin de savoir ni combien de sites possede
 * l organisation, ni lesquels. Le moindre privilege se defend mieux qu il ne se
 * rattrape — les quatre permissions de paiement et de remboursement suffisent.</p>
 *
 * @param cleApi   cle API ou jeton d acces. JAMAIS journalise, ni en clair ni
 *                 tronque : un identifiant partiel dans un journal reste un
 *                 identifiant partiel de trop.
 * @param profilId identifiant du profil de site ({@code pfl_...}), obligatoire avec
 *                 un jeton d organisation, inutile avec une cle API
 * @param modeTest mode test explicite, requis avec un jeton d organisation. Sans
 *                 objet avec une cle API, dont le prefixe porte deja le mode.
 */
@ConfigurationProperties(prefix = "autoservplus.paiement.mollie")
public record MollieProprietes(
        String cleApi,
        String profilId,
        @DefaultValue("true") boolean modeTest) {

    /** Prefixe des jetons d acces organisation, par opposition aux cles API. */
    private static final String PREFIXE_JETON_ORGANISATION = "access_";

    /** Un identifiant a-t-il ete fourni ? C est ce qui decide de la passerelle active. */
    public boolean estConfigure() {
        return cleApi != null && !cleApi.isBlank();
    }

    /**
     * Le jeton authentifie-t-il une organisation plutot qu un site ? Deduit du
     * prefixe, qui est la seule information disponible avant le premier appel — et
     * une information que Mollie documente comme stable.
     */
    public boolean estJetonOrganisation() {
        return estConfigure() && cleApi.strip().startsWith(PREFIXE_JETON_ORGANISATION);
    }

    /** Identifiant debarrasse des espaces qu une variable d environnement traine souvent. */
    public String jeton() {
        return cleApi == null ? null : cleApi.strip();
    }

    /**
     * Refuse une configuration qui echouerait au premier paiement.
     *
     * <p>Un jeton d organisation sans profil produit un rejet de Mollie a la creation
     * du paiement, c est-a-dire au moment ou un client tente d acheter. Le constater
     * au demarrage vaut mieux : l exploitant le voit tout de suite, et surtout
     * l alternative serait pire que l arret. Se rabattre silencieusement sur le
     * bouchon parce que la configuration est incoherente ferait croire a un garage
     * qu il encaisse alors qu il simule — un defaut qui ne se decouvre qu au moment
     * de compter la caisse.</p>
     *
     * @throws IllegalStateException si le couple identifiant / profil est incoherent
     */
    public void verifierCoherence() {
        if (estJetonOrganisation() && (profilId == null || profilId.isBlank())) {
            throw new IllegalStateException("""
                    Configuration du prestataire de paiement incomplete : un jeton \
                    d acces organisation (access_...) ne designe aucun profil de site, \
                    Mollie exige donc profileId a la creation d un paiement. \
                    Renseignez autoservplus.paiement.mollie.profil-id \
                    (variable MOLLIE_PROFILE_ID), lisible dans le tableau de bord Mollie \
                    sous la forme pfl_... — ou employez une cle API test_/live_, qui \
                    porte le profil elle-meme.""");
        }
    }
}
