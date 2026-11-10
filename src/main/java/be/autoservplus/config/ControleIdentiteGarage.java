package be.autoservplus.config;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Contrôle l'identité légale du garage au démarrage, sous le profil {@code prod}
 * seulement.
 *
 * <p>Sous {@code dev}, {@code demo} et les tests, l'identité est celle d'un garage de
 * démonstration, dont le numéro d'entreprise est faux exprès : le contrôle la
 * refuserait. Le placer sur le profil plutôt que sur la présence des variables est ce
 * qui empêche une production d'y échapper en les omettant toutes.</p>
 *
 * <p>{@code prod} et {@code demo} actifs ensemble font refuser le démarrage, et c'est
 * voulu. {@code demo} apporte l'identité de démonstration, que le contrôle de
 * {@code prod} refuse : une mise en service réelle sous ce profil publierait des mots de
 * passe et un faux numéro de TVA. Exempter ce cas ferait de l'ajout de {@code demo} un
 * moyen d'échapper au contrôle.</p>
 */
@Component
@Profile("prod")
class ControleIdentiteGarage {

    ControleIdentiteGarage(IdentiteGarage identite) {
        identite.verifierPourProduction();
    }
}
