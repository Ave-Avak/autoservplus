package be.autoservplus.communication.service;

import be.autoservplus.common.temps.FuseauGarage;
import be.autoservplus.config.IdentiteGarage;
import be.autoservplus.config.MentionsIdentiteGarage;
import be.autoservplus.identite.domain.ChampCompte;
import be.autoservplus.identite.domain.Langue;
import be.autoservplus.identite.domain.Utilisateur;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Seule implémentation de {@link ServiceCourriel} : rédige chaque courriel puis le
 * confie à {@link EnvoiApresCommit}, qui le fait partir après la validation de la
 * transaction par le {@link TransportCourriel} configuré.
 *
 * <p>Texte brut, rédigé par l'i18n dans la langue enregistrée sur le compte du
 * destinataire, et non dans celle de la requête en cours : une réinitialisation est
 * anonyme, une décision du garage est prise par un administrateur qui lit une autre
 * langue, et le destinataire lit son courriel ailleurs. Les dates suivent la même
 * langue, dans le fuseau de l'atelier.</p>
 *
 * <p>Le corps se compose d'une salutation, du message propre au type et d'une
 * signature au nom du garage. Les clés sont {@code courriel.<type>.sujet} et
 * {@code courriel.<type>.corps}.</p>
 */
@Service
public class CourrielSortant implements ServiceCourriel {

    private static final Logger log = LoggerFactory.getLogger(CourrielSortant.class);

    /**
     * Types envoyés hors du fil de la requête ({@link ExecuteurCourriels}) : ceux qu'un
     * formulaire anonyme ou un mot de passe faux déclenche pour une adresse qui a un
     * compte, et pour elle seule. Dans la requête, leur durée dirait à un tiers que
     * l'adresse saisie existe.
     */
    static final Set<String> HORS_DE_LA_REQUETE = Set.of("verification-adresse",
            "reinitialisation", "rappel-verification", "verrouillage-connexion",
            "tentative-inscription");

    private final MessageSource messages;
    private final EnvoiApresCommit envoi;
    private final IdentiteGarage garage;
    private final FuseauGarage fuseau;
    private final LiensCourriel liens;
    private final MentionsIdentiteGarage identite;

    public CourrielSortant(MessageSource messages, EnvoiApresCommit envoi,
                           IdentiteGarage garage, FuseauGarage fuseau,
                           LiensCourriel liens, MentionsIdentiteGarage identite) {
        this.liens = liens;
        this.identite = identite;
        this.messages = messages;
        this.envoi = envoi;
        this.garage = garage;
        this.fuseau = fuseau;
    }

    // --- compte ---------------------------------------------------------------------

    @Override
    public void envoyerVerificationAdresse(Utilisateur destinataire, String lienVerification) {
        auCompte("verification-adresse", destinataire, null, lienVerification);
    }

    @Override
    public void envoyerReinitialisationMotDePasse(Utilisateur destinataire, String lien) {
        auCompte("reinitialisation", destinataire, null, lien);
    }

    @Override
    public void envoyerAvisTentativeInscription(Utilisateur destinataire) {
        auCompte("tentative-inscription", destinataire, null, liens.vers("/connexion"),
                liens.vers("/mot-de-passe/oublie"));
    }

    @Override
    public void envoyerRappelVerification(Utilisateur destinataire, String lienVerification) {
        auCompte("rappel-verification", destinataire, null, lienVerification);
    }

    @Override
    public void envoyerLienDefinitionMotDePasse(Utilisateur destinataire, String lien) {
        auCompte("definition-mot-de-passe", destinataire, null, lien);
    }

    @Override
    public void envoyerAvisVerrouillageSecondFacteur(Utilisateur destinataire, Instant jusqua) {
        Locale langue = langue(destinataire.getLangue());
        ZonedDateTime fin = jusqua.atZone(fuseau.zone());
        auCompte("verrouillage-second-facteur", destinataire, null,
                jour(fin, langue, "d MMMM yyyy"), heure(fin));
    }

