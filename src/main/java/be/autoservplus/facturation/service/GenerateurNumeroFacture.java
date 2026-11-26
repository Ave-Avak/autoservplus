package be.autoservplus.facturation.service;

import be.autoservplus.common.temps.FuseauGarage;
import be.autoservplus.facturation.domain.CompteurFacture;
import be.autoservplus.facturation.repository.CompteurFactureRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Attribue le numéro d'une facture, format {@code ANNEE-NNNN}, avec la garantie
 * légale d'une suite <b>sans trou</b> par exercice.
 *
 * <p>Séquence PostgreSQL écartée : elle est délibérément non transactionnelle et ne
 * se rejoue pas au rollback, si bien qu'une transaction annulée emporte son numéro
 * et creuse un trou, <b>interdit</b> pour une facture (AR n°1, art. 5 : numérotation
 * ininterrompue). Le compteur vit donc en table, incrémenté sous verrou dans la
 * transaction d'émission, incrément et insertion de la facture partagent le même sort.
 * Rendez-vous, interventions, commandes, devis et fils suivent le même principe par
 * {@code CompteurAnnuel}, sans que la loi l'impose.</p>
 *
 * <p>{@code Propagation.MANDATORY} verrouille cette règle dans le code : un numéro
 * ne peut pas être tiré hors d'une transaction existante. Un appel isolé échoue
 * immédiatement au lieu d'ouvrir sa propre transaction, de committer l'incrément,
 * puis de laisser l'émission échouer plus loin, exactement le trou qu'on évite.</p>
 *
 * <p>Passage d'année : l'exercice est l'année au garage de l'instant d'émission. La
 * première facture d'une nouvelle année crée sa ligne de compteur et repart à 0001.
 * Aucun traitement de bascule n'est nécessaire, il n'y a rien à réinitialiser.</p>
 */
@Component
public class GenerateurNumeroFacture {

    private final CompteurFactureRepository compteurs;
    private final Clock horloge;
    private final FuseauGarage fuseau;

    public GenerateurNumeroFacture(CompteurFactureRepository compteurs, Clock horloge,
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
    public NumeroFacture prochain() {
        // Année au garage, comme la date imprimée sur le document : l'horloge est en UTC.
        short exercice = (short) fuseau.annee(horloge.instant());
        // Création avant verrouillage : on ne peut pas verrouiller une ligne absente.
        compteurs.creerSiAbsent(exercice);
        CompteurFacture compteur = compteurs.verrouillerParExercice(exercice)
                .orElseThrow(() -> new IllegalStateException(
                        "Compteur de facture introuvable pour l exercice " + exercice));
        int sequence = compteur.consommerProchainNumero();
        // Flush immédiat : l'incrément part en base tant que le verrou est tenu.
        compteurs.saveAndFlush(compteur);
        return NumeroFacture.de(exercice, sequence);
    }
}
