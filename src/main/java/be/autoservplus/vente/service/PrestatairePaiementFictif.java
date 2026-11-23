package be.autoservplus.vente.service;

import be.autoservplus.vente.domain.StatutPaiement;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prestataire de paiement bouchonné, actif tant qu'aucun identifiant de
 * prestataire n'est fourni : toute la logique métier du module se développe et se
 * teste contre lui, sans réseau, avec des statuts programmés (même rôle que
 * {@code TransportConsole} pour le courriel).
 *
 * <p>Un paiement créé naît INITIE. Les tests, la démonstration et la page de
 * paiement simulée font évoluer son statut via {@link #programmerStatut} : c'est ce
 * que {@link #lireEtat} restituera, comme le ferait la relecture de l'API
 * Mollie.</p>
 *
 * <p><b>Le repli s'annonce.</b> Il vaut aussi en production, où il évite de rompre
 * un parcours d'achat faute de clé, mais un encaissement simulé qui se tairait
 * serait pire que la rupture qu'il évite. D'où l'avertissement au démarrage, et la
 * bannière que porte la page de paiement simulée.</p>
 */
@Service
@SiAucunPrestataireConfigure
public class PrestatairePaiementFictif implements PrestatairePaiement {

    private static final Logger JOURNAL =
            LoggerFactory.getLogger(PrestatairePaiementFictif.class);

    private static final SecureRandom ALEA = new SecureRandom();

    private final Map<String, StatutPaiement> statuts = new ConcurrentHashMap<>();

    /**
     * Paiements que le bouchon refuse d'annuler, comme le prestataire réel le fait de la
     * plupart des moyens une fois le client parti chez sa banque. Annulable par défaut :
     * une commande abandonnée s'annule alors à l'échéance RM-21, sans attendre une
     * expiration que le bouchon ne simule pas.
     */
    private final Set<String> nonAnnulables = ConcurrentHashMap.newKeySet();

    /** Paiements dont tout appel échoue, comme pendant une panne du prestataire. */
    private final Set<String> enPanne = ConcurrentHashMap.newKeySet();

    /** Remboursements déjà accordés, indexés par clé d'idempotence. */
    private final Map<String, String> remboursements = new ConcurrentHashMap<>();

    /**
     * Référence au hasard, et non un compteur. Un compteur en mémoire repartait de 1 à
     * chaque démarrage, alors que la base garde les références des sessions précédentes :
     * le premier paiement après un redémarrage butait sur uq_paiement_mollie. Seize
     * chiffres hexadécimaux, soit 64 bits aléatoires, rendent une collision négligeable
     * sans rien conserver.
     */
    static String referenceAleatoire(String prefixe) {
        return prefixe + HexFormat.of().toHexDigits(ALEA.nextLong());
    }

    @Override
    public PaiementCree creerPaiement(DemandePaiement demande) {
        String reference = referenceAleatoire("tr_fictif_");
        statuts.put(reference, StatutPaiement.INITIE);
        // URL factice, locale : aucun service externe dans les profils hors prod.
        return new PaiementCree(reference, "/paiement-fictif/" + reference);
    }

    /**
     * Aucun moyen de paiement n'est rapporté, et c'est volontaire : en inventer un
     * ferait afficher « Bancontact » sur une commande que personne n'a payée. L'écran
     * de détail sait dire que le moyen n'a pas été communiqué.
     *
     * <p>Une référence inconnue rend EXPIRE. Les statuts vivent en mémoire : après un
     * redémarrage, une tentative restée ouverte en base n'existe plus ici, comme un
     * paiement ouvert que le prestataire réel aurait laissé expirer. La reprise en
     * crée alors une nouvelle au lieu d'échouer. Le remboursement refuse toujours une
     * référence inconnue.</p>
     */
    @Override
    public EtatPaiement lireEtat(String referencePrestataire) {
        exigerDisponible(referencePrestataire);
        StatutPaiement statut = statuts.get(referencePrestataire);
        if (statut == null) {
            return EtatPaiement.de(StatutPaiement.EXPIRE);
        }
        boolean ouvert = statut == StatutPaiement.INITIE;
        String page = ouvert ? "/paiement-fictif/" + referencePrestataire : null;
        return new EtatPaiement(statut, null, page,
                ouvert && !nonAnnulables.contains(referencePrestataire));
    }

    /**
     * Annule un paiement ouvert et annulable, qui devient ECHOUE comme un paiement
     * « canceled » du prestataire réel. Tout autre paiement est refusé par une panne,
     * comme le fait le prestataire d'un paiement déjà réglé ou non annulable.
     */
    @Override
    public EtatPaiement annuler(String referencePrestataire) {
        exigerDisponible(referencePrestataire);
        if (!lireEtat(referencePrestataire).annulable()) {
            throw new PrestataireIndisponibleException(
                    "Le paiement " + referencePrestataire + " ne peut pas etre annule.");
        }
        statuts.put(referencePrestataire, StatutPaiement.ECHOUE);
        return EtatPaiement.de(StatutPaiement.ECHOUE);
    }

    private void exigerDisponible(String referencePrestataire) {
        if (enPanne.contains(referencePrestataire)) {
            throw new PrestataireIndisponibleException(
                    "Panne simulee du prestataire pour " + referencePrestataire + ".");
        }
    }

    /**
     * Remboursement bouchonné : succès immédiat, comme un prestataire qui accepte le
     * Refund sur-le-champ.
     *
     * <p>Le paiement d'origine doit exister et avoir été encaissé, rembourser une
     * référence inconnue ou un paiement jamais abouti est une erreur de programmation
     * que le bouchon signale au lieu de la masquer, sans quoi les tests de la
     * rétractation passeraient sur une chaîne que le vrai prestataire refuserait.</p>
     *
     * <p>La clé d'idempotence est honorée : deux appels portant la même clé rendent
     * la même référence de remboursement, exactement comme le ferait Mollie. C'est ce
     * qui permet de prouver en test qu'une double validation ne rembourse qu'une
     * fois : même si, en amont, le verrou optimiste et l'index unique de l'avoir
     * doivent l'avoir déjà empêchée.</p>
     */
    @Override
    public RemboursementCree rembourser(DemandeRemboursement demande) {
        exigerDisponible(demande.referencePrestataire());
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
                cle -> referenceAleatoire("re_fictif_"));
        statuts.put(demande.referencePrestataire(), StatutPaiement.REMBOURSE);
        return new RemboursementCree(reference);
    }

    /**
     * Avertissement au démarrage. En {@code WARN} et non en {@code INFO} : un
     * exploitant qui croit encaisser alors qu'il simule ne découvrirait le problème
     * qu'au moment de compter la caisse.
     */
    @PostConstruct
    void annoncerLeRepli() {
        JOURNAL.warn("Aucun identifiant de prestataire de paiement configure "
                + "(autoservplus.paiement.mollie.cle-api / MOLLIE_API_KEY) : les "
                + "paiements sont SIMULES et aucun encaissement reel n a lieu.");
    }

    /** Dit si le prestataire accepte d'annuler ce paiement tant qu'il est ouvert. */
    public void programmerAnnulable(String referencePrestataire, boolean annulable) {
        if (annulable) {
            nonAnnulables.remove(referencePrestataire);
        } else {
            nonAnnulables.add(referencePrestataire);
        }
    }

    /** Fait échouer, ou non, tout appel qui porte sur ce paiement (tests). */
    public void programmerPanne(String referencePrestataire, boolean panne) {
        if (panne) {
            enPanne.add(referencePrestataire);
        } else {
            enPanne.remove(referencePrestataire);
        }
    }

    /** Programme le statut que la prochaine relecture restituera (tests, demo). */
    public void programmerStatut(String referencePrestataire, StatutPaiement statut) {
        statuts.put(referencePrestataire, statut);
    }
}
