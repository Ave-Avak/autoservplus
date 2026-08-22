package be.autoservplus.vente.service;

import be.autoservplus.vente.domain.StatutPaiement;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Passerelle Mollie : UNIQUE point de contact avec l API du prestataire
 * (strategie securite §11). Active en production seulement — partout ailleurs,
 * {@link PrestatairePaiementFictif} tient le role sans reseau.
 *
 * <p>Contrats non negociables de l implementation reelle :</p>
 * <ul>
 *   <li>timeout strict {@link #DELAI_MAXIMUM} sur tout appel synchrone ;</li>
 *   <li>validation de chaque reponse (montant, devise, statut attendu) avant de
 *       la restituer au metier ;</li>
 *   <li>projection des statuts Mollie vers {@link StatutPaiement} : open vers
 *       INITIE, pending vers EN_COURS, paid vers REUSSI, failed et canceled vers
 *       ECHOUE, expired vers EXPIRE ;</li>
 *   <li>cle API lue de la variable d environnement {@code MOLLIE_API_KEY} —
 *       jamais dans le code ni dans un fichier versionne.</li>
 * </ul>
 */
@Service
@Profile("prod")
public class MollieGateway implements PrestatairePaiement {

    /** Timeout strict des appels synchrones au prestataire (strategie securite §11). */
    public static final Duration DELAI_MAXIMUM = Duration.ofSeconds(5);

    @Override
    public PaiementCree creerPaiement(DemandePaiement demande) {
        // TODO : appel POST /v2/payments via le SDK Mollie, avec DELAI_MAXIMUM,
        // la cle d idempotence de la demande, et validation de la reponse.
        throw new UnsupportedOperationException(
                "À implémenter avec le SDK Mollie et la clé API - hors périmètre assisté");
    }

    @Override
    public StatutPaiement lireStatut(String referencePrestataire) {
        // TODO : appel GET /v2/payments/{id} via le SDK Mollie, avec DELAI_MAXIMUM,
        // et projection du statut Mollie vers StatutPaiement.
        throw new UnsupportedOperationException(
                "À implémenter avec le SDK Mollie et la clé API - hors périmètre assisté");
    }
}