    @Override
    public void envoyerAvisVerrouillageConnexion(Utilisateur destinataire, Instant jusqua) {
        Locale langue = langue(destinataire.getLangue());
        ZonedDateTime fin = jusqua.atZone(fuseau.zone());
        auCompte("verrouillage-connexion", destinataire, null,
                jour(fin, langue, "d MMMM yyyy"), heure(fin));
    }

    @Override
    public void envoyerAvisReinitialisationSecondFacteur(Utilisateur destinataire) {
        auCompte("second-facteur-reinitialise", destinataire, null);
    }

    @Override
    public void envoyerAvisMotDePasseModifie(Utilisateur destinataire, OrigineMotDePasse origine) {
        Locale langue = langue(destinataire.getLangue());
        String prefixe = "courriel.mot-de-passe-modifie.";
        String corps = texte(prefixe + origine.name().toLowerCase(Locale.ROOT).replace('_', '-'),
                langue) + "\n" + texte(prefixe + "conseil", langue);
        partir("mot-de-passe-modifie", destinataire.getEmail(), destinataire.nomComplet(),
                destinataire.getPrenom(), langue, corps, null);
    }

    @Override
    public void envoyerAvisModificationCompte(Utilisateur destinataire, List<ChampCompte> champs) {
        Locale langue = langue(destinataire.getLangue());
        String liste = champs.stream()
                .map(champ -> texte("compte.champ." + champ.name(), langue))
                .collect(Collectors.joining(", "));
        auCompte("compte-modifie", destinataire, null, liste);
    }

    @Override
    public void envoyerConfirmationNouvelleAdresse(DetailsChangementEmailCourriel details,
                                                   String lienConfirmation) {
        Locale langue = langue(details.langue());
        partir("nouvelle-adresse", details.destinataire(), details.prenom(), details.prenom(),
                langue, corps("nouvelle-adresse", langue, details.ancienneAdresse(),
                        lienConfirmation), null);
    }

    @Override
    public void envoyerAvisChangementAdresse(DetailsChangementEmailCourriel details) {
        Locale langue = langue(details.langue());
        partir("avis-changement-adresse", details.destinataire(), details.prenom(),
                details.prenom(), langue,
                corps("avis-changement-adresse", langue, details.nouvelleAdresse()), null);
    }

    @Override
    public void envoyerConfirmationSuppressionCompte(DetailsSuppressionCompteCourriel details) {
        Locale langue = langue(details.langue());
        partir("compte-supprime", details.adresseEmail(), details.prenom(), details.prenom(),
                langue, corps("compte-supprime", langue), null);
    }

    // --- rendez-vous et atelier -----------------------------------------------------

    @Override
    public void envoyerConfirmationRdv(Utilisateur destinataire, DetailsRdvCourriel rdv,
                                       PieceJointeCourriel agenda) {
        Locale langue = langue(destinataire.getLangue());
        String corps = corps("rdv-confirme", langue, rdv.numero(),
                jour(rdv.debut(), langue, "EEEE d MMMM yyyy"), heure(rdv.debut()));
        if (agenda != null) {
            corps = corps + "\n" + texte("courriel.rdv-confirme.agenda", langue);
        }
        partir("rdv-confirme", destinataire.getEmail(), destinataire.nomComplet(),
                destinataire.getPrenom(), langue, corps, agenda);
    }

    @Override
    public void envoyerRefusRdv(Utilisateur destinataire, DetailsRdvCourriel rdv, String motif) {
        Locale langue = langue(destinataire.getLangue());
        auCompte("rdv-refuse", destinataire, null, rdv.numero(),
                jour(rdv.debut(), langue, "EEEE d MMMM yyyy"), heure(rdv.debut()), motif);
    }

