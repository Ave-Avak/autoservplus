package be.autoservplus.facturation.service;

import be.autoservplus.common.exception.RegleMetierException;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.facturation.domain.Avoir;
import be.autoservplus.facturation.domain.Facture;
import be.autoservplus.facturation.repository.AvoirRepository;
import be.autoservplus.vente.domain.Commande;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * Emission des notes de credit (F30).
 *
 * <p><b>Pourquoi un avoir et non une correction de la facture.</b> Le trigger
 * {@code tg_facture_immuable} (V6) refuse tout UPDATE sur les donnees comptables
 * d une facture emise, et ce refus n est pas un obstacle a contourner : c est la
 * regle. Une facture transmise au client et declaree a la TVA ne se reecrit pas ; on
 * emet un second document qui la contre-passe, et les deux se lisent ensemble. Toute
 * la retractation repose sur ce point.</p>
 *
 * <p><b>Idempotence a deux etages</b>, comme pour la facture. Le controle applicatif
 * rend l operation rejouable : une validation redistribuee retrouve l avoir existant
 * et le retourne tel quel. Deux validations <b>simultanees</b> passeraient toutes
 * deux ce controle ; l index unique {@code uq_avoir_facture} (V27) refuse alors la
 * seconde insertion, sa transaction est annulee — et avec elle son numero, rendu au
 * compteur, ainsi que le remboursement qu elle portait.</p>
 *
 * <p><b>Propagation.MANDATORY</b> sur l emission : elle consomme un numero de la
 * suite legale et doit partager le sort de la transaction qui l a demandee — celle
 * qui rembourse et qui bascule la commande. Ouvrir sa propre transaction laisserait
 * exister un avoir sans remboursement si la suite echouait.</p>
 *
 * <p>Le PDF n est pas produit ici : l avoir existe en base des la validation, le
 * document n est fabrique qu a la premiere demande de telechargement
 * ({@code PdfAvoirService}). Emettre et imprimer sont deux gestes distincts — meme
 * choix que pour la facture.</p>
 */
@Service
@Transactional(readOnly = true)
public class AvoirService {

    private static final Logger log = LoggerFactory.getLogger(AvoirService.class);

    private final AvoirRepository avoirs;
    private final GenerateurNumeroAvoir numeros;
    private final Clock horloge;

    public AvoirService(AvoirRepository avoirs, GenerateurNumeroAvoir numeros, Clock horloge) {
        this.avoirs = avoirs;
        this.numeros = numeros;
        this.horloge = horloge;
    }

    /**
     * Emet la note de credit qui contre-passe integralement une facture, ou retourne
     * celle qui existe deja.
     *
     * <p>Les montants ne sont pas passes en parametre : ils sont ceux de la facture,
     * lus d elle. Laisser l appelant les fournir ouvrirait la porte a un avoir qui
     * n annule pas exactement la facture qu il pretend corriger — precisement
     * l erreur qu un controle de TVA cherche.</p>
     *
     * @param motif motif legal retenu, sous forme stable et non traduite
     *              (voir {@link Avoir#MOTIF_RETRACTATION})
     * @throws org.springframework.transaction.IllegalTransactionStateException
     *         si aucune transaction n est en cours
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Avoir contrePasser(Facture facture, String motif) {
        return avoirs.findByFacture(facture)
                .orElseGet(() -> emettre(facture, motif));
    }

    private Avoir emettre(Facture facture, String motif) {
        NumeroAvoir numero = numeros.prochain();
        Avoir avoir = avoirs.save(Avoir.contrePassant(
                numero.valeur(), facture, motif, horloge.instant()));
        log.info("Avoir {} emis en contre-passation de la facture {} ({} EUR TVAC).",
                avoir.getNumero(), facture.getNumero(), avoir.getMontantTvac());
        return avoir;
    }

    /** Avoir d une facture, s il a deja ete emis. */
    public Optional<Avoir> avoirDe(Facture facture) {
        return avoirs.findByFacture(facture);
    }

    /**
     * Charge un avoir en verifiant qu il appartient bien au membre — via la facture
     * qu il corrige, qui porte le titulaire. L avoir d autrui remonte comme
     * {@link RessourceIntrouvableException}, donc 404 et non 403 : confirmer
     * l existence d une note de credit a un tiers serait deja une fuite, s agissant
     * d un document nominatif. Meme mecanisme que {@code FactureService#pourMembre}.
     */
    public Avoir pourMembre(UUID reference, String email) {
        Avoir avoir = avoirs.findByReference(reference)
                .orElseThrow(() -> new RessourceIntrouvableException("Avoir", reference));
        if (!avoir.getFacture().getMembre().getEmail().equalsIgnoreCase(email)) {
            throw new RessourceIntrouvableException("Avoir", reference);
        }
        return avoir;
    }

    /**
     * Commande a l origine de l avoir, en remontant par la facture.
     *
     * @throws RegleMetierException si la facture corrigee vient d une intervention
     *         (RM-17) : le bloc n est pas branche, et le document d avoir affiche le
     *         numero de commande
     */
    public Commande commandeDe(Avoir avoir) {
        Commande commande = avoir.getFacture().getCommande();
        if (commande == null) {
            throw new RegleMetierException(
                    "L avoir d une facture d intervention n est pas encore gere.");
        }
        return commande;
    }
}
