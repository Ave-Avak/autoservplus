package be.autoservplus.vente.service;

import be.autoservplus.vente.domain.StatutPaiement;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Prestataire de paiement bouchonne, actif tant qu aucun identifiant de
 * prestataire n est fourni : toute la logique metier du module se developpe et se
 * teste contre lui, sans reseau et de maniere deterministe (meme role que
 * {@code CourrielConsole} pour l email).
 *
 * <p>Un paiement cree nait INITIE ; les tests, la demonstration et la page de
 * paiement simulee font evoluer son statut via {@link #programmerStatut} — c est ce
 * que {@link #lireEtat} restituera, comme le ferait la relecture de l API
 * Mollie.</p>
 *
 * <p><b>Le repli s annonce.</b> Il vaut aussi en production, ou il evite de rompre
 * un parcours d achat faute de cle — mais un encaissement simule qui se tairait
 * serait pire que la rupture qu il evite. D ou l avertissement au demarrage, et la
 * banniere que porte la page de paiement simulee.</p>
 */
@Service
@SiAucunPrestataireConfigure
public class PrestatairePaiementFictif implements PrestatairePaiement {

    private static final Logger JOURNAL =
            LoggerFactory.getLogger(PrestatairePaiementFictif.class);

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

    /**
     * Aucun moyen de paiement n est rapporte, et c est volontaire : en inventer un
     * ferait afficher « Bancontact » sur une commande que personne n a payee. L ecran
     * de detail sait dire que le moyen n a pas ete communique.
     */
    @Override
    public EtatPaiement lireEtat(String referencePrestataire) {
        StatutPaiement statut = statuts.get(referencePrestataire);
        if (statut == null) {
            throw new IllegalStateException(
                    "Reference inconnue du prestataire fictif : " + referencePrestataire);
        }
        return EtatPaiement.de(statut);
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

    /**
     * Avertissement au demarrage. En {@code WARN} et non en {@code INFO} : un
     * exploitant qui croit encaisser alors qu il simule ne decouvrirait le probleme
     * qu au moment de compter la caisse.
     */
    @PostConstruct
    void annoncerLeRepli() {
        JOURNAL.warn("Aucun identifiant de prestataire de paiement configure "
                + "(autoservplus.paiement.mollie.cle-api / MOLLIE_API_KEY) : les "
                + "paiements sont SIMULES et aucun encaissement reel n a lieu.");
    }

    /** Programme le statut que la prochaine relecture restituera (tests, demo). */
    public void programmerStatut(String referencePrestataire, StatutPaiement statut) {
        statuts.put(referencePrestataire, statut);
    }
}
