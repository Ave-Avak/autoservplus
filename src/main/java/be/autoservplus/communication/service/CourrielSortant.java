package be.autoservplus.communication.service;

import be.autoservplus.config.IdentiteGarage;
import be.autoservplus.identite.domain.Langue;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.reservation.repository.ParametreAtelierRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

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

    private final MessageSource messages;
    private final EnvoiApresCommit envoi;
    private final IdentiteGarage garage;
    private final ParametreAtelierRepository parametres;
    private final LiensCourriel liens;

    public CourrielSortant(MessageSource messages, EnvoiApresCommit envoi,
                           IdentiteGarage garage, ParametreAtelierRepository parametres,
                           LiensCourriel liens) {
        this.liens = liens;
        this.messages = messages;
        this.envoi = envoi;
        this.garage = garage;
        this.parametres = parametres;
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
        ZonedDateTime fin = jusqua.atZone(parametres.courants().zone());
        auCompte("verrouillage-second-facteur", destinataire, null,
                jour(fin, langue, "d MMMM yyyy"), heure(fin));
    }

    @Override
    public void envoyerAvisVerrouillageConnexion(Utilisateur destinataire, Instant jusqua) {
        Locale langue = langue(destinataire.getLangue());
        ZonedDateTime fin = jusqua.atZone(parametres.courants().zone());
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
                                        boolean confirme, int delaiReponseHeures) {
        Locale langue = langue(destinataire.getLangue());
        auCompte(confirme ? "rdv-recu-confirme" : "rdv-recu", destinataire, null, rdv.numero(),
                jour(rdv.debut(), langue, "EEEE d MMMM yyyy"), heure(rdv.debut()), delaiReponseHeures);
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
    public void envoyerDemandeValidationDepassement(Utilisateur destinataire,
                                                    DetailsRdvCourriel rdv,
                                                    DetailsDepassementCourriel depassement) {
        Locale langue = langue(destinataire.getLangue());
        auCompte("depassement", destinataire, null, depassement.numeroIntervention(),
                rdv.numero(), jour(rdv.debut(), langue, "EEEE d MMMM yyyy"),
                depassement.montantInitial(), depassement.montantPropose(),
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

    @Override
    public void envoyerConfirmationPaiement(DetailsPaiementCourriel details) {
        Locale langue = langue(details.langue());
        partir("paiement-confirme", details.adresseEmail(), details.prenom(), details.prenom(),
                langue, corps("paiement-confirme", langue, details.numeroCommande(),
                        details.montantTvac()), null);
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
            log.warn("Courriel {} du rendez-vous {} non envoyé : aucune adresse d'atelier.", type, numero);
            return;
        }
        partir(type, adresse, garage.enseigne(), garage.enseigne(), langue,
                corps(type, langue, arguments), null);
    }

    private void partir(String type, String adresse, String nom, String prenom, Locale langue,
                        String corps, PieceJointeCourriel piece) {
        String texte = messages.getMessage("courriel.salutation", new Object[]{prenom}, langue)
                + "\n\n" + corps + "\n\n"
                + messages.getMessage("courriel.signature", new Object[]{garage.enseigne()}, langue);
        envoi.planifier(new Courriel(type, adresse, nom,
                texte("courriel." + type + ".sujet", langue), texte, piece));
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
