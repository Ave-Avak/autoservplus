package be.autoservplus.communication.service;

import be.autoservplus.identite.domain.Utilisateur;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Implementation de developpement : le contenu du courriel est journalise plutot
 * qu envoye. Le lien reste ainsi accessible sans dependre d un service externe.
 */
@Service
@Profile("!prod")
public class CourrielConsole implements ServiceCourriel {

    private static final Logger JOURNAL = LoggerFactory.getLogger(CourrielConsole.class);

    @Override
    public void envoyerVerificationAdresse(Utilisateur destinataire, String lienVerification) {
        JOURNAL.info("""

                ---------- COURRIEL SIMULE : verification d adresse ----------
                Destinataire : {} <{}>
                Lien         : {}
                -------------------------------------------------------------
                """, destinataire.nomComplet(), destinataire.getEmail(), lienVerification);
    }

    @Override
    public void envoyerReinitialisationMotDePasse(Utilisateur destinataire, String lien) {
        JOURNAL.info("""

                ---------- COURRIEL SIMULE : mot de passe oublie ----------
                Destinataire : {} <{}>
                Lien         : {}
                ----------------------------------------------------------
                """, destinataire.nomComplet(), destinataire.getEmail(), lien);
    }
    @Override
    public void envoyerRappelVerification(Utilisateur destinataire, String lienVerification) {
        JOURNAL.info("""

                ---------- COURRIEL SIMULE : compte jamais active ----------
                Destinataire : {} <{}>
                Lien         : {}
                -----------------------------------------------------------
                """, destinataire.nomComplet(), destinataire.getEmail(), lienVerification);
    }

    @Override
    public void envoyerConfirmationRdv(Utilisateur destinataire, DetailsRdvCourriel rdv) {
        JOURNAL.info("""

                ---------- COURRIEL SIMULE : confirmation de rendez-vous ----------
                Destinataire : {} <{}>
                Rendez-vous  : {} le {} a {}
                ------------------------------------------------------------------
                """, destinataire.nomComplet(), destinataire.getEmail(),
                rdv.numero(), rdv.jourLisible(), rdv.heureLisible());
    }

    @Override
    public void envoyerRefusRdv(Utilisateur destinataire, DetailsRdvCourriel rdv, String motif) {
        JOURNAL.info("""

                ---------- COURRIEL SIMULE : refus de rendez-vous ----------
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

                ---------- COURRIEL SIMULE : annulation par le garage ----------
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

                ---------- COURRIEL SIMULE : accord requis sur un depassement de devis ----------
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

                ---------- COURRIEL SIMULE : intervention terminee ----------
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

                ---------- COURRIEL SIMULE : paiement confirme ----------
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

                    ---------- COURRIEL SIMULE : retractation acceptee ----------
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

                ---------- COURRIEL SIMULE : retractation refusee ----------
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

                ---------- COURRIEL SIMULE : compte supprime ----------
                Destinataire : {} <{}>
                Bonjour {}, votre compte AutoServ+ a bien ete supprime.
                Vos donnees personnelles ont ete effacees et votre acces revoque.
                Vos factures restent conservees sept ans, comme la loi l impose
                (article 60 du Code de la TVA) ; elles ne vous identifient plus
                dans l application.
                ------------------------------------------------------
                """, details.prenom(), details.adresseEmail(), details.prenom());
    }
}