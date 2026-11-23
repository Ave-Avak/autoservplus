package be.autoservplus.facturation.service;

import be.autoservplus.common.temps.FuseauGarage;
import be.autoservplus.facturation.domain.CompteurAvoir;
import be.autoservplus.facturation.repository.CompteurAvoirRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Attribue le numéro d'une note de crédit, format {@code AV-ANNEE-NNNN}, avec la
 * même garantie légale de suite <b>sans trou</b> par exercice que la facture.
 *
 * <p>Jumeau de {@link GenerateurNumeroFacture}, et pour la même raison : un document
 * rectificatif est soumis à la discipline de numérotation de l'AR n°1 (art. 5, suite
 * ininterrompue. Art. 12 pour la note de crédit). Reprendre ici la séquence
 * {@code seq_numero_avoir} du socle V9 aurait été plus court d'une classe, mais
 * aurait fait cohabiter deux raisonnements contradictoires dans le même module :
 * V26 a disqualifié la séquence pour la facture au motif qu'elle laisse des trous au
 * rollback, et ce motif ne devient pas faux parce que le document change de nom.</p>
 *
 * <p>Compteur <b>séparé</b> de celui des factures : un avoir a sa propre suite légale
 * et ne consomme pas un numéro de facture. Les deux suites se lisent indépendamment
 * dans les livres.</p>
 *
 * <p>{@code Propagation.MANDATORY} verrouille la règle dans le code : un numéro ne
 * peut pas être tiré hors d'une transaction existante. Un appel isolé échoue
 * immédiatement au lieu d'ouvrir sa propre transaction, de committer l'incrément,
 * puis de laisser l'émission échouer plus loin : exactement le trou qu'on évite.</p>
 */
@Component
public class GenerateurNumeroAvoir {

    private final CompteurAvoirRepository compteurs;
    private final Clock horloge;
    private final FuseauGarage fuseau;

    public GenerateurNumeroAvoir(CompteurAvoirRepository compteurs, Clock horloge,
                                 FuseauGarage fuseau) {
        this.fuseau = fuseau;
        this.compteurs = compteurs;
        this.horloge = horloge;
    }

    /**
     * Consomme le numéro suivant de l'exercice courant.
     *
     * @throws org.springframework.transaction.IllegalTransactionStateException
     *         si aucune transaction n'est en cours
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public NumeroAvoir prochain() {
        // Année au garage, comme la date imprimée sur le document : l'horloge est en UTC.
        short exercice = (short) fuseau.annee(horloge.instant());
        // Création avant verrouillage : on ne peut pas verrouiller une ligne absente.
        compteurs.creerSiAbsent(exercice);
        CompteurAvoir compteur = compteurs.verrouillerParExercice(exercice)
                .orElseThrow(() -> new IllegalStateException(
                        "Compteur d avoir introuvable pour l exercice " + exercice));
        int sequence = compteur.consommerProchainNumero();
        // Flush immédiat : l'incrément part en base tant que le verrou est tenu.
        compteurs.saveAndFlush(compteur);
        return NumeroAvoir.de(exercice, sequence);
    }
}