    @Override
    public void envoyerAnnulationParLeGarage(Utilisateur destinataire, DetailsRdvCourriel rdv,
                                             String motif) {
        Locale langue = langue(destinataire.getLangue());
        auCompte("rdv-annule", destinataire, null, rdv.numero(),
                jour(rdv.debut(), langue, "EEEE d MMMM yyyy"), heure(rdv.debut()), motif);
    }

    @Override
    public void envoyerAccuseDemandeRdv(Utilisateur destinataire, DetailsRdvCourriel rdv,
                                        ZonedDateTime echeance) {
        Locale langue = langue(destinataire.getLangue());
        auCompte("rdv-recu", destinataire, null, rdv.numero(),
                jour(rdv.debut(), langue, "EEEE d MMMM yyyy"), heure(rdv.debut()),
                jour(echeance, langue, "EEEE d MMMM yyyy"), heure(echeance));
    }

    @Override
    public void envoyerAccuseRdvConfirme(Utilisateur destinataire, DetailsRdvCourriel rdv,
                                         PieceJointeCourriel agenda) {
        Locale langue = langue(destinataire.getLangue());
        String corps = corps("rdv-recu-confirme", langue, rdv.numero(),
                jour(rdv.debut(), langue, "EEEE d MMMM yyyy"), heure(rdv.debut()));
        if (agenda != null) {
            corps = corps + "\n" + texte("courriel.rdv-confirme.agenda", langue);
        }
        partir("rdv-recu-confirme", destinataire.getEmail(), destinataire.nomComplet(),
                destinataire.getPrenom(), langue, corps, agenda);
    }

    @Override
    public void envoyerExpirationRdv(Utilisateur destinataire, DetailsRdvCourriel rdv) {
        Locale langue = langue(destinataire.getLangue());
        auCompte("rdv-expire", destinataire, null, rdv.numero(),
                jour(rdv.debut(), langue, "EEEE d MMMM yyyy"), heure(rdv.debut()));
    }

    @Override
    public void envoyerRelanceAuGarage(DetailsRdvCourriel rdv, String membre, ZonedDateTime echeance,
                                       String cheminDetail) {
        Locale langue = Locale.FRENCH;
        auGarage("rdv-relance-garage", rdv.numero(), langue, rdv.numero(),
                jour(rdv.debut(), langue, "EEEE d MMMM yyyy"), heure(rdv.debut()), membre,
                jour(echeance, langue, "EEEE d MMMM"), heure(echeance), liens.vers(cheminDetail));
    }

    @Override
    public void envoyerAvisNouvelleDemandeAuGarage(DetailsRdvCourriel rdv, String membre,
                                                   boolean confirme, String cheminDetail) {
        Locale langue = Locale.FRENCH;
        auGarage(confirme ? "rdv-avis-garage-confirme" : "rdv-avis-garage", rdv.numero(), langue,
                rdv.numero(), jour(rdv.debut(), langue, "EEEE d MMMM yyyy"), heure(rdv.debut()),
                membre, liens.vers(cheminDetail));
    }

    @Override
    public void envoyerRappelRdv(Utilisateur destinataire, DetailsRdvCourriel rdv) {
        Locale langue = langue(destinataire.getLangue());
        auCompte("rdv-rappel", destinataire, null, rdv.numero(),
                jour(rdv.debut(), langue, "EEEE d MMMM yyyy"), heure(rdv.debut()));
    }

    @Override
    public void envoyerAnnulationParLeMembre(Utilisateur destinataire, DetailsRdvCourriel rdv) {
        Locale langue = langue(destinataire.getLangue());
        auCompte("rdv-annule-membre", destinataire, null, rdv.numero(),
                jour(rdv.debut(), langue, "EEEE d MMMM yyyy"), heure(rdv.debut()));
    }

