package be.autoservplus.facturation.service;

import be.autoservplus.facturation.domain.Avoir;
import be.autoservplus.facturation.domain.Facture;
import be.autoservplus.facturation.repository.AvoirRepository;
import be.autoservplus.facturation.service.dto.DocumentAvoir;
import be.autoservplus.facturation.service.dto.DocumentFacture;
import be.autoservplus.vente.domain.Commande;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Fabrique et sert le PDF d'une note de crédit (F30), avec archivage.
 *
 * <p>Même politique que {@code PdfFactureService}, et pour les mêmes raisons.
 * <b>Généré à la demande</b> : rien n'oblige à produire un PDF que personne ne
 * réclamera. L'avoir existe en base dès la validation, c'est lui qui fait foi.
 * <b>Archivé une fois</b> : le document conservé sept ans doit rester celui qui a été
 * remis au client, pas ce que le code produirait aujourd'hui. Une évolution du
 * gabarit ne doit pas réécrire rétroactivement des pièces déjà transmises.</p>
 *
 * <p>Si le fichier archivé a disparu, le service reconstruit plutôt que d'échouer :
 * les données comptables sont figées en base et le document reconstitué porte les
 * mêmes montants.</p>
 *
 * <p><b>Les lignes et la ventilation sont celles de la facture corrigée</b>, relues
 * par {@link FactureService}. Elles ne sont ni recopiées ni recalculées : les lignes
 * sont immuables depuis leur rattachement à la commande, et c'est précisément ce qui
 * garantit qu'un avoir contre-passe la facture au centime près. Recalculer depuis le
 * catalogue du jour produirait, sur un prix modifié entre-temps, un avoir qui
 * n'annule pas ce qu'il prétend annuler.</p>
 */
@Service
public class PdfAvoirService {

    private static final Logger log = LoggerFactory.getLogger(PdfAvoirService.class);

    private final AvoirRepository avoirs;
    private final AvoirService avoirService;
    private final FactureService factureService;
    private final GenerateurPdfAvoir generateur;
    private final ArchiveComptable archive;
    private final MessageSource messages;

    public PdfAvoirService(AvoirRepository avoirs, AvoirService avoirService,
                           FactureService factureService, GenerateurPdfAvoir generateur,
                           ArchiveComptable archive, MessageSource messages) {
        this.avoirs = avoirs;
        this.avoirService = avoirService;
        this.factureService = factureService;
        this.generateur = generateur;
        this.archive = archive;
        this.messages = messages;
    }

    /**
     * PDF de la note de crédit : relu de l'archive s'il y est, composé et archivé
     * sinon.
     *
     * <p>Transactionnel en écriture pour le seul {@code chemin_pdf} : le trigger
     * {@code tg_avoir_immuable} (V27) laisse passer cette colonne précisément parce
     * qu'elle ne porte aucune donnée comptable.</p>
     */
    @Transactional
    public byte[] pdfDe(Avoir avoir) {
        if (avoir.estArchive()) {
            var archive = this.archive.lire(avoir.getCheminPdf());
            if (archive.isPresent()) {
                return archive.get();
            }
            log.warn("Avoir {} absent de l archive ({}) : reconstruction.",
                    avoir.getNumero(), avoir.getCheminPdf());
        }
        byte[] pdf = generateur.engendrer(documentDe(avoir));
        String chemin = archive.archiverAvoir(avoir.exercice(), avoir.getNumero(), pdf);
        avoir.archiverPdf(chemin);
        avoirs.saveAndFlush(avoir);
        return pdf;
    }

    /** Nom de fichier proposé au téléchargement : le numéro de l'avoir, rien d'autre. */
    public String nomDeFichier(Avoir avoir) {
        return "avoir-%s.pdf".formatted(avoir.getNumero());
    }

    /**
     * Constitue le document à imprimer. La locale est celle du membre, pas celle de
     * la session : la note de crédit est émise dans la langue du client, la même que
     * celle de la facture qu'elle corrige.
     */
    private DocumentAvoir documentDe(Avoir avoir) {
        Facture facture = avoir.getFacture();
        Commande commande = avoirService.commandeDe(avoir);
        // Instantané de la facture corrigée : la note de crédit vise le même client,
        // dans la même langue.
        Locale langue = Locale.forLanguageTag(facture.getLangueDocument().name());
        List<DocumentFacture.LigneFacture> lignes = factureService.lignesDuDocument(facture);
        return new DocumentAvoir(
                avoir.getNumero(),
                avoir.getDateEmission(),
                facture.getNumero(),
                facture.getDateEmission(),
                commande.getNumero(),
                avoir.getMotif(),
                PdfFactureService.clientDe(facture, messages, langue),
                lignes,
                factureService.ventilationDe(facture),
                avoir.getMontantHtva(),
                avoir.getMontantTva(),
                avoir.getMontantTvac(),
                langue);
    }

}
