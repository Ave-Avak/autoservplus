package be.autoservplus.intervention.service;

import be.autoservplus.intervention.repository.LigneInterventionRepository;
import be.autoservplus.intervention.domain.ChangementPieceIncluse;
import be.autoservplus.intervention.domain.PieceIncluse;
import be.autoservplus.catalogue.domain.ChoixPieces;
import be.autoservplus.common.ReserveAuxTests;
import be.autoservplus.config.IdentiteGarage;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.repository.PieceRepository;
import be.autoservplus.catalogue.repository.PrestationRepository;
import be.autoservplus.common.exception.ConflitConcurrenceException;
import be.autoservplus.common.exception.RefusTraduit;
import be.autoservplus.common.exception.RegleMetierException;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.communication.service.DetailsDepassementCourriel;
import be.autoservplus.communication.service.ServiceCourriel;
import be.autoservplus.facturation.domain.Avoir;
import be.autoservplus.facturation.domain.Encaissement;
import be.autoservplus.facturation.domain.Facture;
import be.autoservplus.facturation.domain.MoyenEncaissement;
import be.autoservplus.facturation.service.AvoirService;
import be.autoservplus.facturation.service.EncaissementService;
import be.autoservplus.facturation.service.FactureService;
import be.autoservplus.i18n.LanguesSupportees;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.service.AuteurCourant;
import be.autoservplus.intervention.domain.HistoriqueStatutIntervention;
import be.autoservplus.intervention.domain.Intervention;
import be.autoservplus.intervention.domain.LigneIntervention;
import be.autoservplus.intervention.domain.StatutIntervention;
import be.autoservplus.intervention.repository.HistoriqueStatutInterventionRepository;
import be.autoservplus.intervention.repository.InterventionRepository;
import be.autoservplus.intervention.service.dto.ClotureIntervention;
import be.autoservplus.intervention.service.dto.FactureAtelierVue;
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
import org.springframework.data.domain.AuditorAware;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
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
    private final LigneInterventionRepository lignes;
    private final HistoriqueStatutInterventionRepository historiques;
    private final PrestationRepository prestations;
    private final PieceRepository pieces;
    private final FactureService factures;
    private final AvoirService avoirs;
    private final EncaissementService encaissements;
    private final CommandeRepository commandes;
    private final VehiculeRepository vehicules;
    private final ParametreAtelierRepository parametres;
    private final AuteurCourant auteurCourant;
    private final JournalPiecesIncluses journalPiecesIncluses;
    private final AuditorAware<String> auditeur;
    private final GenerateurNumeroIntervention numeros;
    private final ServiceCourriel courriel;
    private final ApplicationEventPublisher evenements;
    private final IdentiteGarage identite;
    private final Clock horloge;

    public InterventionService(InterventionRepository interventions,
                               LigneInterventionRepository lignes,
                               HistoriqueStatutInterventionRepository historiques,
                               PrestationRepository prestations,
                               PieceRepository pieces,
                               FactureService factures,
                               AvoirService avoirs,
                               EncaissementService encaissements,
                               CommandeRepository commandes,
                               VehiculeRepository vehicules,
                               ParametreAtelierRepository parametres,
                               AuteurCourant auteurCourant,
                               JournalPiecesIncluses journalPiecesIncluses,
                               AuditorAware<String> auditeur,
                               GenerateurNumeroIntervention numeros,
                               ServiceCourriel courriel,
                               ApplicationEventPublisher evenements,
                               IdentiteGarage identite,
                               Clock horloge) {
        this.interventions = interventions;
        this.lignes = lignes;
        this.historiques = historiques;
        this.prestations = prestations;
        this.pieces = pieces;
        this.factures = factures;
        this.avoirs = avoirs;
        this.encaissements = encaissements;
        this.commandes = commandes;
        this.vehicules = vehicules;
        this.parametres = parametres;
        this.auteurCourant = auteurCourant;
        this.journalPiecesIncluses = journalPiecesIncluses;
        this.auditeur = auditeur;
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
                    journalPiecesIncluses.ecrire(creee, creee.copieDesPiecesIncluses());
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
            journalPiecesIncluses.ecrire(creee, creee.copieDesPiecesIncluses());
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
     *
     * <p>Le stock des pièces posées se décompte ici, puis la facture d'atelier est émise
     * (A15, RM-17), dans la même transaction : statut, stock et facture tiennent ou tombent
     * ensemble, et une clôture annulée ne consomme aucun numéro de facture.</p>
     *
     * <p>Refusée avant toute écriture sans titulaire (Q-H-14), et sans son adresse quand une
     * facture est à émettre (Q-H-23, art. 5 AR n° 1) : l'écran mène alors à la modification
     * du compte.</p>
     *
     * <p>Ordre des verrous : l'intervention d'abord, prise avant toute lecture et attendue au
     * plus le délai configuré, puis les pièces, par identifiant croissant au décompte. La
     * transition est écrite avant le décompte, dans {@link #ecrire}, où un conflit de version se
     * traduit. Écrite après, elle partait au vidage automatique que déclenche la première
     * requête du décompte, hors de cette traduction : une écriture concurrente du dossier
     * finissait en page d'erreur, et le décompte avait lu des lignes déjà périmées.</p>
     *
     * @throws ClotureRefuseeException     titulaire absent, ou adresse incomplète
     * @throws ConflitConcurrenceException dossier verrouillé au-delà du délai, ou modifié depuis
     *                                     sa lecture dans la transaction
     */
    @Transactional
    public ClotureIntervention terminer(UUID reference) {
        Intervention it = verrouiller(reference);
        if (!it.getStatut().peutPasserA(StatutIntervention.TERMINEE)) {
            // La machine à états refuse d'abord : une intervention planifiée ne se clôt pas,
            // quelle que soit l'adresse de son client.
            it.terminer(horloge.instant());
        }
        Utilisateur titulaire = it.titulaire();
        if (titulaire == null) {
            throw ClotureRefuseeException.sansTitulaire();
        }
        if (!it.lignesAFacturer().isEmpty() && !titulaire.adressePostaleComplete()) {
            throw ClotureRefuseeException.adresseIncomplete(titulaire.getReference());
        }
        StatutIntervention avant = it.getStatut();
        it.terminer(horloge.instant());
        historiser(it, avant, it.getStatut(), null);
        ecrire(it);
        List<String> rameneesAZero = decompterLeStock(it);
        Optional<Facture> facture = factures.emettrePourIntervention(it, titulaire);
        Intervention enregistre = ecrire(it);
        evenements.publishEvent(new InterventionTermineeEvent(enregistre.getReference()));
        return new ClotureIntervention(enregistre.getNumero(), rameneesAZero,
                facture.map(Facture::getNumero).orElse(null), enregistre.getCommande() != null);
    }

    /** Facture d'atelier de l'intervention, pour son détail au back-office. */
    public Optional<FactureAtelierVue> factureAtelier(UUID reference) {
        return factures.factureDe(charger(reference)).map(this::vueFacture);
    }

    /**
     * Facture d'atelier vue par le titulaire, au suivi de son intervention (Q-H-48). Même
     * contrôle d'appartenance que le suivi : une intervention d'autrui répond 404.
     */
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public Optional<FactureAtelierVue> factureAtelierDuMembre(UUID reference, String email) {
        return factures.factureDe(chargerPourMembre(reference, email)).map(this::vueFacture);
    }

    /**
     * Compte du titulaire à compléter quand son adresse manque : un avertissement à la
     * réception (Q-H-42), avant que la clôture ne le refuse.
     */
    public Optional<UUID> compteSansAdresse(UUID reference) {
        Utilisateur titulaire = charger(reference).titulaire();
        if (titulaire == null || titulaire.adressePostaleComplete()) {
            return Optional.empty();
        }
        return Optional.of(titulaire.getReference());
    }

    /**
     * Annule la facture d'atelier de l'intervention par une note de crédit totale, avec la
     * précision de l'administrateur (Q-H-14, Q-H-50). L'intervention ne change pas : elle reste
     * terminée, seule sa facture est corrigée.
     *
     * @return numéro de la note de crédit
     * @throws RefusTraduit sans facture, facture déjà annulée, ou précision invalide
     */
    @Transactional
    public String annulerFactureAtelier(UUID reference, String precision) {
        Facture facture = factures.factureDe(charger(reference))
                .orElseThrow(() -> new RefusTraduit("admin.intervention.erreur.sans-facture-a-annuler",
                        "L'intervention " + reference + " n'a pas de facture d'atelier."));
        return avoirs.annulerFactureAtelier(facture, precision).getNumero();
    }

    /**
     * Enregistre le règlement au comptoir de la facture d'atelier, pour son total TVAC
     * (RM-20 révisée, Q-H-43).
     *
     * @return numéro de la facture réglée
     * @throws RefusTraduit sans facture, ou refus de l'encaissement
     */
    @Transactional
    public String encaisserFactureAtelier(UUID reference, MoyenEncaissement moyen, LocalDate date) {
        Facture facture = factureAEncaisser(reference);
        encaissements.enregistrer(facture, moyen, date);
        return facture.getNumero();
    }

    /** Annule l'encaissement de la facture d'atelier, motif obligatoire, auteur lu du contexte. */
    @Transactional
    public String annulerEncaissement(UUID reference, String motif) {
        Facture facture = factureAEncaisser(reference);
        encaissements.annuler(facture, motif, auteurCourant.resoudre().getEmail());
        return facture.getNumero();
    }

    private Facture factureAEncaisser(UUID reference) {
        return factures.factureDe(charger(reference))
                .orElseThrow(() -> new RefusTraduit("admin.intervention.erreur.sans-facture-a-regler",
                        "L'intervention " + reference + " n'a pas de facture d'atelier."));
    }

    private FactureAtelierVue vueFacture(Facture facture) {
        Optional<Avoir> avoir = avoirs.avoirDe(facture);
        Optional<Encaissement> reglement = encaissements.actifDe(facture);
        return new FactureAtelierVue(facture.getReference(), facture.getNumero(),
                FormatageRdv.eurosEcran(facture.getMontantTvac()), facture.getDateEcheance(),
                facture.getMontantTvac().signum() == 0 ? "facture.statut.rien-a-regler" : "facture.statut.a-regler",
                avoir.map(Avoir::getReference).orElse(null), avoir.map(Avoir::getNumero).orElse(null),
                reglement.map(Encaissement::getDateEncaissement).orElse(null),
                reglement.map(e -> e.getMoyen().name()).orElse(null),
                LocalDate.ofInstant(horloge.instant(), parametres.courants().zone()));
    }

    /**
     * Retire du stock les pièces posées : les lignes facturables seules, devis initial et
     * ajouts acceptés. Une ligne refusée par le membre n'a pas été posée, et une
     * intervention annulée ne passe jamais ici : c'est pourquoi le stock se décompte à la
     * clôture et non à l'ajout.
     *
     * <p>Une clôture n'est jamais refusée pour un stock insuffisant (Q-H-31) : une pièce
     * commandée exprès et posée sans que sa réception ait été saisie est bien sortie, zéro
     * est alors la valeur juste. Le stock descend au plus à zéro, comme au paiement d'une
     * commande, et le manque s'écrit au journal. Les pièces sont traitées par identifiant
     * croissant, pour que deux clôtures prennent leurs verrous dans le même ordre.</p>
     *
     * @return libellés, dans la langue de l'écran, des pièces dont le plancher a joué
     */
    private List<String> decompterLeStock(Intervention it) {
        Map<Long, Integer> demandes = new TreeMap<>();
        Map<Long, Piece> parIdentifiant = new HashMap<>();
        for (LigneIntervention ligne : it.getLignes()) {
            if (!ligne.estFacturable()) {
                continue;
            }
            if (ligne.getPiece() != null) {
                demandes.merge(ligne.getPiece().getId(), ligne.quantitePourLeStock(), Integer::sum);
                parIdentifiant.put(ligne.getPiece().getId(), ligne.getPiece());
            }
            // Les pièces incluses d'un forfait sortent du stock comme une ligne de pièce : même
            // table de demandes, donc même ordre des verrous et même plancher à zéro (V77).
            for (PieceIncluse incluse : ligne.getPiecesIncluses()) {
                demandes.merge(incluse.getPiece().getId(), incluse.getQuantite(), Integer::sum);
                parIdentifiant.put(incluse.getPiece().getId(), incluse.getPiece());
            }
        }
        String auteur = auditeur.getCurrentAuditor().orElse(null);
        Locale langue = LanguesSupportees.deLEcran();
        List<String> rameneesAZero = new ArrayList<>();
        demandes.forEach((pieceId, demande) -> {
            int enStock = pieces.retirerAuPlus(pieceId, demande, auteur);
            if (enStock < demande) {
                Piece piece = parIdentifiant.get(pieceId);
                log.warn("Stock insuffisant à la clôture de l'intervention {} : {} (posées {}, en stock {}), "
                        + "ramené à zéro.", it.getNumero(), piece.getReferenceFabricant(), demande, enStock);
                rameneesAZero.add(piece.libelle(langue).texte());
            }
        });
        return rameneesAZero;
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
    /** Pièces fournies par le garage quand la prestation propose le choix. */
    @ReserveAuxTests
    @Transactional
    public LigneIntervention ajouterLigneMainOeuvre(UUID interventionRef, UUID prestationRef, BigDecimal quantite) {
        ChoixPieces parDefaut = prestations.findByReference(prestationRef)
                .map(Prestation::choixParDefaut).orElse(null);
        return ajouterLigneMainOeuvre(interventionRef, prestationRef, quantite, parDefaut);
    }

    /**
     * Même ajout, avec le choix des pièces du formulaire.
     *
     * <p>La ligne nouvelle est enregistrée elle-même avant l'écriture du dossier. Le dossier
     * existant s'écrit par {@code saveAndFlush}, donc par {@code merge} : sur une entité gérée,
     * {@code merge} se propage aux enfants, et pour un enfant encore transitoire il en crée une
     * copie gérée, qu'il range dans la collection à la place de l'instance du domaine. Cette
     * instance restait transitoire, et la trace de la copie des pièces incluses, qui pointait
     * vers elle, faisait échouer l'écriture. Enregistrée d'abord, la ligne est gérée, et
     * {@code merge} la laisse telle quelle.</p>
     *
     * @param choix choix des pièces du formulaire, qui présélectionne les pièces fournies. Il est
     *              ignoré pour une prestation qui ne propose pas de prix pose seule, où le même
     *              formulaire l'envoie aussi.
     * @throws be.autoservplus.catalogue.domain.ChoixPiecesRefuseException choix absent sur un
     *         forfait qui propose un prix pose seule : rien n'est déduit ici, la présélection
     *         reste au formulaire
     */
    @Transactional
    public LigneIntervention ajouterLigneMainOeuvre(UUID interventionRef, UUID prestationRef, BigDecimal quantite,
                                                    ChoixPieces choix) {
        Intervention it = verrouiller(interventionRef);
        Prestation prestation = prestations.findByReference(prestationRef)
                .orElseThrow(() -> new RessourceIntrouvableException("Prestation", prestationRef));
        ChoixPieces retenu = prestation.proposePoseSeule() ? choix : null;
        StatutIntervention avant = it.getStatut();
        LigneIntervention ligne = lignes.save(it.ajouterLigneMainOeuvre(prestation, quantite, retenu));
        enregistrerAjout(it, avant);
        journalPiecesIncluses.ecrire(it, ligne.copie());
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
    public LigneIntervention ajouterLignePiece(UUID interventionRef, UUID pieceRef, int quantite) {
        Intervention it = verrouiller(interventionRef);
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

    /** Retire la ligne et ses pièces incluses, que le journal garde comme retirées. */
    @Transactional
    public void retirerLigne(UUID interventionRef, Long ligneId) {
        Intervention it = verrouiller(interventionRef);
        List<ChangementPieceIncluse> retraits = it.ligne(ligneId)
                .map(LigneIntervention::retraitAvecLaLigne).orElse(List.of());
        if (!it.retirerLigne(ligneId)) {
            throw new RessourceIntrouvableException("LigneIntervention", ligneId);
        }
        journalPiecesIncluses.ecrire(it, retraits);
        ecrire(it);
    }

    /**
     * Ajoute une pièce incluse à une ligne de forfait, ou change sa quantité (V77). Même verrou
     * que l'ajout de ligne et la clôture : deux administrateurs qui ajustent le même dossier en
     * même temps passent l'un après l'autre, la version monte pour chacun, et une modification
     * tenue au-delà du délai donne au second un conflit à recharger.
     *
     * @throws RessourceIntrouvableException ligne inconnue, ou pièce inconnue, ou pièce retirée de
     *                                       la vente que la ligne ne portait pas encore (RM-28)
     * @throws PieceIncluseRefuseeException  intervention close, ligne sans pièces incluses, ou
     *                                       quantité inférieure à un
     */
    @Transactional
    public void fixerPieceIncluse(UUID interventionRef, Long ligneId, UUID pieceRef, int quantite) {
        Intervention it = verrouiller(interventionRef);
        LigneIntervention ligne = it.ligne(ligneId)
                .orElseThrow(() -> new RessourceIntrouvableException("LigneIntervention", ligneId));
        Piece piece = pieces.findByReference(pieceRef)
                .orElseThrow(() -> new RessourceIntrouvableException("Piece", pieceRef));
        boolean dejaIncluse = ligne.getPiecesIncluses().stream()
                .anyMatch(incluse -> incluse.getPiece().equals(piece));
        if (!dejaIncluse && !piece.isActif()) {
            // Une pièce retirée de la vente ne s'ajoute plus (RM-28). Celle qu'une ligne porte
            // déjà garde sa quantité corrigeable : le garage la pose encore.
            throw new RessourceIntrouvableException("Piece", pieceRef);
        }
        ChangementPieceIncluse changement = it.fixerPieceIncluse(ligne, piece, quantite);
        ecrire(it);
        journalPiecesIncluses.ecrire(it, List.of(changement));
    }

    /** @throws PieceIncluseRefuseeException intervention close, ou pièce absente de la ligne */
    @Transactional
    public void retirerPieceIncluse(UUID interventionRef, Long ligneId, UUID pieceRef) {
        Intervention it = verrouiller(interventionRef);
        LigneIntervention ligne = it.ligne(ligneId)
                .orElseThrow(() -> new RessourceIntrouvableException("LigneIntervention", ligneId));
        Piece piece = pieces.findByReference(pieceRef)
                .orElseThrow(() -> new RessourceIntrouvableException("Piece", pieceRef));
        ChangementPieceIncluse changement = it.retirerPieceIncluse(ligne, piece);
        journalPiecesIncluses.ecrire(it, List.of(changement));
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

    /**
     * Pièces de ce dossier, lignes de pièce et pièces incluses, que le stock ne couvre pas :
     * une seule requête d'agrégat sur les trois sources.
     */
    private Set<Long> piecesACommander(Intervention it) {
        Set<Long> pieceIds = new HashSet<>();
        for (LigneIntervention ligne : it.getLignes()) {
            if (ligne.getPiece() != null) {
                pieceIds.add(ligne.getPiece().getId());
            }
            ligne.getPiecesIncluses().forEach(incluse -> pieceIds.add(incluse.getPiece().getId()));
        }
        if (pieceIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(interventions.piecesACommander(pieceIds));
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

    /**
     * L'intervention, ligne verrouillée et version montée, pour une écriture qui change ses
     * lignes ou les décompte : ajout et retrait de ligne, pièces incluses, clôture. Un ajout de ligne ne modifie
     * pas la ligne de l'intervention elle-même : sans ce verrou, il passait à côté d'une clôture
     * en cours, et la ligne ajoutée restait sur une intervention close, ni décomptée ni facturée.
     *
     * @throws ConflitConcurrenceException dossier tenu au-delà du délai, ou périmé
     */
    private Intervention verrouiller(UUID reference) {
        return interventions.verrouiller(reference)
                .orElseThrow(() -> new RessourceIntrouvableException("Intervention", reference));
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
                            FormatageRdv.euros(it.totalDevisInitialTvac(), langue),
                            FormatageRdv.euros(it.totalDevisInitialHtva(), langue),
                            FormatageRdv.euros(it.totalProposeTvac(), langue),
                            FormatageRdv.euros(it.totalProposeHtva(), langue),
                            it.lignesEnAttente().stream()
                                    .map(l -> "- %s x%s : %s"
                                            .formatted(l.getLibelleFige(),
                                                    FormatageQuantite.lisible(l.getQuantite(), l.getUnite(), langue),
                                                    FormatageRdv.euros(l.totalTvac(), langue)))
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
