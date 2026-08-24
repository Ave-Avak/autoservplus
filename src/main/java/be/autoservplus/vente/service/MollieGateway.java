package be.autoservplus.vente.service;

import be.autoservplus.config.MollieProprietes;
import be.autoservplus.vente.domain.StatutPaiement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Passerelle Mollie : UNIQUE point de contact avec l API du prestataire
 * (strategie securite §11). Active des qu un identifiant est configure — partout
 * ailleurs, {@link PrestatairePaiementFictif} tient le role sans reseau.
 *
 * <p><b>La cle decide, plus le profil.</b> Le choix se faisait par
 * {@code @Profile("prod")}, ce qui liait deux questions independantes : deployer en
 * production sans identifiant levait une exception au clic sur « payer », et
 * demarrer en demonstration avec un identifiant valide l ignorait. Voir
 * {@link ConditionPrestataire} pour le detail du basculement.</p>
 *
 * <p>Contrats non negociables de l implementation reelle :</p>
 * <ul>
 *   <li>timeout strict {@link #DELAI_MAXIMUM} sur tout appel synchrone ;</li>
 *   <li>validation de chaque reponse (montant, devise, statut attendu) avant de
 *       la restituer au metier ;</li>
 *   <li>projection des statuts Mollie vers {@link StatutPaiement} : open vers
 *       INITIE, pending vers EN_COURS, paid vers REUSSI, failed et canceled vers
 *       ECHOUE, expired vers EXPIRE ;</li>
 *   <li>identifiant lu de la configuration {@code autoservplus.paiement.mollie.*}
 *       (variables {@code MOLLIE_API_KEY}, {@code MOLLIE_PROFILE_ID}) — jamais dans
 *       le code ni dans un fichier versionne, et jamais journalise.</li>
 * </ul>
 */
@Service
@SiPrestataireConfigure
public class MollieGateway implements PrestatairePaiement {

    private static final Logger JOURNAL = LoggerFactory.getLogger(MollieGateway.class);

    /** Timeout strict des appels synchrones au prestataire (strategie securite §11). */
    public static final Duration DELAI_MAXIMUM = Duration.ofSeconds(5);

    private final MollieProprietes proprietes;

    /**
     * Refuse de demarrer sur une configuration qui echouerait au premier paiement
     * (voir {@link MollieProprietes#verifierCoherence()}). L arret vaut mieux que
     * les deux alternatives : rompre devant un client au moment de payer, ou se
     * rabattre en silence sur la simulation alors qu un identifiant reel a ete
     * fourni — c est-a-dire faire croire a un encaissement qui n a pas lieu.
     */
    public MollieGateway(MollieProprietes proprietes) {
        proprietes.verifierCoherence();
        this.proprietes = proprietes;
        JOURNAL.info("Prestataire de paiement Mollie actif (mode {}{}).",
                proprietes.modeTest() ? "TEST" : "REEL",
                proprietes.estJetonOrganisation()
                        ? ", jeton d organisation, profil " + proprietes.profilId()
                        : ", cle API");
    }

    @Override
    public PaiementCree creerPaiement(DemandePaiement demande) {
        // TODO : appel POST /v2/payments, avec DELAI_MAXIMUM, la cle d idempotence
        // de la demande, et validation de la reponse.
        throw new UnsupportedOperationException("Passerelle Mollie : implementation au commit suivant");
    }

    @Override
    public StatutPaiement lireStatut(String referencePrestataire) {
        // TODO : appel GET /v2/payments/{id}, avec DELAI_MAXIMUM, et projection du
        // statut Mollie vers StatutPaiement.
        throw new UnsupportedOperationException("Passerelle Mollie : implementation au commit suivant");
    }

    @Override
    public RemboursementCree rembourser(DemandeRemboursement demande) {
        // TODO : appel POST /v2/payments/{id}/refunds, avec DELAI_MAXIMUM et la cle
        // d idempotence de la demande (derivee du paiement, donc stable : un rejeu ne
        // doit pas rembourser deux fois).
        //
        // Trois points a ne pas manquer a l implementation reelle :
        //  - valider le montant rendu contre le montant demande avant de restituer,
        //    comme pour creerPaiement ;
        //  - un Refund Mollie nait « pending » et n est « refunded » qu apres
        //    execution bancaire. Le contrat actuel suppose une reponse synchrone :
        //    le passage a l asynchrone demandera un webhook de remboursement et un
        //    statut intermediaire sur paiement, pas seulement du code ici ;
        //  - Mollie refuse le Refund au-dela de son propre delai ; l echec doit
        //    remonter au service, qui annule alors la validation de la demande —
        //    aucun avoir ne doit exister sans remboursement.
        throw new UnsupportedOperationException("Passerelle Mollie : implementation au commit suivant");
    }
}
