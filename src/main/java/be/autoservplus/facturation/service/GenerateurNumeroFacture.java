package be.autoservplus.facturation.service;

import be.autoservplus.facturation.domain.CompteurFacture;
import be.autoservplus.facturation.repository.CompteurFactureRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Year;

/**
 * Attribue le numero d une facture, format {@code ANNEE-NNNN}, avec la garantie
 * legale d une suite <b>sans trou</b> par exercice.
 *
 * <p>Rupture assumee avec {@code GenerateurNumeroCommande} et
 * {@code GenerateurNumeroIntervention}, qui appellent {@code nextval} sur une
 * sequence PostgreSQL. Une sequence est deliberement non transactionnelle : elle ne
 * se rejoue pas au rollback, si bien qu une transaction annulee emporte son numero
 * et creuse un trou. Acceptable pour un numero de commande, <b>interdit</b> pour une
 * facture (AR n°1, art. 5 : numerotation ininterrompue). Le compteur vit donc en
 * table, incremente sous verrou dans la transaction d emission — increment et
 * insertion de la facture partagent le meme sort.</p>
 *
 * <p>{@code Propagation.MANDATORY} verrouille cette regle dans le code : un numero
 * ne peut pas etre tire hors d une transaction existante. Un appel isole echoue
 * immediatement au lieu d ouvrir sa propre transaction, de committer l increment,
 * puis de laisser l emission echouer plus loin — exactement le trou qu on evite.</p>
 *
 * <p>Passage d annee : {@link Year#now(Clock)} lit l horloge injectee, la premiere
 * facture d une nouvelle annee cree sa ligne de compteur et repart a 0001. Aucun
 * traitement de bascule n est necessaire, il n y a rien a reinitialiser.</p>
 */
@Component
public class GenerateurNumeroFacture {

    private final CompteurFactureRepository compteurs;
    private final Clock horloge;

    public GenerateurNumeroFacture(CompteurFactureRepository compteurs, Clock horloge) {
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
    public NumeroFacture prochain() {
        short exercice = (short) Year.now(horloge).getValue();
        // Creation avant verrouillage : on ne peut pas verrouiller une ligne absente.
        compteurs.creerSiAbsent(exercice);
        CompteurFacture compteur = compteurs.verrouillerParExercice(exercice)
                .orElseThrow(() -> new IllegalStateException(
                        "Compteur de facture introuvable pour l exercice " + exercice));
        int sequence = compteur.consommerProchainNumero();
        // Flush immediat : l increment part en base tant que le verrou est tenu.
        compteurs.saveAndFlush(compteur);
        return NumeroFacture.de(exercice, sequence);
    }
}
