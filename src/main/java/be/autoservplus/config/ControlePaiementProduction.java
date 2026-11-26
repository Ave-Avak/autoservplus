package be.autoservplus.config;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Exige un prestataire de paiement réel au démarrage, sous le profil {@code prod} seulement.
 *
 * <p>Même parti que {@link ControleCourrielProduction} : sans ce contrôle, une variable
 * oubliée laissait le paiement simulé actif en production, et un membre obtenait une
 * facture numérotée sans un euro reçu. Le repli simulé et le mode test restent à
 * {@code dev} et {@code demo}.</p>
 */
@Component
@Profile("prod")
class ControlePaiementProduction {

    ControlePaiementProduction(MollieProprietes mollie) {
        mollie.verifierPourProduction();
    }
}
