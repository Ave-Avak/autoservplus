package be.autoservplus.communication.service;

import be.autoservplus.identite.domain.Utilisateur;

import java.time.Instant;
import java.time.ZonedDateTime;

/**
 * Envoi des courriels transactionnels.
 *
 * <p>Le code metier depend de cette interface, jamais d un fournisseur particulier.
 * {@link CourrielSortant} redige le message dans la langue du destinataire et le fait
 * partir apres la validation de la transaction. Le transport est un reglage : un relais
 * SMTP, obligatoire sous {@code prod}, ou le journal sur les autres profils quand aucun
 * relais n est configure.</p>
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

    /**
     * Le garage a confirme la demande de rendez-vous du membre.
     *
     * @param agenda fichier iCalendar du rendez-vous (F38), ou {@code null} si sa
     *               production a echoue. Le courriel part <b>quand meme</b> dans ce
     *               cas : la confirmation est l information importante, le fichier
     *               n en est que le confort, et priver le membre de la premiere
     *               parce que le second manque serait un mauvais echange.
     */
    void envoyerConfirmationRdv(Utilisateur destinataire, DetailsRdvCourriel rdv,
                                PieceJointeCourriel agenda);

    /** Le garage a refuse la demande de rendez-vous, avec un motif obligatoire. */
    void envoyerRefusRdv(Utilisateur destinataire, DetailsRdvCourriel rdv, String motif);

    /** Le garage annule un rendez-vous deja confirme (panne, imprevu), motif obligatoire. */
    void envoyerAnnulationParLeGarage(Utilisateur destinataire, DetailsRdvCourriel rdv, String motif);

    /**
     * Accusé de réception d'une demande de rendez-vous. Dit si la demande est déjà
     * confirmée ou si elle attend la réponse du garage, qui répond sous
     * {@code delaiReponseHeures} heures.
     */
    void envoyerAccuseDemandeRdv(Utilisateur destinataire, DetailsRdvCourriel rdv, boolean confirme,
                                 int delaiReponseHeures);

    /** La demande est restée sans réponse jusqu'à son échéance : le créneau est libéré. */
    void envoyerExpirationRdv(Utilisateur destinataire, DetailsRdvCourriel rdv);

    /**
     * Relance au garage d'une demande toujours en attente à mi-délai, à l'adresse de
     * l'atelier et dans la langue par défaut de l'application.
     */
    void envoyerRelanceAuGarage(DetailsRdvCourriel rdv, String membre, ZonedDateTime echeance,
                                String cheminDetail);

    /**
     * Avis au garage d'une nouvelle demande, à l'adresse de l'atelier et dans la langue
     * par défaut de l'application. {@code cheminDetail} mène au détail du back-office.
     */
    void envoyerAvisNouvelleDemandeAuGarage(DetailsRdvCourriel rdv, String membre, boolean confirme,
                                            String cheminDetail);

    /** Rappel au membre avant un rendez-vous confirmé. */
    void envoyerRappelRdv(Utilisateur destinataire, DetailsRdvCourriel rdv);

    /** Confirmation au membre de l'annulation qu'il vient de faire. */
    void envoyerAnnulationParLeMembre(Utilisateur destinataire, DetailsRdvCourriel rdv);

    /**
     * Le garage a chiffre des travaux qui portent l intervention a plus de 10 % du
     * devis initial : RM-15 exige l accord expres du membre avant de poursuivre.
     * C est le canal dedie annonce par RM-16 : le statut percu reste « En cours »,
     * la demande passe par ce courriel et par l ecran de validation.
     */
    void envoyerDemandeValidationDepassement(Utilisateur destinataire,
                                             DetailsRdvCourriel rdv,
                                             DetailsDepassementCourriel depassement);

    /**
     * L intervention est terminee (F17) : le membre est invite a venir recuperer son
     * vehicule aux heures d ouverture, et a deposer un avis. Envoye apres commit de
     * la transition TERMINEE, jamais pendant : un courriel ne doit pas annoncer un
     * etat qui pourrait encore etre annule par un rollback.
     */
    void envoyerInterventionTerminee(DetailsInterventionTerminee details);

    /**
     * Le paiement de la commande est confirme (F14) : recu adresse au membre,
     * envoye apres commit du passage a PAYEE, jamais pendant la transaction.
     */
    void envoyerConfirmationPaiement(DetailsPaiementCourriel details);

    /**
     * Le garage a tranche une demande de retractation (F30, RM-23), en l acceptant
     * ou en la refusant. Envoye apres commit de la decision.
     *
     * <p>Le refus est notifie autant que l acceptation : le professionnel qui oppose
     * une exception au droit de retractation doit en informer le consommateur, et un
     * membre laisse sans reponse ne saurait pas que son dossier a ete traite.</p>
     *
     * <p>Le message <b>lie</b> vers le telechargement de la note de credit, il n en
     * attache pas le binaire, meme choix que pour la facture : un PDF en piece
     * jointe echappe a l authentification, se retrouve dans les sauvegardes de
     * messagerie du destinataire, et alourdit un envoi que le membre relira peut-etre
     * jamais.</p>
     */
    void envoyerDecisionRetractation(DetailsRetractationCourriel details);

    /**
     * Lien de confirmation envoye a la NOUVELLE adresse (CdC 5.2.2). Tant qu il n est
     * pas suivi, le compte reste joignable a l ancienne : une saisie erronee coute un
     * courriel perdu, jamais l acces au compte.
     */
    void envoyerConfirmationNouvelleAdresse(DetailsChangementEmailCourriel details,
                                            String lienConfirmation);

    /**
     * Avis envoye a l ANCIENNE adresse, sans lien d action.
     *
     * <p>C est le seul signal qu une prise de controle laisse au titulaire legitime :
     * qui detourne un compte commence par en deplacer l adresse, apres quoi la
     * reinitialisation de mot de passe ne revient plus jamais a la victime. L avis part
     * des la DEMANDE et non a la bascule, averti apres coup, le titulaire n aurait
     * plus aucun moyen de reprendre la main.</p>
     *
     * <p>Aucun bouton d annulation n y figure : un lien d annulation dans un courriel
     * est lui-meme une action non authentifiee, donc une prise supplementaire. Le
     * message invite a se connecter et a changer de mot de passe, ce qui suppose de
     * prouver son identite.</p>
     */
    void envoyerAvisChangementAdresse(DetailsChangementEmailCourriel details);

    /**
     * Le compte du membre a ete supprime a sa demande (F23, art. 17 RGPD). Envoye
     * apres commit de l anonymisation.
     *
     * <p>Adresse et prenom viennent du <b>parametre</b> et non d un rechargement :
     * apres anonymisation la ligne ne porte plus qu un jeton non routable. Le service
     * les capture avant l ecrasement et les fait voyager jusqu ici.</p>
     *
     * <p>Le message rappelle ce qui a ete conserve et pourquoi : les documents
     * comptables, dix ans, article 60 du Code de la TVA. Une confirmation qui dirait
     * seulement « tout est efface » serait fausse, et la personne decouvrirait la
     * conservation au pire moment.</p>
     */
    void envoyerConfirmationSuppressionCompte(DetailsSuppressionCompteCourriel details);

    /**
     * Lien de definition du mot de passe d un administrateur cree au back-office. Le
     * courriel ne porte JAMAIS de mot de passe : le compte nait sans secret utilisable,
     * et c est le titulaire qui choisit le sien en suivant le lien.
     */
    void envoyerLienDefinitionMotDePasse(Utilisateur destinataire, String lien);

    /**
     * Le second facteur a echoue cinq fois et le compte est verrouille. Un tel echec
     * suppose le mot de passe deja connu : le titulaire doit le savoir, pour le changer.
     */
    void envoyerAvisVerrouillageSecondFacteur(Utilisateur destinataire, Instant jusqua);

    /**
     * Cinq mots de passe incorrects ont verrouille le compte. Contrairement au second
     * facteur, rien ne prouve que le mot de passe soit connu : l avis informe, et indique
     * la reinitialisation qui leve le verrou.
     */
    void envoyerAvisVerrouillageConnexion(Utilisateur destinataire, Instant jusqua);

    /** Un super-administrateur a reinitialise le second facteur du titulaire. */
    void envoyerAvisReinitialisationSecondFacteur(Utilisateur destinataire);

    /**
     * Le mot de passe du titulaire vient de changer. L'avis ne porte ni lien ni jeton :
     * il prévient celui qui n'est pas à l'origine du changement, pour qu'il réagisse, et
     * n'ouvre rien à qui lirait sa boîte de réception. Rédigé dans la langue du compte.
     */
    void envoyerAvisMotDePasseModifie(Utilisateur destinataire, OrigineMotDePasse origine);
}