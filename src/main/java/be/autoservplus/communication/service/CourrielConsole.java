package be.autoservplus.communication.service;

import be.autoservplus.identite.domain.Utilisateur;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * SEULE implementation de {@link ServiceCourriel} : le contenu du courriel est
 * journalise, jamais expedie. Le lien reste ainsi accessible sans dependre d un
 * service externe.
 *
 * <p><b>Perimetre assume, pas dette masquee.</b> L integration d un fournisseur
 * d envoi reel (Brevo) n est pas au perimetre : elle demanderait un compte, un
 * domaine expediteur verifie (SPF, DKIM), une gestion des rebonds et des
 * desabonnements, et ces quatre elements ne s eprouvent pas sans une adresse
 * d envoi legitime. Un envoi mal configure part en indesirable, ce qui est pire
 * qu un envoi assume comme absent : le membre ne recoit rien ET personne ne le
 * sait. La frontiere {@link ServiceCourriel} est en place et c est elle qui compte —
 * brancher Brevo ne touchera que ce fichier.</p>
 *
 * <p><b>Le profil ne conditionne plus cette classe.</b> Elle portait
 * {@code @Profile("!prod")} alors qu aucune autre implementation n existe : demarrer
 * en profil {@code prod} echouait donc au cablage, faute de {@code ServiceCourriel} a
 * injecter. Un stub assume doit etre disponible partout ou il tient le role, sans
 * quoi il n en tient aucun. Meme raisonnement que pour le prestataire de paiement :
 * un repli qui n existe pas dans l environnement ou l on en a besoin n est pas un
 * repli.</p>
 *
 * <p>Chaque bloc journalise s annonce NON EXPEDIE, et un avertissement au demarrage
 * le rappelle : lire « courriel simule » dans un journal pouvait se comprendre comme
 * « simule ici, expedie ailleurs ».</p>
 */
@Service
public class CourrielConsole implements ServiceCourriel {

    private static final Logger JOURNAL = LoggerFactory.getLogger(CourrielConsole.class);

    /**
     * Avertissement au demarrage. En {@code WARN} comme pour le paiement simule : un
     * exploitant qui croit que ses membres recoivent leurs courriels d activation ne
     * s en apercevrait qu au premier appel d un client bloque a l inscription.
     */
    @PostConstruct
    void annoncerLAbsenceDEnvoi() {
        JOURNAL.warn("Aucun fournisseur d envoi de courriel n est configure : les "
                + "messages sont JOURNALISES et jamais expedies. Les liens d activation "
                + "et de reinitialisation se lisent dans ce journal.");
    }

    @Override
    public void envoyerVerificationAdresse(Utilisateur destinataire, String lienVerification) {
        JOURNAL.info("""

                ---------- COURRIEL NON EXPEDIE (demonstration) : verification d adresse ----------
                Destinataire : {} <{}>
                Lien         : {}
                -------------------------------------------------------------
                """, destinataire.nomComplet(), destinataire.getEmail(), lienVerification);
    }

    @Override
    public void envoyerReinitialisationMotDePasse(Utilisateur destinataire, String lien) {
        JOURNAL.info("""

                ---------- COURRIEL NON EXPEDIE (demonstration) : mot de passe oublie ----------
                Destinataire : {} <{}>
                Lien         : {}
                ----------------------------------------------------------
                """, destinataire.nomComplet(), destinataire.getEmail(), lien);
    }
    @Override
    public void envoyerRappelVerification(Utilisateur destinataire, String lienVerification) {
        JOURNAL.info("""

                ---------- COURRIEL NON EXPEDIE (demonstration) : compte jamais active ----------
                Destinataire : {} <{}>
                Lien         : {}
                -----------------------------------------------------------
                """, destinataire.nomComplet(), destinataire.getEmail(), lienVerification);
    }

    @Override
    public void envoyerConfirmationRdv(Utilisateur destinataire, DetailsRdvCourriel rdv,
                                       PieceJointeCourriel agenda) {
        JOURNAL.info("""

                ---------- COURRIEL NON EXPEDIE (demonstration) : confirmation de rendez-vous ----------
                Destinataire : {} <{}>
                Rendez-vous  : {} le {} a {}
                Piece jointe : {}
                ------------------------------------------------------------------
                """, destinataire.nomComplet(), destinataire.getEmail(),
                rdv.numero(), rdv.jourLisible(), rdv.heureLisible(),
                descriptionPieceJointe(agenda));
    }

    /**
     * La console ne peut pas joindre un fichier : elle en annonce le nom et la
     * taille. C est ce qui rend la piece jointe <b>observable</b> en developpement —
     * sans cette ligne, un fichier vide ou absent passerait inapercu jusqu a la mise
     * en production, ou plus personne ne regarde les journaux.
     */
    private static String descriptionPieceJointe(PieceJointeCourriel piece) {
        if (piece == null) {
            return "aucune";
        }
        return "%s (%s, %d octets)".formatted(piece.nomFichier(), piece.typeMime(), piece.tailleOctets());
    }

