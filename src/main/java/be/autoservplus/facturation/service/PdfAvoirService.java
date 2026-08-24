package be.autoservplus.facturation.service;

import be.autoservplus.facturation.domain.Avoir;
import be.autoservplus.facturation.domain.Facture;
import be.autoservplus.facturation.repository.AvoirRepository;
import be.autoservplus.facturation.service.dto.DocumentAvoir;
import be.autoservplus.facturation.service.dto.DocumentFacture;
import be.autoservplus.identite.domain.Utilisateur;
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
 * Fabrique et sert le PDF d une note de credit (F30), avec archivage.
 *
 * <p>Meme politique que {@code PdfFactureService}, et pour les memes raisons.
 * <b>Genere a la demande</b> : rien n oblige a produire un PDF que personne ne
 * reclamera — l avoir existe en base des la validation, c est lui qui fait foi.
 * <b>Archive une fois</b> : le document conserve dix ans doit rester celui qui a ete
 * remis au client, pas ce que le code produirait aujourd hui ; une evolution du
 * gabarit ne doit pas reecrire retroactivement des pieces deja transmises.</p>
 *
 * <p>Si le fichier archive a disparu, le service reconstruit plutot que d echouer :
 * les donnees comptables sont figees en base et le document reconstitue porte les
 * memes montants.</p>
 *
 * <p><b>Les lignes et la ventilation sont celles de la facture corrigee</b>, relues
 * par {@link FactureService}. Elles ne sont ni recopiees ni recalculees : les lignes
 * sont immuables depuis leur rattachement a la commande, et c est precisement ce qui
 * garantit qu un avoir contre-passe la facture au centime pres. Recalculer depuis le
 * catalogue du jour produirait, sur un prix modifie entre-temps, un avoir qui
 * n annule pas ce qu il pretend annuler.</p>
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
     * PDF de la note de credit : relu de l archive s il y est, compose et archive
     * sinon.
     *
     * <p>Transactionnel en ecriture pour le seul {@code chemin_pdf} : le trigger
     * {@code tg_avoir_immuable} (V27) laisse passer cette colonne precisement parce
     * qu elle ne porte aucune donnee comptable.</p>
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

    /** Nom de fichier propose au telechargement : le numero de l avoir, rien d autre. */
    public String nomDeFichier(Avoir avoir) {
        return "avoir-%s.pdf".formatted(avoir.getNumero());
    }

    /**
     * Constitue le document a imprimer. La locale est celle du membre, pas celle de
     * la session : la note de credit est emise dans la langue du client — la meme que
     * celle de la facture qu elle corrige.
     */
    private DocumentAvoir documentDe(Avoir avoir) {
        Facture facture = avoir.getFacture();
        Commande commande = avoirService.commandeDe(avoir);
        Utilisateur membre = facture.getMembre();
        Locale langue = Locale.forLanguageTag(membre.getLangue().name());
        List<DocumentFacture.LigneFacture> lignes = factureService.lignesDe(facture).stream()
                .map(PdfAvoirService::ligneDe)
                .toList();
        return new DocumentAvoir(
                avoir.getNumero(),
                avoir.getDateEmission(),
                facture.getNumero(),
                facture.getDateEmission(),
                commande.getNumero(),
                avoir.getMotif(),
                new DocumentFacture.ClientFacture(
                        // Marqueur traduit si le compte a ete anonymise (F23) : le
                        // document est emis dans la langue du client, meme regenere
                        // apres son depart.
                        membre.nomComplet(messages, langue),
                        membre.getRue(), membre.getNumeroRue(),
                        membre.getCodePostal(), membre.getLocalite(), membre.getPays(),
                        membre.getEmail()),
                lignes,
                factureService.ventilationDe(facture),
                avoir.getMontantHtva(),
                avoir.getMontantTva(),
                avoir.getMontantTvac(),
                langue);
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
