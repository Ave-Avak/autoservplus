package be.autoservplus.retractation.service;

import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.retractation.domain.DemandeAnnulation;
import be.autoservplus.retractation.domain.StatutDemandeAnnulation;
import be.autoservplus.retractation.repository.DemandeAnnulationRepository;
import be.autoservplus.retractation.service.dto.RetractationVue;
import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.domain.StatutCommande;
import be.autoservplus.vente.repository.CommandeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cote membre de la retractation (F30, RM-23) : controle d eligibilite et depot de
 * la demande.
 *
 * <p><b>Ce que le systeme peut trancher seul</b> : la commande appartient bien au
 * demandeur, elle a ete payee, le delai legal court encore, aucune demande n est
 * deja pendante. Tout le reste — la piece a-t-elle ete montee, deballee, abimee —
 * suppose un constat physique que seul l atelier peut faire, et part donc a la
 * validation administrateur ({@code AdminRetractationService}). Ce partage est la
 * raison d etre du flux en deux temps voulu par le CdC (P372-373).</p>
 *
 * <p><b>Point de depart du delai de quatorze jours.</b> La loi le fait courir a
 * compter de la <b>reception</b> du bien (CDE, art. VI.47 §2). La V1 ne suit pas la
 * livraison : aucune date de reception n existe en base. Le delai est donc compte
 * depuis la <b>conclusion de la commande</b>, qui est necessairement anterieure a la
 * reception. Le membre dispose ainsi d une fenetre plus courte que la fenetre legale,
 * jamais plus longue : l ecart joue contre le garage, pas contre le consommateur, ce
 * qui est le seul sens dans lequel une approximation est defendable. Le jour ou la
 * livraison sera suivie, il suffira de changer la date de depart.</p>
 *
 * <p>Arithmetique en {@link Duration} de 14 x 24 h, et non en jours calendaires
 * arretes a minuit : legerement plus stricte que le calcul legal, dans le meme sens
 * que ci-dessus. L horloge est injectee ({@link Clock}), jamais {@code Instant.now()}
 * — c est ce qui rend la fenetre testable a date gelee.</p>
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("isAuthenticated()")
public class RetractationService {

    private static final Logger log = LoggerFactory.getLogger(RetractationService.class);

    /** Delai legal de retractation du consommateur (CDE, art. VI.47 §1er). */
    public static final Duration DELAI_LEGAL = Duration.ofDays(14);

    private final CommandeRepository commandes;
    private final DemandeAnnulationRepository demandes;
    private final Clock horloge;

    public RetractationService(CommandeRepository commandes,
                               DemandeAnnulationRepository demandes,
                               Clock horloge) {
        this.commandes = commandes;
        this.demandes = demandes;
        this.horloge = horloge;
    }

    /**
     * Depose une demande de retractation sur une commande du membre connecte.
     *
     * <p>L identite vient du contexte de securite et la commande est retrouvee par sa
     * reference publique : une commande qui n est pas celle du demandeur remonte
     * comme introuvable, donc 404 et jamais 403 — meme mecanisme que partout ailleurs,
     * un 403 confirmerait a un tiers que cette commande existe.</p>
     *
     * @param motifMembre facultatif : le droit de retractation est inconditionnel, le
     *                    consommateur n a pas a se justifier (CDE, art. VI.47)
     * @throws RessourceIntrouvableException     reference inconnue, ou commande d autrui
     * @throws RetractationImpossibleException   le controle automatique refuse
     */
    @Transactional
    public DemandeAnnulation demander(String email, UUID referenceCommande, String motifMembre) {
        Commande commande = commandeDuMembre(referenceCommande, email);
        MotifRefusRetractation refus = refusEventuel(commande, horloge.instant());
        if (refus != null) {
            throw new RetractationImpossibleException(refus);
        }
        DemandeAnnulation demande = demandes.save(
                new DemandeAnnulation(commande, motifMembre, horloge.instant()));
        log.info("Demande de retractation {} deposee sur la commande {}.",
                demande.getReference(), commande.getNumero());
        return demande;
    }

