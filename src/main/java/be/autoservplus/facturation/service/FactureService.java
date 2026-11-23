package be.autoservplus.facturation.service;

import be.autoservplus.common.exception.RegleMetierException;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.facturation.domain.Facture;
import be.autoservplus.facturation.repository.FactureRepository;
import be.autoservplus.facturation.service.dto.FactureVue;
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
import java.util.Optional;
import java.util.UUID;

/**
 * Émission des factures (F31). Ce bloc ne traite que la facture de <b>commande</b>.
 * La facturation d'intervention (RM-17) réutilisera l'entité sans la modifier.
 *
 * <p>L'émission est le seul moment où un numéro est attribué : le compteur est
 * incrémenté dans cette transaction, si bien qu'une émission qui échoue ne consomme
 * aucun numéro et ne creuse aucun trou dans la suite légale.</p>
 *
 * <p><b>Idempotence</b> à deux étages. Le contrôle applicatif rend l'opération
 * rejouable : un {@code CommandePayeeEvent} redistribué retrouve la facture
 * existante et la retourne telle quelle. Deux rejeux <b>simultanés</b> passeraient
 * tous deux ce contrôle : l'index partiel {@code uq_facture_commande} refuse alors
 * le second insert, sa transaction est annulée et son numéro rendu. Une vérification
 * applicative seule ne suffirait pas. L'index seul transformerait chaque rejeu en
 * erreur.</p>
 *
 * <p>Le PDF n'est pas produit ici : la facture existe en base dès l'encaissement,
 * le document n'est fabriqué qu'à la première demande de téléchargement
 * ({@code PdfFactureService}). Émettre et imprimer sont deux gestes distincts.</p>
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
     * Émet la facture de la commande, ou retourne celle qui existe déjà.
     *
     * @throws RessourceIntrouvableException si la référence est inconnue
     * @throws RegleMetierException          si la commande n'est pas payée
     */
    @Transactional
    public Facture emettrePourCommande(UUID referenceCommande) {
        Commande commande = commandes.findByReference(referenceCommande)
                .orElseThrow(() -> new RessourceIntrouvableException("Commande", referenceCommande));
        // Une facture atteste d'un encaissement : elle ne précède jamais le paiement.
        if (commande.getStatut() != StatutCommande.PAYEE) {
            throw new RegleMetierException(
                    "La commande %s n est pas payee : aucune facture ne peut etre emise."
                            .formatted(commande.getNumero()));
        }
        return factures.findByCommande(commande)
                .orElseGet(() -> emettre(commande));
    }

    /** Facture d'une commande, si elle a déjà été émise. */
    public Optional<Facture> factureDe(UUID referenceCommande) {
        return commandes.findByReference(referenceCommande).flatMap(factures::findByCommande);
    }

    /** Factures du membre connecté, pour rattacher un lien à chaque commande facturée. */
    public List<FactureVue> facturesDuMembre(String email) {
        return factures.facturesDuMembre(email).stream().map(FactureVue::de).toList();
    }

    /**
     * Charge une facture en vérifiant qu'elle appartient bien au membre. La facture
     * d'autrui remonte comme {@link RessourceIntrouvableException}, donc 404, le
     * même code qu'une référence inconnue : confirmer l'existence d'une facture à un
     * tiers serait déjà une fuite, s'agissant d'un document nominatif.
     */
    public Facture pourMembre(UUID reference, String email) {
        Facture facture = factures.findByReference(reference)
                .orElseThrow(() -> new RessourceIntrouvableException("Facture", reference));
        if (!facture.getMembre().getEmail().equals(email)) {
            throw new RessourceIntrouvableException("Facture", reference);
        }
        return facture;
    }

    private Facture emettre(Commande commande) {
        List<LignePanier> lignes = commandes.lignesDe(commande);
        VentilationTva ventilation = VentilationTva.desLignes(lignes);
        // Taux unique quand il l'est, NULL sinon : la ventilation porte alors le détail.
        BigDecimal tauxApplique = ventilation.tauxUnique().orElse(null);

        NumeroFacture numero = numeros.prochain();
        Facture facture = factures.save(Facture.pourCommande(
                numero.valeur(), numero.exercice(), numero.sequenceAnnuelle(),
                commande, tauxApplique, horloge.instant()));
        log.info("Facture {} emise pour la commande {} ({} EUR TVAC).",
                facture.getNumero(), commande.getNumero(), facture.getMontantTvac());
        signalerEcartDeVentilation(facture, commande, ventilation);
        // Limite assumée : le récapitulatif exige l'adresse et le profil refuse de
        // l'effacer tant que la commande attend son paiement. Si elle manque malgré
        // tout, la facture est émise : le paiement est encaissé, et refuser laisserait
        // une commande payée sans facture. Le défaut est signalé pour être corrigé par
        // une note de crédit et une nouvelle facture.
        if (facture.getClientRue() == null || facture.getClientCodePostal() == null
                || facture.getClientLocalite() == null) {
            log.warn("Facture {} emise sans adresse complete du client (commande {}).",
                    facture.getNumero(), commande.getNumero());
        }
        return facture;
    }

    /**
     * La facture reprend les montants figés de la commande, son PDF imprime une ventilation
     * recalculée des lignes : les deux doivent donner les mêmes nombres. Un écart ne peut
     * venir que d'une commande conclue sous une autre formule de calcul. La facture est émise
     * quand même, une commande payée doit avoir la sienne (RM-22), et l'écart est écrit en
     * erreur pour être corrigé par une note de crédit et une nouvelle facture.
     */
    private void signalerEcartDeVentilation(Facture facture, Commande commande, VentilationTva ventilation) {
        if (ventilation.totalHtva().compareTo(commande.getMontantHtva()) != 0
                || ventilation.totalTva().compareTo(commande.getMontantTva()) != 0
                || ventilation.totalTvac().compareTo(commande.getMontantTvac()) != 0) {
            log.error("Facture {} : les montants figés de la commande {} ({} HTVA, {} TVA, {} TVAC) "
                            + "diffèrent de la ventilation de ses lignes ({} HTVA, {} TVA, {} TVAC).",
                    facture.getNumero(), commande.getNumero(),
                    commande.getMontantHtva(), commande.getMontantTva(), commande.getMontantTvac(),
                    ventilation.totalHtva(), ventilation.totalTva(), ventilation.totalTvac());
        }
    }

    /**
     * Ventilation TVA de la facture, recalculée des lignes figées de sa commande.
     * Elle n'est pas stockée : les lignes étant immuables dès leur rattachement à
     * une commande, la recalculer donne toujours le même résultat. Une table de
     * lignes de facture serait une duplication exacte, avec le risque de divergence
     * qui va avec.
     */
    public VentilationTva ventilationDe(Facture facture) {
        if (facture.getCommande() == null) {
            // Source intervention : bloc RM-17, non branché ici.
            throw new RegleMetierException(
                    "La ventilation d une facture d intervention n est pas encore geree.");
        }
        return VentilationTva.desLignes(commandes.lignesDe(facture.getCommande()));
    }

    /** Lignes facturées, dans l'ordre stable de la commande. */
    public List<LignePanier> lignesDe(Facture facture) {
        return commandes.lignesDe(facture.getCommande());
    }
}
