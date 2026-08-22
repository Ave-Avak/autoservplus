package be.autoservplus.facturation.service;

import be.autoservplus.common.exception.RegleMetierException;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.facturation.domain.Facture;
import be.autoservplus.facturation.repository.FactureRepository;
import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.domain.LignePanier;
import be.autoservplus.vente.domain.StatutCommande;
import be.autoservplus.vente.repository.CommandeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Emission des factures (F31). Ce bloc ne traite que la facture de <b>commande</b> ;
 * la facturation d intervention (RM-17) reutilisera l entite sans la modifier.
 *
 * <p>L emission est le seul moment ou un numero est attribue : le compteur est
 * incremente dans cette transaction, si bien qu une emission qui echoue ne consomme
 * aucun numero et ne creuse aucun trou dans la suite legale.</p>
 *
 * <p><b>Idempotence</b> a deux etages. Le controle applicatif rend l operation
 * rejouable : un {@code CommandePayeeEvent} redistribue retrouve la facture
 * existante et la retourne telle quelle. Deux rejeux <b>simultanes</b> passeraient
 * tous deux ce controle : l index partiel {@code uq_facture_commande} refuse alors
 * le second insert, sa transaction est annulee et son numero rendu. Une verification
 * applicative seule ne suffirait pas ; l index seul transformerait chaque rejeu en
 * erreur.</p>
 *
 * <p>Le PDF n est pas produit ici : la facture existe en base des l encaissement,
 * le document n est fabrique qu a la premiere demande de telechargement
 * ({@code PdfFactureService}). Emettre et imprimer sont deux gestes distincts.</p>
 */
@Service
@Transactional(readOnly = true)
public class FactureService {

    private static final Logger log = LoggerFactory.getLogger(FactureService.class);

    private final FactureRepository factures;
    private final CommandeRepository commandes;
    private final GenerateurNumeroFacture numeros;
    private final Clock horloge;

    public FactureService(FactureRepository factures, CommandeRepository commandes,
                          GenerateurNumeroFacture numeros, Clock horloge) {
        this.factures = factures;
        this.commandes = commandes;
        this.numeros = numeros;
        this.horloge = horloge;
    }

    /**
     * Emet la facture de la commande, ou retourne celle qui existe deja.
     *
     * @throws RessourceIntrouvableException si la reference est inconnue
     * @throws RegleMetierException          si la commande n est pas payee
     */
    @Transactional
    public Facture emettrePourCommande(UUID referenceCommande) {
        Commande commande = commandes.findByReference(referenceCommande)
                .orElseThrow(() -> new RessourceIntrouvableException("Commande", referenceCommande));
        // Une facture atteste d un encaissement : elle ne precede jamais le paiement.
        if (commande.getStatut() != StatutCommande.PAYEE) {
            throw new RegleMetierException(
                    "La commande %s n est pas payee : aucune facture ne peut etre emise."
                            .formatted(commande.getNumero()));
        }
        return factures.findByCommande(commande)
                .orElseGet(() -> emettre(commande));
    }

    /** Facture d une commande, si elle a deja ete emise. */
    public java.util.Optional<Facture> factureDe(UUID referenceCommande) {
        return commandes.findByReference(referenceCommande).flatMap(factures::findByCommande);
    }

    private Facture emettre(Commande commande) {
        List<LignePanier> lignes = commandes.lignesDe(commande);
        VentilationTva ventilation = VentilationTva.desLignes(lignes);
        // Taux unique quand il l est, NULL sinon : la ventilation porte alors le detail.
        BigDecimal tauxApplique = ventilation.tauxUnique().orElse(null);

        NumeroFacture numero = numeros.prochain();
        Facture facture = factures.save(Facture.pourCommande(
                numero.valeur(), numero.exercice(), numero.sequenceAnnuelle(),
                commande, tauxApplique, horloge.instant()));
        log.info("Facture {} emise pour la commande {} ({} EUR TVAC).",
                facture.getNumero(), commande.getNumero(), facture.getMontantTvac());
        return facture;
    }

    /**
     * Ventilation TVA de la facture, recalculee des lignes figees de sa commande.
     * Elle n est pas stockee : les lignes etant immuables des leur rattachement a
     * une commande, la recalculer donne toujours le meme resultat — une table de
     * lignes de facture serait une duplication exacte, avec le risque de divergence
     * qui va avec.
     */
    public VentilationTva ventilationDe(Facture facture) {
        if (facture.getCommande() == null) {
            // Source intervention : bloc RM-17, non branche ici.
            throw new RegleMetierException(
                    "La ventilation d une facture d intervention n est pas encore geree.");
        }
        return VentilationTva.desLignes(commandes.lignesDe(facture.getCommande()));
    }

    /** Lignes facturees, dans l ordre stable de la commande. */
    public List<LignePanier> lignesDe(Facture facture) {
        return commandes.lignesDe(facture.getCommande());
    }
}
