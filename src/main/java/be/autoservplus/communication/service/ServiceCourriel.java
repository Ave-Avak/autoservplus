package be.autoservplus.communication.service;

import be.autoservplus.identite.domain.ChampCompte;
import be.autoservplus.identite.domain.Utilisateur;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

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
    /**
     * Avertit le titulaire qu'une inscription a été tentée avec son adresse, sans jeton :
     * liens vers la connexion et vers la demande d'un nouveau mot de passe.
     */
    void envoyerAvisTentativeInscription(Utilisateur destinataire);

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
     * Accusé de réception d'une demande qui attend la réponse du garage, avec son
     * échéance de réponse enregistrée, dans le fuseau de l'atelier.
     */
    void envoyerAccuseDemandeRdv(Utilisateur destinataire, DetailsRdvCourriel rdv, ZonedDateTime echeance);

    /**
     * Accusé d'un rendez-vous confirmé d'office. Porte le fichier d'agenda comme toute
     * confirmation, sauf si sa rédaction a échoué ({@code agenda} nul).
     */
    void envoyerAccuseRdvConfirme(Utilisateur destinataire, DetailsRdvCourriel rdv,
                                  PieceJointeCourriel agenda);

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
     * Le membre a déplacé son rendez-vous, confirmé d'office sur le nouveau créneau.
     * Porte le fichier d'agenda, sauf si sa rédaction a échoué ({@code agenda} nul).
     */
    void envoyerModificationRdvConfirmee(Utilisateur destinataire, DetailsRdvCourriel ancien,
                                         DetailsRdvCourriel nouveau, PieceJointeCourriel agenda);

    /**
     * Le membre a déplacé son rendez-vous, qui attend la réponse du garage jusqu'à
     * l'échéance enregistrée. Sans fichier d'agenda, comme toute demande en attente.
     */
    void envoyerModificationRdvEnAttente(Utilisateur destinataire, DetailsRdvCourriel ancien,
                                         DetailsRdvCourriel nouveau, ZonedDateTime echeance);

    /**
     * Le garage a déplacé le rendez-vous, désormais confirmé, avec son motif et son
     * téléphone : à moins de 24 heures, le membre ne peut plus l'annuler en ligne.
     * Porte le fichier d'agenda, sauf si sa rédaction a échoué ({@code agenda} nul).
     */
    void envoyerModificationParLeGarage(Utilisateur destinataire, DetailsRdvCourriel ancien,
                                        DetailsRdvCourriel nouveau, String motif, String telephone,
                                        PieceJointeCourriel agenda);

    /** Avis au garage d'un rendez-vous déplacé par le membre, comme l'avis d'une nouvelle demande. */
    void envoyerAvisModificationAuGarage(DetailsRdvCourriel ancien, DetailsRdvCourriel nouveau,
                                         String membre, boolean confirme, String cheminDetail);

    /**
     * Le garage a chiffre des travaux qui portent l intervention a plus de 10 % du
     * devis initial : RM-15 exige l accord expres du membre avant de poursuivre.
     * C est le canal dedie annonce par RM-16 : le statut percu reste « En cours »,
     * la demande passe par ce courriel et par l ecran de validation.
     */
    void envoyerDemandeValidationDepassement(Utilisateur destinataire,
                                             DetailsDepassementCourriel depassement);

    /**
     * L intervention est terminee (F17) : le membre est invite a venir recuperer son
     * vehicule aux heures d ouverture, et a deposer un avis. Envoye apres commit de
     * la transition TERMINEE, jamais pendant : un courriel ne doit pas annoncer un
     * etat qui pourrait encore etre annule par un rollback.
     */
    void envoyerInterventionTerminee(DetailsInterventionTerminee details);

    /**
     * La commande est payée (F14) : confirmation du contrat conclu sur un support durable
     * (art. VI.46, § 7, CDE), avec les CGV acceptées et le formulaire type de
     * rétractation en pièces jointes. Envoyée après commit du passage à PAYEE, jamais
     * pendant la transaction.
     */
    void envoyerConfirmationCommande(DetailsConfirmationCommande details);

    /**
     * Une commande est payée et attend le garage (F14) : avis à l'adresse de l'atelier,
     * en français, après commit du passage à PAYEE. Rien sans adresse d'atelier.
     */
    void envoyerAvisCommandeAuGarage(DetailsCommandeGarage details);

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
     * Accusé de réception d'une rétractation en ligne (CDE, art. VI.61/2, §4) : envoyé
     * dès la soumission, avant toute réponse du garage, avec le contenu de la
     * déclaration ainsi que la date et l'heure de sa soumission.
     */
    void envoyerAccuseRetractation(DetailsAccuseRetractationCourriel details);

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

    /**
     * Un administrateur a modifié les coordonnées du titulaire, à sa demande. L'avis
     * nomme les champs sans leurs valeurs : un courriel lu par un tiers ne livre ni la
     * nouvelle adresse ni le nouveau numéro. Rédigé dans la langue du compte.
     */
    void envoyerAvisModificationCompte(Utilisateur destinataire, List<ChampCompte> champs);

    /**
     * Un paiement du membre est arrivé alors que sa commande était déjà annulée. L'avis
     * dit que la commande reste annulée et que le montant sera remboursé. Rédigé dans
     * la langue du compte.
     */
    void envoyerAvisPaiementApresAnnulation(Utilisateur destinataire, String numeroCommande,
                                            String montantTvac);

    /** Le paiement reçu après annulation vient d'être remboursé. Langue du compte. */
    void envoyerAvisRemboursementApresAnnulation(Utilisateur destinataire, String numeroCommande,
                                                 String montantTvac);

    /** Prévient l'atelier qu'un paiement reçu sur une commande annulée attend son remboursement. */
    void envoyerAvisPaiementARembourserAuGarage(String numeroCommande, String montantTvac,
                                                String membre);

    /**
     * Le garage a répondu dans un fil (A26). Le courriel donne le sujet et le lien du fil,
     * jamais le corps du message : une boîte aux lettres se lit sans le second facteur de
     * l'espace membre. Langue du compte.
     */
    void envoyerAvisReponseGarage(Utilisateur destinataire, String sujet, UUID fil);

    /**
     * Un membre a écrit dans un fil, à l'adresse de l'atelier. Sans le corps du message, pour
     * la même raison que l'avis au membre.
     */
    void envoyerAvisMessageAuGarage(String sujet, String membre, UUID fil);
}