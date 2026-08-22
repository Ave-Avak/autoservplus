package be.autoservplus.vente.service;

import be.autoservplus.vente.domain.StatutPaiement;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Prestataire de paiement bouchonne, actif partout sauf en production : toute la
 * logique metier du module se developpe et se teste contre lui, sans reseau et
 * de maniere deterministe (meme role que {@code CourrielConsole} pour l email).
 *
 * <p>Un paiement cree nait INITIE ; les tests et la demo font evoluer son statut
 * via {@link #programmerStatut} — c est ce que {@link #lireStatut} restituera,
 * comme le ferait la relecture de l API Mollie.</p>
 */
@Service
@Profile("!prod")
public class PrestatairePaiementFictif implements PrestatairePaiement {

    private final Map<String, StatutPaiement> statuts = new ConcurrentHashMap<>();
    private final AtomicLong compteur = new AtomicLong(1);

    @Override
    public PaiementCree creerPaiement(DemandePaiement demande) {
        String reference = "tr_fictif_%04d".formatted(compteur.getAndIncrement());
        statuts.put(reference, StatutPaiement.INITIE);
        // URL factice, locale : aucun service externe dans les profils hors prod.
        return new PaiementCree(reference, "/paiement-fictif/" + reference);
    }

    @Override
    public StatutPaiement lireStatut(String referencePrestataire) {
        StatutPaiement statut = statuts.get(referencePrestataire);
        if (statut == null) {
            throw new IllegalStateException(
                    "Reference inconnue du prestataire fictif : " + referencePrestataire);
        }
        return statut;
    }

    /** Programme le statut que la prochaine relecture restituera (tests, demo). */
    public void programmerStatut(String referencePrestataire, StatutPaiement statut) {
        statuts.put(referencePrestataire, statut);
    }
}
