package be.autoservplus.facturation.service;

import be.autoservplus.facturation.domain.Facture;
import be.autoservplus.facturation.repository.FactureRepository;
import be.autoservplus.facturation.service.dto.DocumentFacture;
import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.domain.LignePanier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Fabrique et sert le PDF d'une facture (F31), avec archivage.
 *
 * <p><b>Généré à la demande, archivé une fois.</b> Le document n'est pas fabriqué à
 * l'émission : rien n'oblige à produire un PDF que personne ne réclamera. À la
 * première demande il est composé, écrit dans l'archive, et le chemin est inscrit
 * sur la facture. Toutes les demandes suivantes relisent ce fichier.</p>
 *
 * <p>Servir l'archive plutôt que régénérer n'est pas une optimisation mais une
 * exigence : la facture conservée dix ans doit rester le document remis au client,
 * pas ce que le code produirait aujourd'hui. Une évolution du gabarit ne doit pas
 * réécrire rétroactivement des documents déjà transmis.</p>
 *
 * <p>Si le fichier archivé a disparu, le service le reconstruit plutôt que d'échouer :
 * les données comptables sont figées en base, le document reconstitué porte les mêmes
 * montants, et un client sans facture serait un problème plus grave qu'un document
 * régénéré.</p>
 */
@Service
public class PdfFactureService {

    private static final Logger log = LoggerFactory.getLogger(PdfFactureService.class);

    private final FactureRepository factures;
    private final FactureService factureService;
    private final GenerateurPdfFacture generateur;
    private final ArchiveComptable archive;
    private final MessageSource messages;

    public PdfFactureService(FactureRepository factures, FactureService factureService,
                             GenerateurPdfFacture generateur, ArchiveComptable archive,
                             MessageSource messages) {
        this.factures = factures;
        this.factureService = factureService;
        this.generateur = generateur;
        this.archive = archive;
        this.messages = messages;
    }

    /**
     * PDF de la facture : relu de l'archive s'il y est, composé et archivé sinon.
     *
     * <p>Transactionnel en écriture pour le seul {@code chemin_pdf} : le trigger
     * {@code tg_facture_immuable} laisse passer cette colonne précisément parce
     * qu'elle ne porte aucune donnée comptable.</p>
     */
    @Transactional
    public byte[] pdfDe(Facture facture) {
        if (facture.estArchivee()) {
            var archivee = archive.lire(facture.getCheminPdf());
            if (archivee.isPresent()) {
                return archivee.get();
            }
            log.warn("Facture {} absente de l archive ({}) : reconstruction.",
                    facture.getNumero(), facture.getCheminPdf());
        }
        byte[] pdf = generateur.engendrer(documentDe(facture));
        String chemin = archive.archiver(facture.getExercice(), facture.getNumero(), pdf);
        facture.archiverPdf(chemin);
        factures.saveAndFlush(facture);
        return pdf;
    }

    /** Nom de fichier proposé au téléchargement : le numéro de facture, rien d'autre. */
    public String nomDeFichier(Facture facture) {
        return "facture-%s.pdf".formatted(facture.getNumero());
    }

    /**
     * Constitue le document à imprimer. La locale est celle du membre, pas celle de
     * la session : une facture est émise dans la langue du client.
     */
    private DocumentFacture documentDe(Facture facture) {
        Commande commande = facture.getCommande();
        // Langue et client de l'instantané figé à l'émission (V61), jamais du compte vif.
        Locale langue = Locale.forLanguageTag(facture.getLangueDocument().name());
        List<DocumentFacture.LigneFacture> lignes = factureService.lignesDe(facture).stream()
                .map(PdfFactureService::ligneDe)
                .toList();
        return new DocumentFacture(
                facture.getNumero(),
                facture.getDateEmission(),
                commande.getNumero(),
                commande.getDatePaiement(),
                clientDe(facture, messages, langue),
                lignes,
                factureService.ventilationDe(facture),
                facture.getMontantHtva(),
                facture.getMontantTva(),
                facture.getMontantTvac(),
                langue);
    }

    /** Client de l'instantané de la facture, repris tel quel par la note de crédit. */
    static DocumentFacture.ClientFacture clientDe(Facture facture, MessageSource messages,
                                                  Locale langue) {
        return new DocumentFacture.ClientFacture(
                facture.clientNomComplet(messages, langue),
                facture.getClientRue(), facture.getClientNumeroRue(),
                facture.getClientCodePostal(), facture.getClientLocalite(),
                facture.getClientPays());
    }

    private static DocumentFacture.LigneFacture ligneDe(LignePanier ligne) {
        return new DocumentFacture.LigneFacture(
                ligne.getLibelleFige(),
                ligne.getQuantite(),
                ligne.getPrixUnitaireHtva(),
                ligne.getTauxTva(),
                ligne.totalTvac());
    }
}
