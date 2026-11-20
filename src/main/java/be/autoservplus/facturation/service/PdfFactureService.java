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
 * Fabrique et sert le PDF d une facture (F31), avec archivage.
 *
 * <p><b>Genere a la demande, archive une fois.</b> Le document n est pas fabrique a
 * l emission : rien n oblige a produire un PDF que personne ne reclamera. A la
 * premiere demande il est compose, ecrit dans l archive, et le chemin est inscrit
 * sur la facture. Toutes les demandes suivantes relisent ce fichier.</p>
 *
 * <p>Servir l archive plutot que regenerer n est pas une optimisation mais une
 * exigence : la facture conservee dix ans doit rester le document remis au client,
 * pas ce que le code produirait aujourd hui. Une evolution du gabarit ne doit pas
 * reecrire retroactivement des documents deja transmis.</p>
 *
 * <p>Si le fichier archive a disparu, le service le reconstruit plutot que d echouer :
 * les donnees comptables sont figees en base, le document reconstitue porte les memes
 * montants, et un client sans facture serait un probleme plus grave qu un document
 * regenere.</p>
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
     * PDF de la facture : relu de l archive s il y est, compose et archive sinon.
     *
     * <p>Transactionnel en ecriture pour le seul {@code chemin_pdf} : le trigger
     * {@code tg_facture_immuable} laisse passer cette colonne precisement parce
     * qu elle ne porte aucune donnee comptable.</p>
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

    /** Nom de fichier propose au telechargement : le numero de facture, rien d autre. */
    public String nomDeFichier(Facture facture) {
        return "facture-%s.pdf".formatted(facture.getNumero());
    }

    /**
     * Constitue le document a imprimer. La locale est celle du membre, pas celle de
     * la session : une facture est emise dans la langue du client.
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
                ligne.totalHtva());
    }
}