    @Override
    public void envoyerModificationRdvConfirmee(Utilisateur destinataire, DetailsRdvCourriel ancien,
                                                DetailsRdvCourriel nouveau, PieceJointeCourriel agenda) {
        Locale langue = langue(destinataire.getLangue());
        String corps = corps("rdv-modifie", langue, nouveau.numero(),
                jour(ancien.debut(), langue, "EEEE d MMMM yyyy"), heure(ancien.debut()),
                jour(nouveau.debut(), langue, "EEEE d MMMM yyyy"), heure(nouveau.debut()));
        if (agenda != null) {
            corps = corps + "\n" + texte("courriel.rdv-confirme.agenda", langue);
        }
        partir("rdv-modifie", destinataire.getEmail(), destinataire.nomComplet(),
                destinataire.getPrenom(), langue, corps, agenda);
    }

    @Override
    public void envoyerModificationRdvEnAttente(Utilisateur destinataire, DetailsRdvCourriel ancien,
                                                DetailsRdvCourriel nouveau, ZonedDateTime echeance) {
        Locale langue = langue(destinataire.getLangue());
        auCompte("rdv-modifie-en-attente", destinataire, null, nouveau.numero(),
                jour(ancien.debut(), langue, "EEEE d MMMM yyyy"), heure(ancien.debut()),
                jour(nouveau.debut(), langue, "EEEE d MMMM yyyy"), heure(nouveau.debut()),
                jour(echeance, langue, "EEEE d MMMM yyyy"), heure(echeance));
    }

    @Override
    public void envoyerModificationParLeGarage(Utilisateur destinataire, DetailsRdvCourriel ancien,
                                               DetailsRdvCourriel nouveau, String motif, String telephone,
                                               PieceJointeCourriel agenda) {
        Locale langue = langue(destinataire.getLangue());
        String corps = corps("rdv-deplace-garage", langue, nouveau.numero(),
                jour(ancien.debut(), langue, "EEEE d MMMM yyyy"), heure(ancien.debut()),
                jour(nouveau.debut(), langue, "EEEE d MMMM yyyy"), heure(nouveau.debut()),
                motif, telephone == null ? "" : telephone);
        if (agenda != null) {
            corps = corps + "\n" + texte("courriel.rdv-confirme.agenda", langue);
        }
        partir("rdv-deplace-garage", destinataire.getEmail(), destinataire.nomComplet(),
                destinataire.getPrenom(), langue, corps, agenda);
    }

    @Override
    public void envoyerAvisModificationAuGarage(DetailsRdvCourriel ancien, DetailsRdvCourriel nouveau,
                                                String membre, boolean confirme, String cheminDetail) {
        Locale langue = Locale.FRENCH;
        auGarage(confirme ? "rdv-modifie-garage-confirme" : "rdv-modifie-garage", nouveau.numero(), langue,
                nouveau.numero(), jour(ancien.debut(), langue, "EEEE d MMMM yyyy"), heure(ancien.debut()),
                jour(nouveau.debut(), langue, "EEEE d MMMM yyyy"), heure(nouveau.debut()),
                membre, liens.vers(cheminDetail));
    }

    @Override
    public void envoyerDemandeValidationDepassement(Utilisateur destinataire,
                                                    DetailsDepassementCourriel depassement) {
        auCompte("depassement", destinataire, null, depassement.numeroIntervention(),
                depassement.vehicule(),
                depassement.montantInitial(), depassement.montantPropose(),
                depassement.montantAPayer(),
                String.join("\n", depassement.lignesEnAttente()),
                liens.vers(depassement.cheminValidation()));
    }

    @Override
    public void envoyerInterventionTerminee(DetailsInterventionTerminee details) {
        Locale langue = langue(details.langue());
        partir("intervention-terminee", details.adresseEmail(), details.prenom(),
                details.prenom(), langue, corps("intervention-terminee", langue,
                        details.numeroIntervention(), details.libelleVehicule(),
                        details.immatriculation(), liens.vers(details.cheminDepotAvis())), null);
    }

    // --- vente ----------------------------------------------------------------------