    @Override
    public void envoyerRefusRdv(Utilisateur destinataire, DetailsRdvCourriel rdv, String motif) {
        JOURNAL.info("""

                ---------- COURRIEL NON EXPEDIE (demonstration) : refus de rendez-vous ----------
                Destinataire : {} <{}>
                Rendez-vous  : {} le {} a {}
                Motif        : {}
                -----------------------------------------------------------
                """, destinataire.nomComplet(), destinataire.getEmail(),
                rdv.numero(), rdv.jourLisible(), rdv.heureLisible(), motif);
    }

    @Override
    public void envoyerAnnulationParLeGarage(Utilisateur destinataire, DetailsRdvCourriel rdv, String motif) {
        JOURNAL.info("""

                ---------- COURRIEL NON EXPEDIE (demonstration) : annulation par le garage ----------
                Destinataire : {} <{}>
                Rendez-vous  : {} le {} a {}
                Motif        : {}
                ----------------------------------------------------------------
                """, destinataire.nomComplet(), destinataire.getEmail(),
                rdv.numero(), rdv.jourLisible(), rdv.heureLisible(), motif);
    }

    @Override
    public void envoyerDemandeValidationDepassement(Utilisateur destinataire,
                                                    DetailsRdvCourriel rdv,
                                                    DetailsDepassementCourriel depassement) {
        JOURNAL.info("""

                ---------- COURRIEL NON EXPEDIE (demonstration) : accord requis sur un depassement de devis ----------
                Destinataire  : {} <{}>
                Intervention  : {} (rendez-vous {} du {})
                Devis initial : {}
                Total propose : {}
                Travaux soumis a votre accord :
                {}
                Repondre ici  : {}
                --------------------------------------------------------------------------------
                """, destinataire.nomComplet(), destinataire.getEmail(),
                depassement.numeroIntervention(), rdv.numero(), rdv.jourLisible(),
                depassement.montantInitial(), depassement.montantPropose(),
                String.join("\n", depassement.lignesEnAttente()),
                depassement.lienValidation());
    }

    @Override
    public void envoyerInterventionTerminee(DetailsInterventionTerminee details) {
        JOURNAL.info("""

                ---------- COURRIEL NON EXPEDIE (demonstration) : intervention terminee ----------
                Destinataire : {} <{}>
                Intervention : {}
                Bonjour {}, les travaux sur votre {} ({}) sont termines.
                Vous pouvez venir recuperer votre vehicule aux heures d ouverture
                du garage. Vous pouvez aussi deposer un avis sur votre experience :
                {}
                -------------------------------------------------------------
                """, details.prenom(), details.adresseEmail(),
                details.numeroIntervention(), details.prenom(),
                details.libelleVehicule(), details.immatriculation(),
                details.cheminDepotAvis());
    }

    @Override
    public void envoyerConfirmationPaiement(DetailsPaiementCourriel details) {
        JOURNAL.info("""

                ---------- COURRIEL NON EXPEDIE (demonstration) : paiement confirme ----------
                Destinataire : {} <{}>
                Commande     : {}
                Bonjour {}, votre paiement de {} est bien recu.
                Le garage prepare votre commande ; vous serez prevenu(e)
                lorsqu'elle sera prete.
                ---------------------------------------------------------
                """, details.prenom(), details.adresseEmail(),
                details.numeroCommande(), details.prenom(), details.montantTvac());
    }

    @Override
    public void envoyerDecisionRetractation(DetailsRetractationCourriel details) {
        if (details.acceptee()) {
            JOURNAL.info("""

                    ---------- COURRIEL NON EXPEDIE (demonstration) : retractation acceptee ----------
                    Destinataire : {} <{}>
                    Commande     : {}
                    Bonjour {}, votre demande d'annulation est acceptee.
                    Un remboursement de {} a ete demande a notre prestataire
                    de paiement ; il apparaitra sur le compte ayant servi a payer.
                    Note de credit : {} — telechargeable depuis « Mes commandes ».
                    -------------------------------------------------------------
                    """, details.prenom(), details.adresseEmail(),
                    details.numeroCommande(), details.prenom(),
                    details.montantTvac(), details.numeroAvoir());
            return;
        }
        JOURNAL.info("""

                ---------- COURRIEL NON EXPEDIE (demonstration) : retractation refusee ----------
                Destinataire : {} <{}>
                Commande     : {}
                Bonjour {}, votre demande d'annulation n'a pas pu etre acceptee.
                Motif : {}
                -----------------------------------------------------------
                """, details.prenom(), details.adresseEmail(),
                details.numeroCommande(), details.prenom(), details.motifRefus());
    }

    @Override
    public void envoyerConfirmationSuppressionCompte(DetailsSuppressionCompteCourriel details) {
        JOURNAL.info("""

                ---------- COURRIEL NON EXPEDIE (demonstration) : compte supprime ----------
                Destinataire : {} <{}>
                Bonjour {}, votre compte AutoServ+ a bien ete supprime.
                Vos donnees personnelles ont ete effacees et votre acces revoque.
                Vos factures restent conservees dix ans, comme la loi l impose
                (article 60 du Code de la TVA) ; elles ne vous identifient plus
                dans l application.
                ------------------------------------------------------
                """, details.prenom(), details.adresseEmail(), details.prenom());
    }
}