    /**
     * Etat de la retractation pour chaque commande du membre, indexe par reference de
     * commande — de quoi decider, ligne par ligne, s il faut afficher le bouton de
     * demande, un rappel « demande en cours », ou le lien vers la note de credit.
     *
     * <p>Une seule requete pour les commandes et une pour les demandes, jamais une
     * requete par ligne : l historique d un client fidele en compterait des dizaines.</p>
     */
    public Map<UUID, RetractationVue> etatsDuMembre(String email) {
        Instant maintenant = horloge.instant();

        // Derniere demande par commande. La requete est triee du plus ancien au plus
        // recent : ecraser au fil du parcours laisse donc la plus recente en place.
        Map<UUID, DemandeAnnulation> derniere = new HashMap<>();
        for (DemandeAnnulation demande : demandes.demandesDuMembre(email)) {
            derniere.put(demande.getCommande().getReference(), demande);
        }

        Map<UUID, RetractationVue> etats = new LinkedHashMap<>();
        for (Commande commande : commandes.historiqueDuMembre(email)) {
            etats.put(commande.getReference(),
                    vuePour(commande, derniere.get(commande.getReference()), maintenant));
        }
        return etats;
    }

    /**
     * Le meme etat, pour une seule commande — ce dont le detail d une commande a
     * besoin (F32).
     *
     * <p>Passe par {@link #vuePour} comme {@link #etatsDuMembre} : l eligibilite est
     * calculee par le <b>meme</b> {@link #refusEventuel}, et la vue construite par le
     * meme code. Recalculer la regle ici la ferait diverger de la liste au premier
     * amendement — le membre verrait un bouton sur un ecran et pas sur l autre, pour
     * la meme commande.</p>
     *
     * <p>La commande d autrui remonte en 404 via {@link #commandeDuMembre}, comme
     * partout ailleurs.</p>
     */
    public RetractationVue etatDeLaCommande(String email, UUID referenceCommande) {
        Commande commande = commandeDuMembre(referenceCommande, email);
        // historiqueDe trie du plus recent au plus ancien : le premier est la derniere
        // demande, celle que l ecran doit montrer.
        DemandeAnnulation derniere = demandes.historiqueDe(commande).stream()
                .findFirst()
                .orElse(null);
        return vuePour(commande, derniere, horloge.instant());
    }

    /** Construction unique de la vue, partagee par la liste et le detail. */
    private RetractationVue vuePour(Commande commande, DemandeAnnulation derniereDemande,
                                    Instant maintenant) {
        boolean demandable = refusEventuel(commande, maintenant) == null;
        return derniereDemande == null
                ? RetractationVue.sansDemande(commande.getReference(), demandable)
                : RetractationVue.avecDemande(derniereDemande, demandable);
    }

    /** Historique des demandes d une commande du membre (la plus recente d abord). */
    public List<DemandeAnnulation> historique(UUID referenceCommande, String email) {
        return demandes.historiqueDe(commandeDuMembre(referenceCommande, email));
    }

    /**
     * Le motif de refus du controle automatique, ou {@code null} si la commande est
     * eligible.
     *
     * <p>Un {@code null} plutot qu un {@code Optional} : la methode est privee, et le
     * seul appelant public la traduit immediatement en exception ou en booleen.
     * L ordre des gardes n est pas neutre — l etat de la commande est verifie avant
     * le delai, pour qu une commande jamais payee reponde « non payee » et non
     * « delai expire » deux semaines plus tard.</p>
     */
    private MotifRefusRetractation refusEventuel(Commande commande, Instant maintenant) {
        if (commande.getStatut() == StatutCommande.REMBOURSEE
                || commande.getStatut() == StatutCommande.ANNULEE) {
            return MotifRefusRetractation.COMMANDE_CLOTUREE;
        }
        if (commande.getStatut() != StatutCommande.PAYEE) {
            return MotifRefusRetractation.COMMANDE_NON_PAYEE;
        }
        if (!maintenant.isBefore(commande.getDateCommande().plus(DELAI_LEGAL))) {
            return MotifRefusRetractation.DELAI_EXPIRE;
        }
        if (demandes.existsByCommandeAndStatut(commande, StatutDemandeAnnulation.EN_ATTENTE)) {
            return MotifRefusRetractation.DEMANDE_DEJA_EN_COURS;
        }
        return null;
    }

    private Commande commandeDuMembre(UUID reference, String email) {
        Commande commande = commandes.findByReference(reference)
                .orElseThrow(() -> new RessourceIntrouvableException("Commande", reference));
        if (!commande.getMembre().getEmail().equalsIgnoreCase(email)) {
            throw new RessourceIntrouvableException("Commande", reference);
        }
        return commande;
    }
}