    /**
     * Confirmation du contrat (art. VI.46, § 7, CDE) : les informations de l'art. VI.45,
     * § 1er, qui n'ont pas été remises sur un support durable avant la conclusion. Le
     * courriel nomme le vendeur, la commande, le retrait, la rétractation, la garantie et
     * les recours. Les conditions, le délai et les modalités de la rétractation (8° à 10°)
     * sont dans l'article 7 des CGV jointes, qui reprend l'annexe 1 du livre VI (VI.45,
     * § 3).
     */
    @Override
    public void envoyerConfirmationCommande(DetailsConfirmationCommande details) {
        Locale langue = langue(details.langue());
        ZoneId zone = fuseau.zone();
        String cle = "courriel.confirmation-commande.";
        List<String> blocs = new ArrayList<>();
        blocs.add(corps("confirmation-commande", langue, details.numeroCommande(),
                details.totalTvac(), jour(details.conclueLe().atZone(zone), langue, "d MMMM yyyy"),
                jour(details.payeeLe().atZone(zone), langue, "d MMMM yyyy")));

        blocs.add(texte(cle + "vendeur", langue) + "\n" + String.join("\n", identite.lignes(langue)));

        List<String> commande = new ArrayList<>();
        commande.add(texte(cle + "commande", langue));
        for (DetailsConfirmationCommande.LigneCommandee ligne : details.lignes()) {
            commande.add(message(cle + "ligne", langue, ligne.libelle(),
                    String.valueOf(ligne.quantite()), ligne.totalTvac(), ligne.tauxTva()));
        }
        commande.add(message(cle + "total-htva", langue, details.totalHtva()));
        commande.add(message(cle + "total-tva", langue, details.totalTva()));
        commande.add(message(cle + "total-tvac", langue, details.totalTvac()));
        commande.add(texte(cle + "paiement", langue));
        blocs.add(String.join("\n", commande));

        List<String> retrait = new ArrayList<>();
        retrait.add(texte(cle + "retrait", langue));
        if (details.pieces()) {
            retrait.add(texte(cle + "retrait.pieces", langue));
        }
        if (details.prestations()) {
            retrait.add(texte(cle + "retrait.prestations", langue));
        }
        blocs.add(String.join("\n", retrait));

        // La fonction en ligne est désignée par les libellés de l'écran, comme dans
        // l'article 7 des CGV : un libellé renommé se renomme ici en même temps.
        String page = texte("commandes.nav", langue);
        String lien = texte("commandes.annulation.demander", langue);
        String retractation = texte(cle + "retractation", langue) + "\n" + (details.versionCgv() != null
                ? message(cle + "retractation.corps", langue, details.versionCgv(), page, lien)
                : message(cle + "retractation.sans-cgv", langue, page, lien));
        if (details.renonciation() != null) {
            DetailsConfirmationCommande.RenonciationConfirmee renonciation = details.renonciation();
            retractation += "\n" + message(cle + "renonciation", langue,
                    jour(renonciation.le().atZone(zone), langue, "d MMMM yyyy"),
                    renonciation.version(), renonciation.libelle());
        }
        blocs.add(retractation);

        if (details.pieces()) {
            // Le lien que porte le code QR de la notice jointe : un PDF n'a pas de lien cliquable.
            blocs.add(texte(cle + "garantie", langue) + "\n" + texte(cle + "garantie.corps", langue)
                    + "\n" + message(cle + "garantie.notice", langue,
                            texte("garantie-legale.youreurope.url", langue)));
        }
        blocs.add(texte(cle + "reclamations", langue) + "\n" + texte(cle + "reclamations.corps", langue));
        String jointes = details.versionCgv() != null ? "pieces-jointes" : "pieces-jointes.formulaire";
        blocs.add(texte(cle + jointes + (details.pieces() ? ".avec-notice" : ""), langue));

        partirAvecPieces("confirmation-commande", details.adresseEmail(), details.prenom(),
                details.prenom(), langue, String.join("\n\n", blocs), details.piecesJointes());
    }

