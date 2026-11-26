package be.autoservplus.facturation.service;

import be.autoservplus.catalogue.domain.ChoixPieces;
import be.autoservplus.common.exception.RegleMetierException;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.common.montant.CalculTva;
import be.autoservplus.common.temps.FuseauGarage;
import be.autoservplus.facturation.domain.Facture;
import be.autoservplus.facturation.repository.FactureRepository;
import be.autoservplus.facturation.service.dto.DocumentFacture;
import be.autoservplus.facturation.service.dto.FactureVue;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.intervention.domain.Intervention;
import be.autoservplus.intervention.domain.LigneIntervention;
import be.autoservplus.intervention.service.FormatageQuantite;
import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.domain.LignePanier;
import be.autoservplus.vente.domain.StatutCommande;
import be.autoservplus.vente.repository.CommandeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Émission des factures (F31) : celle d'une commande payée en ligne, et celle d'une
 * intervention à sa clôture (A15, RM-17), à régler à l'atelier.
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
    private final FuseauGarage fuseau;
    private final Clock horloge;

    public FactureService(FactureRepository factures, CommandeRepository commandes,
                          GenerateurNumeroFacture numeros, FuseauGarage fuseau, Clock horloge) {
        this.factures = factures;
        this.commandes = commandes;
        this.numeros = numeros;
        this.fuseau = fuseau;
        this.horloge = horloge;
    }

    /**
     * Émet la facture d'atelier d'une intervention qui se clôt (A15, RM-17), dans la
     * transaction de la clôture : statut, stock et facture tiennent ou tombent ensemble, et
     * une clôture annulée ne consomme aucun numéro.
     *
     * <p>Lignes : {@link Intervention#lignesAFacturer()}, les ajouts acceptés seuls pour une
     * intervention née d'une commande payée (Q-H-41). Une facture est émise dès qu'une ligne
     * est facturable, même à 0 €. Montants : la ventilation de ces lignes, par taux sur le
     * total du document (règle du groupe G). Échéance : le jour d'émission au garage.</p>
     *
     * @param titulaire le client de la facture, vérifié par l'appelant (Q-H-14)
     * @return la facture, ou vide quand aucune ligne n'est à facturer
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Facture> emettrePourIntervention(Intervention intervention, Utilisateur titulaire) {
        List<LigneIntervention> lignes = intervention.lignesAFacturer();
        if (lignes.isEmpty()) {
            return Optional.empty();
        }
        VentilationTva ventilation = ventilationDesLignes(lignes);
        NumeroFacture numero = numeros.prochain();
        Instant maintenant = horloge.instant();
        Facture facture = factures.save(Facture.pourIntervention(
                numero.valeur(), numero.exercice(), numero.sequenceAnnuelle(), intervention, titulaire,
                ventilation.totalHtva(), ventilation.totalTva(), ventilation.totalTvac(),
                ventilation.tauxUnique().orElse(null), maintenant, fuseau.jour(maintenant)));
        log.info("Facture d'atelier {} emise pour l'intervention {} ({} EUR TVAC).",
                facture.getNumero(), intervention.getNumero(), facture.getMontantTvac());
        return Optional.of(facture);
    }

    /** Facture d'atelier d'une intervention, s'il y en a une. */
    public Optional<Facture> factureDe(Intervention intervention) {
        return factures.findByIntervention(intervention);
    }

    /** Facture de la commande dont naît l'intervention : celle que la facture d'atelier complète. */
    public Optional<Facture> factureCompleteePar(Facture factureDAtelier) {
        if (!factureDAtelier.estDAtelier()) {
            return Optional.empty();
        }
        Intervention intervention = interventionFacturee(factureDAtelier);
        if (intervention.getCommande() == null) {
            return Optional.empty();
        }
        return factures.findByCommande(intervention.getCommande());
    }

    /** Intervention d'une facture d'atelier, relue avec ses lignes dans la transaction en cours. */
    public Intervention interventionFacturee(Facture factureDAtelier) {
        return factures.interventionFacturee(factureDAtelier.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "La facture " + factureDAtelier.getNumero() + " ne vient d'aucune intervention."));
    }

    private static VentilationTva ventilationDesLignes(List<LigneIntervention> lignes) {
        return VentilationTva.desMontants(lignes.stream()
                .map(ligne -> new CalculTva.MontantTaxe(ligne.getTauxTva(), ligne.totalTvac()))
                .toList());
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
        List<LignePanier> lignes = commandes.toutesLesLignesDe(commande);
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
     * Ventilation TVA de la facture, recalculée de ses lignes figées : celles de la commande,
     * ou les lignes facturées de l'intervention, figées elles aussi une fois l'intervention
     * terminée. Elle n'est pas stockée : la recalculer donne toujours le même résultat, et
     * une table de lignes de facture serait une duplication exacte, avec le risque de
     * divergence qui va avec.
     */
    public VentilationTva ventilationDe(Facture facture) {
        if (facture.estDAtelier()) {
            return ventilationDesLignes(interventionFacturee(facture).lignesAFacturer());
        }
        return VentilationTva.desLignes(commandes.toutesLesLignesDe(facture.getCommande()));
    }

    /**
     * Lignes imprimées sur la facture ou sur sa note de crédit, dans la langue du document :
     * celles de la commande dans leur ordre stable, ou les lignes facturées de
     * l'intervention, dont la quantité se lit « 1,5 h » pour des heures.
     */
    public List<DocumentFacture.LigneFacture> lignesDuDocument(Facture facture) {
        Locale langue = Locale.forLanguageTag(facture.getLangueDocument().name());
        if (facture.estDAtelier()) {
            return interventionFacturee(facture).lignesAFacturer().stream()
                    .map(ligne -> new DocumentFacture.LigneFacture(ligne.getLibelleFige(),
                            FormatageQuantite.lisible(ligne.getQuantite(), ligne.getUnite(), langue),
                            ligne.getPrixUnitaireHtva(), ligne.getTauxTva(), ligne.totalTvac(),
                            ChoixPieces.cleMentionDe(ligne.getChoixPieces())))
                    .toList();
        }
        return commandes.toutesLesLignesDe(facture.getCommande()).stream()
                .map(ligne -> new DocumentFacture.LigneFacture(ligne.getLibelleFige(), ligne.getQuantite(),
                        ligne.getPrixUnitaireHtva(), ligne.getTauxTva(), ligne.totalTvac(),
                        ChoixPieces.cleMentionDe(ligne.getChoixPieces())))
                .toList();
    }
}
