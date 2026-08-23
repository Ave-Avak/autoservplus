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

    /** Remboursements deja accordes, indexes par cle d idempotence. */
    private final Map<String, String> remboursements = new ConcurrentHashMap<>();
    private final AtomicLong compteurRemboursements = new AtomicLong(1);

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

    /**
     * Remboursement bouchonne : succes immediat, comme un prestataire qui accepte le
     * Refund sur-le-champ.
     *
     * <p>Le paiement d origine doit exister et avoir ete encaisse — rembourser une
     * reference inconnue ou un paiement jamais abouti est une erreur de programmation
     * que le bouchon signale au lieu de la masquer, sans quoi les tests de la
     * retractation passeraient sur une chaine que le vrai prestataire refuserait.</p>
     *
     * <p>La cle d idempotence est honoree : deux appels portant la meme cle rendent
     * la meme reference de remboursement, exactement comme le ferait Mollie. C est ce
     * qui permet de prouver en test qu une double validation ne rembourse qu une
     * fois — meme si, en amont, le verrou optimiste et l index unique de l avoir
     * doivent l avoir deja empechee.</p>
     */
    @Override
    public RemboursementCree rembourser(DemandeRemboursement demande) {
        StatutPaiement statut = statuts.get(demande.referencePrestataire());
        if (statut == null) {
            throw new IllegalStateException(
                    "Reference inconnue du prestataire fictif : " + demande.referencePrestataire());
        }
        if (statut != StatutPaiement.REUSSI && statut != StatutPaiement.REMBOURSE) {
            throw new IllegalStateException(
                    "Seul un paiement encaisse peut etre rembourse (statut : %s).".formatted(statut));
        }
        String reference = remboursements.computeIfAbsent(demande.cleIdempotence(),
                cle -> "re_fictif_%04d".formatted(compteurRemboursements.getAndIncrement()));
        statuts.put(demande.referencePrestataire(), StatutPaiement.REMBOURSE);
        return new RemboursementCree(reference);
    }

    /** Programme le statut que la prochaine relecture restituera (tests, demo). */
    public void programmerStatut(String referencePrestataire, StatutPaiement statut) {
        statuts.put(referencePrestataire, statut);
    }
}