    @Override
    public void envoyerAvisCommandeAuGarage(DetailsCommandeGarage details) {
        Locale langue = Locale.FRENCH;
        String contenu = texte(details.prestations()
                ? "courriel.commande-a-preparer.prestations"
                : "courriel.commande-a-preparer.pieces", langue);
        String rupture = details.ruptureAHonorer()
                ? texte("courriel.commande-a-preparer.rupture", langue)
                : "";
        auGarage("commande-a-preparer", details.numeroCommande(), langue,
                details.numeroCommande(), details.montantTvac(), details.membre(), contenu, rupture);
    }

    @Override
    public void envoyerAccuseRetractation(DetailsAccuseRetractationCourriel details) {
        Locale langue = langue(details.langue());
        ZonedDateTime soumission = details.soumiseLe().atZone(fuseau.zone());
        String motif = details.motif() == null || details.motif().isBlank()
                ? texte("courriel.retractation-recue.sans-motif", langue)
                : details.motif();
        partir("retractation-recue", details.adresseEmail(), details.prenom(), details.prenom(),
                langue, corps("retractation-recue", langue, details.numeroCommande(),
                        jour(soumission, langue, "d MMMM yyyy"), heure(soumission),
                        details.nomMembre(), details.adresseEmail(), motif), null);
    }

    @Override
    public void envoyerDecisionRetractation(DetailsRetractationCourriel details) {
        Locale langue = langue(details.langue());
        String corps = details.acceptee()
                ? corps("retractation-acceptee", langue, details.numeroCommande(),
                        details.montantTvac(), details.numeroAvoir())
                : corps("retractation-refusee", langue, details.numeroCommande(),
                        details.motifRefus());
        partir(details.acceptee() ? "retractation-acceptee" : "retractation-refusee",
                details.adresseEmail(), details.prenom(), details.prenom(), langue, corps, null);
    }

    @Override
    public void envoyerAvisPaiementApresAnnulation(Utilisateur destinataire, String numeroCommande,
                                                   String montantTvac) {
        auCompte("paiement-apres-annulation", destinataire, null, numeroCommande, montantTvac);
    }

    @Override
    public void envoyerAvisRemboursementApresAnnulation(Utilisateur destinataire,
                                                        String numeroCommande, String montantTvac) {
        auCompte("remboursement-apres-annulation", destinataire, null, numeroCommande, montantTvac);
    }

    @Override
    public void envoyerAvisPaiementARembourserAuGarage(String numeroCommande, String montantTvac,
                                                       String membre) {
        auGarage("paiement-a-rembourser", numeroCommande, Locale.FRENCH,
                numeroCommande, montantTvac, membre);
    }

    @Override
    public void envoyerAvisReponseGarage(Utilisateur destinataire, String sujet, UUID fil) {
        auCompte("reponse-garage", destinataire, null, sujet, liens.vers("/mes-messages/" + fil));
    }

    @Override
    public void envoyerAvisMessageAuGarage(String sujet, String membre, UUID fil) {
        auGarage("message-au-garage", fil.toString(), Locale.FRENCH, sujet, membre,
                liens.vers("/admin/messages/" + fil));
    }

    // --- rédaction ------------------------------------------------------------------

    /** Courriel adressé au compte lui-même : adresse, nom et langue viennent de la ligne. */
    private void auCompte(String type, Utilisateur destinataire, PieceJointeCourriel piece,
                          Object... arguments) {
        Locale langue = langue(destinataire.getLangue());
        partir(type, destinataire.getEmail(), destinataire.nomComplet(),
                destinataire.getPrenom(), langue, corps(type, langue, arguments), piece);
    }

