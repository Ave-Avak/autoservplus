package be.autoservplus.intervention.service;

import be.autoservplus.config.IdentiteGarage;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.repository.PieceRepository;
import be.autoservplus.catalogue.repository.PrestationRepository;
import be.autoservplus.common.exception.ConflitConcurrenceException;
import be.autoservplus.common.exception.RegleMetierException;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.communication.service.DetailsDepassementCourriel;
import be.autoservplus.communication.service.ServiceCourriel;
import be.autoservplus.i18n.LanguesSupportees;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.service.AuteurCourant;
import be.autoservplus.intervention.domain.HistoriqueStatutIntervention;
import be.autoservplus.intervention.domain.Intervention;
import be.autoservplus.intervention.domain.LigneIntervention;
import be.autoservplus.intervention.domain.StatutIntervention;
import be.autoservplus.intervention.repository.HistoriqueStatutInterventionRepository;
import be.autoservplus.intervention.repository.InterventionRepository;
import be.autoservplus.intervention.service.dto.PieceAjoutableVue;
import be.autoservplus.intervention.web.dto.DemandeValidationVue;
import be.autoservplus.intervention.web.dto.InterventionVueAdmin;
import be.autoservplus.intervention.web.dto.InterventionVueMembre;
import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.repository.VehiculeRepository;
import be.autoservplus.reservation.domain.Vehicule;
import be.autoservplus.vente.repository.CommandeRepository;
import be.autoservplus.vente.domain.StatutCommande;
import be.autoservplus.vente.domain.LignePanier;
import be.autoservplus.vente.domain.Commande;
import be.autoservplus.reservation.repository.ParametreAtelierRepository;
import be.autoservplus.reservation.service.support.FormatageRdv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Gestion administrative des interventions : creation depuis un RDV honore
 * (idempotente), transitions gardees, edition des lignes et du commentaire
 * client, vues pour le tableau de bord garage.
 *
 * <p>Point d ancrage de la creation automatique : {@link #creerDepuisRdv} est
 * appele inline dans {@code AdminRdvService.marquerHonore}, dans la meme
 * transaction : le passage HONORE et l existence de l intervention sont
 * atomiques.</p>
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasRole('ADMINISTRATEUR')")
public class InterventionService {

    private static final Logger log = LoggerFactory.getLogger(InterventionService.class);

    private final InterventionRepository interventions;
    private final HistoriqueStatutInterventionRepository historiques;
    private final PrestationRepository prestations;
    private final PieceRepository pieces;
    private final CommandeRepository commandes;
    private final VehiculeRepository vehicules;
    private final ParametreAtelierRepository parametres;
    private final AuteurCourant auteurCourant;
    private final GenerateurNumeroIntervention numeros;
    private final ServiceCourriel courriel;
    private final ApplicationEventPublisher evenements;
    private final IdentiteGarage identite;
    private final Clock horloge;

    public InterventionService(InterventionRepository interventions,
                               HistoriqueStatutInterventionRepository historiques,
                               PrestationRepository prestations,
                               PieceRepository pieces,
                               CommandeRepository commandes,
                               VehiculeRepository vehicules,
                               ParametreAtelierRepository parametres,
                               AuteurCourant auteurCourant,
                               GenerateurNumeroIntervention numeros,
                               ServiceCourriel courriel,
                               ApplicationEventPublisher evenements,
                               IdentiteGarage identite,
                               Clock horloge) {
        this.interventions = interventions;
        this.historiques = historiques;
        this.prestations = prestations;
        this.pieces = pieces;
        this.commandes = commandes;
        this.vehicules = vehicules;
        this.parametres = parametres;
        this.auteurCourant = auteurCourant;
        this.numeros = numeros;
        this.courriel = courriel;
        this.evenements = evenements;
        this.identite = identite;
        this.horloge = horloge;
    }

    // --- creation depuis un RDV (idempotente) -----------------------------------------

    /**
     * Cree l intervention correspondant au RDV, ou retourne celle qui existe deja.
     * Idempotent : un second appel pour le meme RDV ne cree pas de doublon.
     * Appele par {@code AdminRdvService.marquerHonore} dans la meme transaction.
     */
    @Transactional
    public Intervention creerDepuisRdv(Rdv rdv) {
        return interventions.findByRdvId(rdv.getId())
                .orElseGet(() -> {
                    Intervention creee = ecrire(new Intervention(numeros.prochain(), rdv,
                            identite.langueDuDocument(rdv.getMembre().getLangue())));
                    // Ligne de naissance de la chronologie (F17) : pas d etat anterieur.
                    // Dans la branche orElseGet uniquement : un second appel idempotent
                    // ne doit pas inventer une seconde naissance.
                    historiser(creee, null, StatutIntervention.PLANIFIEE, null);
                    return creee;
                });
    }

    // --- transitions -----------------------------------------------------------------
    //
    // Chaque transition ecrit sa ligne de chronologie (F17) dans la meme transaction :
    // l etat AVANT est capture avant l appel au domaine, qui peut refuser (auquel cas
    // rien n est historise : l exception sort avant historiser). Un echec ulterieur de
    // l ecriture annule transition ET chronologie ensemble : elles ne divergent jamais.

    /**
     * Cree une intervention depuis une commande de SERVICES payee (F12-b).
     *
     * <p><b>Seconde origine, qui ne remplace pas la premiere.</b>
     * {@link #creerDepuisRdv} et son invariant de creation atomique au marquage HONORE
     * restent inchanges. Celle-ci s ajoute a cote. La machine a etats est la meme, les
     * statuts ne sont pas dedoubles, et la chronologie F17 s ouvre de la meme facon.</p>
     *
     * <p><b>Idempotent</b>, comme {@code creerDepuisRdv} : un second appel sur la meme
     * commande rend l intervention existante plutot que d en creer une seconde. Un
     * double-clic de l administrateur ne doit pas dedoubler un dossier d atelier.</p>
     *
     * <p><b>Le vehicule est choisi par le garage</b> : {@code intervention.vehicule_id}
     * est {@code NOT NULL} et une prestation achetee en ligne n est rattachee a aucun
     * vehicule au moment de l achat. Le vehicule doit appartenir au titulaire de la
     * commande, sinon on creerait un dossier d atelier sur la voiture d un tiers.</p>
     *
     * @throws RegleMetierException commande non payee, sans ligne de service, ou
     *                              vehicule n appartenant pas au titulaire
     */
    @Transactional
    public Intervention creerDepuisCommande(UUID referenceCommande, UUID referenceVehicule) {
        Commande commande = commandes.findByReference(referenceCommande)
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "Commande", referenceCommande));
        return interventions.findByCommandeId(commande.getId()).orElseGet(() -> {
            if (commande.getStatut() != StatutCommande.PAYEE) {
                throw new RegleMetierException(
                        "Une intervention ne se cree que depuis une commande payée.");
            }
            List<LignePanier> services = commandes.lignesServiceDe(commande);
            if (services.isEmpty()) {
                throw new RegleMetierException(
                        "Cette commande ne contient aucune prestation à exécuter.");
            }
            Vehicule vehicule = vehicules.findByReference(referenceVehicule)
                    .orElseThrow(() -> new RessourceIntrouvableException(
                            "Vehicule", referenceVehicule));
            if (!vehicule.getMembre().getId().equals(commande.getMembre().getId())) {
                throw new RegleMetierException(
                        "Ce véhicule n'appartient pas au client de cette commande.");
            }
            Intervention creee = ecrire(
                    Intervention.pourCommande(numeros.prochain(), commande, vehicule, services));
            historiser(creee, null, StatutIntervention.PLANIFIEE, null);
            return creee;
        });
    }

    @Transactional
    public Intervention demarrer(UUID reference) {
        Intervention it = charger(reference);
        StatutIntervention avant = it.getStatut();
        it.demarrer(horloge.instant());
        historiser(it, avant, it.getStatut(), null);
        return ecrire(it);
    }

    @Transactional
    public Intervention suspendre(UUID reference) {
        Intervention it = charger(reference);
        StatutIntervention avant = it.getStatut();
        it.suspendre();
        historiser(it, avant, it.getStatut(), null);
        return ecrire(it);
    }

    @Transactional
    public Intervention reprendre(UUID reference) {
        Intervention it = charger(reference);
        StatutIntervention avant = it.getStatut();
        it.reprendre();
        historiser(it, avant, it.getStatut(), null);
        return ecrire(it);
    }

    /**
     * Terminaison des travaux. Publie {@link InterventionTermineeEvent} : le courriel
     * « venez recuperer votre vehicule » (F17) part apres commit, via le listener
     * {@code AFTER_COMMIT}, jamais pendant la transaction, ou un rollback annoncerait
     * une cloture qui n a pas eu lieu. Seule cette transition publie : c est la seule
     * qui appelle le membre a se deplacer.
     */
    @Transactional
    public Intervention terminer(UUID reference) {
        Intervention it = charger(reference);
        StatutIntervention avant = it.getStatut();
        it.terminer(horloge.instant());
        historiser(it, avant, it.getStatut(), null);
        Intervention enregistre = ecrire(it);
        evenements.publishEvent(new InterventionTermineeEvent(enregistre.getReference()));
        return enregistre;
    }

    /**
     * Annulation definitive. Depuis PLANIFIEE, EN_COURS, SUSPENDUE ou
     * ATTENTE_VALIDATION_MEMBRE. Refusee si l intervention est deja terminale.
     */
    @Transactional
    public Intervention annuler(UUID reference) {
        Intervention it = charger(reference);
        StatutIntervention avant = it.getStatut();
        it.annuler();
        historiser(it, avant, it.getStatut(), null);
        return ecrire(it);
    }

    // --- edition des lignes et du commentaire ----------------------------------------

    @Transactional
    public Intervention modifierCommentaireAdmin(UUID reference, String texte) {
        Intervention it = charger(reference);
        it.modifierCommentaireAdmin(texte);
        return ecrire(it);
    }

    /**
     * Note interne de l'atelier (A16). Réservée au garage par la classe : aucune
     * surcharge {@code isAuthenticated()} ici, contrairement aux méthodes du membre.
     */
    @Transactional
    public Intervention modifierNoteInterne(UUID reference, String texte) {
        Intervention it = charger(reference);
        it.modifierNoteInterne(texte);
        return ecrire(it);
    }

    /**
     * Ajoute une prestation au dossier. Si l ajout porte le total au-dela du devis
     * majore de 10 %, l entite bascule d elle-meme en ATTENTE_VALIDATION_MEMBRE
     * (RM-15). Le service se contente de constater la bascule et de prevenir le
     * membre. La regle vit dans le domaine, pas ici.
     */
    @Transactional
    public LigneIntervention ajouterLigneMainOeuvre(UUID interventionRef, UUID prestationRef, short quantite) {
        Intervention it = charger(interventionRef);
        Prestation prestation = prestations.findByReference(prestationRef)
                .orElseThrow(() -> new RessourceIntrouvableException("Prestation", prestationRef));
        StatutIntervention avant = it.getStatut();
        LigneIntervention ligne = it.ajouterLigneMainOeuvre(prestation, quantite,
                prestation.getPrixHtva(), prestation.getTauxTva());
        enregistrerAjout(it, avant);
        return ligne;
    }

    /**
     * Ajoute une pièce du catalogue au dossier (RM-14, cas 4 et 11). Mêmes gardes et même
     * seuil RM-15 que la main-d'œuvre : une pièce peut, seule, déclencher la demande
     * d'accord du membre.
     *
     * <p>Le stock n'est pas touché ici, une pièce absente du stock est admise : elle sera
     * marquée « à commander » au détail (Q-H-30). Le stock se décompte à la clôture, quand
     * on sait quelles lignes ont été posées (une ligne refusée par le membre ne l'est pas,
     * une intervention annulée n'en pose aucune).</p>
     *
     * @throws RessourceIntrouvableException pièce inconnue, ou retirée de la vente : une
     *                                       pièce désactivée n'est plus proposée (RM-28)
     */
    @Transactional
    public LigneIntervention ajouterLignePiece(UUID interventionRef, UUID pieceRef, short quantite) {
        Intervention it = charger(interventionRef);
        Piece piece = pieces.findByReference(pieceRef)
                .filter(Piece::isActif)
                .orElseThrow(() -> new RessourceIntrouvableException("Piece", pieceRef));
        StatutIntervention avant = it.getStatut();
        LigneIntervention ligne = it.ajouterLignePiece(piece, quantite);
        enregistrerAjout(it, avant);
        return ligne;
    }

    /**
     * Suite commune aux deux ajouts. La bascule RM-15 est une transition comme une autre :
     * si l'entité a changé d'état d'elle-même, la chronologie (F17) le consigne, sinon
     * l'historique aurait des trous (un EN_COURS vers EN_COURS apparent).
     */
    private void enregistrerAjout(Intervention it, StatutIntervention avant) {
        if (it.getStatut() != avant) {
            historiser(it, avant, it.getStatut(), null);
        }
        Intervention enregistre = ecrire(it);
        if (avant != StatutIntervention.ATTENTE_VALIDATION_MEMBRE
                && enregistre.getStatut() == StatutIntervention.ATTENTE_VALIDATION_MEMBRE) {
            notifierDepassementSansEchouer(enregistre);
            evenements.publishEvent(new DepassementSoumisEvent(enregistre.getReference()));
        }
    }

    @Transactional
    public void retirerLigne(UUID interventionRef, Long ligneId) {
        Intervention it = charger(interventionRef);
        if (!it.retirerLigne(ligneId)) {
            throw new RessourceIntrouvableException("LigneIntervention", ligneId);
        }
        ecrire(it);
    }

    // --- vues ------------------------------------------------------------------------

    /**
     * Interventions ouvertes (non terminales) pour le tableau de bord admin :
     * PLANIFIEE, EN_COURS, SUSPENDUE et ATTENTE_VALIDATION_MEMBRE. Les TERMINEE
     * et ANNULEE sortent de l ecran actif.
     */
    public List<InterventionVueAdmin> interventionsEnCours() {
        ZoneId zone = parametres.courants().zone();
        return interventions.findByStatutIn(EnumSet.of(
                        StatutIntervention.PLANIFIEE,
                        StatutIntervention.EN_COURS,
                        StatutIntervention.SUSPENDUE,
                        StatutIntervention.ATTENTE_VALIDATION_MEMBRE))
                .stream()
                .map(i -> InterventionVueAdmin.de(i, zone))
                .toList();
    }

    public InterventionVueAdmin vueAdmin(UUID reference) {
        ZoneId zone = parametres.courants().zone();
        Intervention it = charger(reference);
        return InterventionVueAdmin.de(it, zone, piecesACommander(it));
    }

    /** Prestations actives, pour peupler le formulaire d ajout de ligne. */
    public List<Prestation> prestationsActives() {
        return prestations.findByActifTrueOrderByLibelleAsc();
    }

    /** Pièces en vente, libellé dans la langue de l'écran, pour le formulaire d'ajout. */
    public List<PieceAjoutableVue> piecesAjoutables() {
        Locale langue = LanguesSupportees.deLEcran();
        return pieces.findByActifTrueOrderByLibelleAsc().stream()
                .map(p -> new PieceAjoutableVue(p.getReference(), p.libelle(langue).texte(),
                        p.getQuantiteStock()))
                .toList();
    }

    /** Pièces de ce dossier que le stock ne couvre pas, une seule requête d'agrégat. */
    private Set<Long> piecesACommander(Intervention it) {
        Set<Long> pieceIds = new HashSet<>();
        it.getLignes().stream()
                .filter(l -> l.getPiece() != null)
                .forEach(l -> pieceIds.add(l.getPiece().getId()));
        if (pieceIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(interventions.piecesACommander(pieceIds,
                EnumSet.of(StatutIntervention.TERMINEE, StatutIntervention.ANNULEE)));
    }

    // --- vue membre (accessible a tout utilisateur authentifie, ownership verifie) ----

    /**
     * Vue destinee au membre proprietaire du RDV lie a l intervention. Le
     * {@code @PreAuthorize} au niveau methode surcharge celui de la classe
     * (ADMINISTRATEUR) : tout membre authentifie peut appeler, l ownership est
     * verifie par comparaison d email. Une intervention d autrui remonte comme
     * {@link RessourceIntrouvableException} (404, meme code qu une reference
     * inconnue) pour ne pas confirmer l existence de la reference.
     */
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public InterventionVueMembre interventionDuMembre(UUID reference, String email) {
        Intervention it = chargerPourMembre(reference, email);
        // La chronologie (F17) est chargee ici, dans la transaction readOnly, et
        // convertie par le DTO : meme controle d ownership que le reste de la vue.
        return InterventionVueMembre.de(it,
                historiques.findByInterventionOrderByHorodatageAscIdAsc(it),
                parametres.courants().zone());
    }

    /**
     * Vérifie que le membre est le titulaire de l'intervention, pour un appelant qui sert
     * une ressource rattachée (photo). Même réponse qu'une référence inconnue sinon.
     */
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public void exigerTitulaire(UUID reference, String email) {
        chargerPourMembre(reference, email);
    }

    // --- RM-15 : validation du depassement par le membre -----------------------------

    /**
     * Detail du depassement soumis au membre. Refuse si aucune reponse n est attendue :
     * on ne presente pas un ecran de decision sur une intervention qui n a rien demande.
     */
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public DemandeValidationVue demandeValidation(UUID reference, String email) {
        Intervention it = chargerPourMembre(reference, email);
        if (it.getStatut() != StatutIntervention.ATTENTE_VALIDATION_MEMBRE) {
            throw new RessourceIntrouvableException("Demande de validation", reference);
        }
        return DemandeValidationVue.de(it);
    }

    @Transactional
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public Intervention validerDepassement(UUID reference, String email) {
        Intervention it = chargerPourMembre(reference, email);
        StatutIntervention avant = it.getStatut();
        it.validerDepassement();
        historiser(it, avant, it.getStatut(), null);
        return ecrire(it);
    }

    @Transactional
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public Intervention refuserDepassement(UUID reference, String email) {
        Intervention it = chargerPourMembre(reference, email);
        StatutIntervention avant = it.getStatut();
        it.refuserDepassement();
        historiser(it, avant, it.getStatut(), null);
        return ecrire(it);
    }

    /**
     * Resolution RDV -> intervention pour le lien depuis la fiche RDV membre.
     * Renvoie la reference de l intervention si elle existe et appartient au membre,
     * sinon {@link RessourceIntrouvableException} (404).
     */
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public UUID referenceParRdvDuMembre(UUID rdvReference, String email) {
        Intervention it = interventions.findByRdvReference(rdvReference)
                .orElseThrow(() -> new RessourceIntrouvableException("Intervention pour RDV", rdvReference));
        if (!it.getRdv().getMembre().getEmail().equals(email)) {
            throw new RessourceIntrouvableException("Intervention pour RDV", rdvReference);
        }
        return it.getReference();
    }

    // --- helpers ---------------------------------------------------------------------

    /**
     * Ecrit une ligne de chronologie (F17) : la transition telle qu elle vient de se
     * produire, horodatee par l horloge injectee. L auteur vient de
     * {@link AuteurCourant}, donc du contexte de securite (jamais d un parametre de
     * requete) et vaut {@code null} pour un traitement sans utilisateur authentifie.
     */
    private void historiser(Intervention it, StatutIntervention avant,
                            StatutIntervention apres, String motif) {
        historiques.save(new HistoriqueStatutIntervention(
                it, avant, apres, horloge.instant(), auteurCourant.resoudre(), motif));
    }

    private Intervention charger(UUID reference) {
        return interventions.findByReference(reference)
                .orElseThrow(() -> new RessourceIntrouvableException("Intervention", reference));
    }

    /**
     * Charge une intervention en verifiant qu elle appartient bien au membre. Une
     * intervention d autrui remonte comme {@link RessourceIntrouvableException} (404,
     * meme code qu une reference inconnue) pour ne pas confirmer l existence de la
     * reference a un tiers.
     */
    private Intervention chargerPourMembre(UUID reference, String email) {
        Intervention it = charger(reference);
        // titulaire() couvre le rendez-vous honoré, la commande de services payée (F12-b)
        // et, sans l'un ni l'autre, le propriétaire du véhicule. Tester getRdv() seul privait
        // de son intervention le membre qui avait acheté une prestation en ligne.
        var titulaire = it.titulaire();
        if (titulaire == null || !titulaire.getEmail().equals(email)) {
            throw new RessourceIntrouvableException("Intervention", reference);
        }
        return it;
    }

    /**
     * Previent le membre qu un accord est attendu (RM-15). Comme
     * {@code AdminRdvService}, l envoi est inline et avale les {@link RuntimeException}
     * du fournisseur : la bascule est deja persistee, un fournisseur mail indisponible
     * ne doit pas la faire disparaitre. Le membre garde l ecran de validation, visible
     * depuis son suivi, comme second canal.
     */
    private void notifierDepassementSansEchouer(Intervention it) {
        // titulaire() et non le membre du rendez-vous : une intervention née d'une commande
        // de prestations, ou entrée directement au garage, attend le même accord.
        Utilisateur titulaire = it.titulaire();
        if (titulaire == null) {
            log.warn("Dépassement de devis sur l'intervention {} sans titulaire : aucun membre à prévenir.",
                    it.getNumero());
            return;
        }
        Locale langue = LanguesSupportees.duCompte(titulaire.getLangue());
        try {
            Vehicule vehicule = it.getVehicule();
            courriel.envoyerDemandeValidationDepassement(
                    titulaire,
                    new DetailsDepassementCourriel(
                            it.getNumero(),
                            "%s %s (%s)".formatted(vehicule.getMarque(), vehicule.getModele(),
                                    vehicule.getPlaque()),
                            FormatageRdv.euros(it.devisReferenceHtva(), langue),
                            FormatageRdv.euros(it.totalProposeHtva(), langue),
                            FormatageRdv.euros(it.totalProposeTvac(), langue),
                            it.lignesEnAttente().stream()
                                    .map(l -> "- %s x%d : %s"
                                            .formatted(l.getLibelleFige(), l.getQuantite(),
                                                    FormatageRdv.euros(l.totalHtva(), langue)))
                                    .toList(),
                            "/mes-interventions/" + it.getReference() + "/validation"));
        } catch (RuntimeException e) {
            log.warn("Envoi de la demande de validation de depassement impossible pour {} : {}",
                    it.getNumero(), e.getMessage());
        }
    }

    private Intervention ecrire(Intervention it) {
        try {
            return interventions.saveAndFlush(it);
        } catch (OptimisticLockingFailureException e) {
            throw new ConflitConcurrenceException(
                    "Cette intervention a été mise à jour par un autre administrateur, rechargez la page.");
        }
    }
}
