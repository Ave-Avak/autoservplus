package be.autoservplus.communication.service;

import be.autoservplus.identite.domain.Utilisateur;

/**
 * Envoi des courriels transactionnels.
 *
 * <p>Le code metier depend de cette interface, jamais d un fournisseur particulier.
 * L implementation active est choisie par le profil Spring : journalisation en console
 * en developpement, envoi reel en production. Ce decouplage permet de tester les
 * services sans emettre le moindre message.</p>
 */
public interface ServiceCourriel {

    void envoyerVerificationAdresse(Utilisateur destinataire, String lienVerification);

    void envoyerReinitialisationMotDePasse(Utilisateur destinataire, String lienReinitialisation);

    /**
     * Envoye lorsqu une reinitialisation est demandee pour un compte jamais active :
     * l utilisateur croit avoir oublie son mot de passe alors qu il n a pas termine
     * son inscription.
     */
    void envoyerRappelVerification(Utilisateur destinataire, String lienVerification);

    /** Le garage a confirme la demande de rendez-vous du membre. */
    void envoyerConfirmationRdv(Utilisateur destinataire, DetailsRdvCourriel rdv);

    /** Le garage a refuse la demande de rendez-vous, avec un motif obligatoire. */
    void envoyerRefusRdv(Utilisateur destinataire, DetailsRdvCourriel rdv, String motif);

    /** Le garage annule un rendez-vous deja confirme (panne, imprevu), motif obligatoire. */
    void envoyerAnnulationParLeGarage(Utilisateur destinataire, DetailsRdvCourriel rdv, String motif);

    /**
     * Le garage a chiffre des travaux qui portent l intervention a plus de 10 % du
     * devis initial : RM-15 exige l accord expres du membre avant de poursuivre.
     * C est le canal dedie annonce par RM-16 — le statut percu reste « En cours »,
     * la demande passe par ce courriel et par l ecran de validation.
     */
    void envoyerDemandeValidationDepassement(Utilisateur destinataire,
                                             DetailsRdvCourriel rdv,
                                             DetailsDepassementCourriel depassement);

    /**
     * L intervention est terminee (F17) : le membre est invite a venir recuperer son
     * vehicule aux heures d ouverture, et a deposer un avis. Envoye apres commit de
     * la transition TERMINEE, jamais pendant — un courriel ne doit pas annoncer un
     * etat qui pourrait encore etre annule par un rollback.
     */
    void envoyerInterventionTerminee(DetailsInterventionTerminee details);
}