    /**
     * Courriel adressé à l'atelier, dans la langue par défaut de l'application. Sans
     * adresse d'atelier, seules les notifications du back-office préviennent le garage :
     * le contrôle d'identité l'exige sous prod, pas sous les autres profils.
     */
    private void auGarage(String type, String numero, Locale langue, Object... arguments) {
        String adresse = garage.courriel();
        if (adresse == null || adresse.isBlank()) {
            log.warn("Courriel {} ({}) non envoyé : aucune adresse d'atelier.", type, numero);
            return;
        }
        envoyer(type, adresse, garage.enseigne(), garage.enseigne(), langue,
                corps(type, langue, arguments), List.of(), false);
    }

    private void partir(String type, String adresse, String nom, String prenom, Locale langue,
                        String corps, PieceJointeCourriel piece) {
        partirAvecPieces(type, adresse, nom, prenom, langue, corps,
                piece == null ? List.of() : List.of(piece));
    }

    private void partirAvecPieces(String type, String adresse, String nom, String prenom,
                                  Locale langue, String corps, List<PieceJointeCourriel> pieces) {
        envoyer(type, adresse, nom, prenom, langue, corps, pieces, true);
    }

    /**
     * @param coordonnees vrai pour un courriel adressé à un membre : le message est envoyé
     *                    d'une adresse qui ne se lit pas, son pied dit donc où joindre le
     *                    garage (F27). L'atelier n'a pas besoin de ses propres coordonnées.
     */
    private void envoyer(String type, String adresse, String nom, String prenom, Locale langue,
                         String corps, List<PieceJointeCourriel> pieces, boolean coordonnees) {
        String texte = messages.getMessage("courriel.salutation", new Object[]{prenom}, langue)
                + "\n\n" + corps + "\n\n"
                + messages.getMessage("courriel.signature", new Object[]{garage.enseigne()}, langue)
                + (coordonnees ? "\n\n" + coordonneesDuGarage(langue) : "");
        Courriel courriel = new Courriel(type, adresse, nom,
                texte("courriel." + type + ".sujet", langue), texte, pieces);
        if (HORS_DE_LA_REQUETE.contains(type)) {
            envoi.planifierHorsRequete(courriel);
        } else {
            envoi.planifier(courriel);
        }
    }

    /** Enseigne, adresse, téléphone et adresse de courriel, une mention absente étant omise. */
    private String coordonneesDuGarage(Locale langue) {
        IdentiteGarage local = garage.pour(langue);
        List<String> lignes = new ArrayList<>();
        lignes.add(texte("courriel.pied.contact", langue));
        lignes.add(local.enseigne());
        lignes.add(local.adresseLisible());
        if (local.telephone() != null && !local.telephone().isBlank()) {
            lignes.add(messages.getMessage("courriel.pied.telephone", new Object[]{local.telephone()}, langue));
        }
        if (local.courriel() != null && !local.courriel().isBlank()) {
            lignes.add(messages.getMessage("courriel.pied.courriel", new Object[]{local.courriel()}, langue));
        }
        return String.join("\n", lignes);
    }

    /**
     * Sans argument, le message est lu tel quel, apostrophes simples comprises. Avec des
     * arguments, il passe par {@code MessageFormat} et ses apostrophes sont doublées.
     */
    private String corps(String type, Locale langue, Object... arguments) {
        String cle = "courriel." + type + ".corps";
        return arguments.length == 0
                ? texte(cle, langue)
                : messages.getMessage(cle, arguments, langue);
    }

    private String texte(String cle, Locale langue) {
        return messages.getMessage(cle, null, langue);
    }

    private String message(String cle, Locale langue, Object... arguments) {
        return messages.getMessage(cle, arguments, langue);
    }

    private static Locale langue(Langue langue) {
        return Locale.of(langue == null ? Langue.fr.name() : langue.name());
    }

    private static String jour(ZonedDateTime instant, Locale langue, String motif) {
        return DateTimeFormatter.ofPattern(motif, langue).format(instant);
    }

    private static String heure(ZonedDateTime instant) {
        return DateTimeFormatter.ofPattern("HH:mm").format(instant);
    }
}
