package be.autoservplus.vente.service;

import be.autoservplus.catalogue.domain.ChoixPieces;
import be.autoservplus.communication.service.DetailsConfirmationCommande;
import be.autoservplus.communication.service.DetailsConfirmationCommande.LigneCommandee;
import be.autoservplus.communication.service.DetailsConfirmationCommande.RenonciationConfirmee;
import be.autoservplus.communication.service.PieceJointeCourriel;
import be.autoservplus.identite.domain.Consentement;
import be.autoservplus.identite.domain.Langue;
import be.autoservplus.identite.domain.TypeDocumentConsentement;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.ConsentementRepository;
import be.autoservplus.legal.domain.TypeDocumentVersionne;
import be.autoservplus.legal.service.ConditionsGeneralesPdf;
import be.autoservplus.legal.service.FormulaireRetractation;
import be.autoservplus.legal.service.NoticeGarantie;
import be.autoservplus.legal.service.VersionsDocumentsService;
import be.autoservplus.reservation.service.support.FormatageRdv;
import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.domain.LignePanier;
import be.autoservplus.vente.repository.CommandeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Rassemble la confirmation du contrat d'une commande payée (art. VI.46, § 7, CDE) :
 * lignes et totaux figés, version des CGV acceptée pour cette commande et sa
 * renonciation VI.53, lues par les preuves que V52 rattache à la commande, et les deux
 * pièces jointes.
 *
 * <p>Les CGV jointes sont celles de la preuve, jamais la version du jour : une
 * publication intervenue entre la commande et le paiement ne change pas le texte
 * accepté. Sans preuve rattachée (commande antérieure à V52 que la reprise n'a pas
 * pu relier), aucun texte n'est joint à sa place, le courriel le dit et l'écart est
 * journalisé.</p>
 *
 * <p>Les noms des fichiers joints suivent la langue du compte, comme leur contenu.</p>
 */
@Component
public class ConfirmationCommande {

    private static final Logger log = LoggerFactory.getLogger(ConfirmationCommande.class);
    private static final String PDF = "application/pdf";

    private final CommandeRepository commandes;
    private final ConsentementRepository consentements;
    private final VersionsDocumentsService versions;
    private final ConditionsGeneralesPdf conditionsGenerales;
    private final FormulaireRetractation formulaire;
    private final MessageSource messages;
    private final NoticeGarantie notice;

    public ConfirmationCommande(CommandeRepository commandes, ConsentementRepository consentements,
                                VersionsDocumentsService versions,
                                ConditionsGeneralesPdf conditionsGenerales,
                                FormulaireRetractation formulaire, MessageSource messages,
                                NoticeGarantie notice) {
        this.notice = notice;
        this.commandes = commandes;
        this.consentements = consentements;
        this.versions = versions;
        this.conditionsGenerales = conditionsGenerales;
        this.formulaire = formulaire;
        this.messages = messages;
    }

    public DetailsConfirmationCommande composer(Commande commande) {
        Utilisateur membre = commande.getMembre();
        Locale langue = Locale.of((membre.getLangue() == null ? Langue.fr : membre.getLangue()).name());
        List<LignePanier> pieces = commandes.lignesDe(commande);
        List<LignePanier> prestations = commandes.lignesServiceDe(commande);
        List<LigneCommandee> lignes = new ArrayList<>();
        for (LignePanier ligne : pieces) {
            lignes.add(ligneCommandee(ligne, langue));
        }
        for (LignePanier ligne : prestations) {
            lignes.add(ligneCommandee(ligne, langue));
        }

        List<PieceJointeCourriel> jointes = new ArrayList<>();
        String versionCgv = null;
        Optional<Consentement> cgv = preuve(commande, TypeDocumentConsentement.CGV);
        if (cgv.isPresent()) {
            Optional<byte[]> texte = conditionsGenerales.pdf(cgv.get().getVersionAcceptee(), langue,
                    commande.getNumero(), cgv.get().getDateConsentement());
            if (texte.isPresent()) {
                versionCgv = cgv.get().getVersionAcceptee();
                jointes.add(new PieceJointeCourriel(
                        messages.getMessage("courriel.confirmation-commande.fichier.cgv", new Object[]{versionCgv},
                                langue), PDF,
                        texte.get()));
            }
        }
        if (versionCgv == null) {
            log.warn("Commande {} : aucune version des CGV rattachée, confirmation envoyée sans elles.",
                    commande.getNumero());
        }
        jointes.add(new PieceJointeCourriel(
                messages.getMessage("courriel.confirmation-commande.fichier.formulaire", null, langue), PDF,
                formulaire.pdf(langue)));
        // VI.45 § 1er, 12° : la notice ne vise que les biens. Le PDF de la Commission part
        // tel quel, page en couleur seule, sur le support durable qu'est ce courriel.
        if (!pieces.isEmpty()) {
            jointes.add(new PieceJointeCourriel(
                    messages.getMessage("courriel.confirmation-commande.fichier.notice", null, langue), PDF,
                    notice.pdf(langue)));
        }

        return new DetailsConfirmationCommande(membre.getEmail(), membre.getPrenom(), membre.getLangue(),
                commande.getNumero(), commande.getDateCommande(),
                commande.getDatePaiement() != null ? commande.getDatePaiement() : commande.getDateCommande(),
                lignes, FormatageRdv.euros(commande.getMontantHtva(), langue),
                FormatageRdv.euros(commande.getMontantTva(), langue),
                FormatageRdv.euros(commande.getMontantTvac(), langue),
                !pieces.isEmpty(), !prestations.isEmpty(), versionCgv, renonciation(commande, langue),
                jointes);
    }

    /**
     * La renonciation cochée, avec le texte de sa version tel qu'il a été présenté. Un
     * refus n'a rien à confirmer : la prestation attend alors la fin du délai.
     */
    private RenonciationConfirmee renonciation(Commande commande, Locale langue) {
        if (!commande.isRenonciationVi53()) {
            return null;
        }
        return preuve(commande, TypeDocumentConsentement.RENONCIATION_RETRACTATION)
                .filter(Consentement::isAccorde)
                .flatMap(preuve -> versions.archive(TypeDocumentVersionne.RENONCIATION_RETRACTATION,
                                preuve.getVersionAcceptee(), langue)
                        .map(texte -> new RenonciationConfirmee(preuve.getDateConsentement(),
                                preuve.getVersionAcceptee(), texte.contenu().split("\n", 2)[0])))
                .orElseGet(() -> {
                    log.warn("Commande {} : renonciation VI.53 sans preuve lisible, non confirmée.",
                            commande.getNumero());
                    return null;
                });
    }

    private Optional<Consentement> preuve(Commande commande, TypeDocumentConsentement type) {
        return consentements.findFirstByCommandeIdAndTypeDocumentOrderByDateConsentementDesc(
                commande.getId(), type);
    }

    private static LigneCommandee ligneCommandee(LignePanier ligne, Locale langue) {
        return new LigneCommandee(ligne.getLibelleFige(), ligne.getQuantite(),
                FormatageRdv.euros(ligne.totalTvac(), langue), ligne.getTauxTva().stripTrailingZeros().toPlainString(),
                ChoixPieces.cleMentionDe(ligne.getChoixPieces()));
    }
}
