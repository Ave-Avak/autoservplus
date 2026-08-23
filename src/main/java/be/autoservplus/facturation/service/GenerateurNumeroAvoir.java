package be.autoservplus.facturation.service;

import be.autoservplus.facturation.domain.CompteurAvoir;
import be.autoservplus.facturation.repository.CompteurAvoirRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Year;

/**
 * Attribue le numero d une note de credit, format {@code AV-ANNEE-NNNN}, avec la
 * meme garantie legale de suite <b>sans trou</b> par exercice que la facture.
 *
 * <p>Jumeau de {@link GenerateurNumeroFacture}, et pour la meme raison : un document
 * rectificatif est soumis a la discipline de numerotation de l AR n°1 (art. 5, suite
 * ininterrompue ; art. 12 pour la note de credit). Reprendre ici la sequence
 * {@code seq_numero_avoir} du socle V9 aurait ete plus court d une classe, mais
 * aurait fait cohabiter deux raisonnements contradictoires dans le meme module :
 * V26 a disqualifie la sequence pour la facture au motif qu elle laisse des trous au
 * rollback, et ce motif ne devient pas faux parce que le document change de nom.</p>
 *
 * <p>Compteur <b>separe</b> de celui des factures : un avoir a sa propre suite legale
 * et ne consomme pas un numero de facture. Les deux suites se lisent independamment
 * dans les livres.</p>
 *
 * <p>{@code Propagation.MANDATORY} verrouille la regle dans le code : un numero ne
 * peut pas etre tire hors d une transaction existante. Un appel isole echoue
 * immediatement au lieu d ouvrir sa propre transaction, de committer l increment,
 * puis de laisser l emission echouer plus loin — exactement le trou qu on evite.</p>
 */
@Component
public class GenerateurNumeroAvoir {

    private final CompteurAvoirRepository compteurs;
    private final Clock horloge;

    public GenerateurNumeroAvoir(CompteurAvoirRepository compteurs, Clock horloge) {
        this.compteurs = compteurs;
        this.horloge = horloge;
    }

    /**
     * Consomme le numero suivant de l exercice courant.
     *
     * @throws org.springframework.transaction.IllegalTransactionStateException
     *         si aucune transaction n est en cours
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public NumeroAvoir prochain() {
        short exercice = (short) Year.now(horloge).getValue();
        // Creation avant verrouillage : on ne peut pas verrouiller une ligne absente.
        compteurs.creerSiAbsent(exercice);
        CompteurAvoir compteur = compteurs.verrouillerParExercice(exercice)
                .orElseThrow(() -> new IllegalStateException(
                        "Compteur d avoir introuvable pour l exercice " + exercice));
        int sequence = compteur.consommerProchainNumero();
        // Flush immediat : l increment part en base tant que le verrou est tenu.
        compteurs.saveAndFlush(compteur);
        return NumeroAvoir.de(exercice, sequence);
    }
}
