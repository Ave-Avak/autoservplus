package be.autoservplus.vente.service;

import be.autoservplus.common.exception.RefusTraduit;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.common.web.PeriodeFiltre;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.service.AuteurCourant;
import be.autoservplus.reservation.repository.ParametreAtelierRepository;
import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.domain.ModificationCommande;
import be.autoservplus.vente.domain.Paiement;
import be.autoservplus.vente.domain.StatutCommande;
import be.autoservplus.vente.domain.StatutPaiement;
import be.autoservplus.vente.repository.CommandeRepository;
import be.autoservplus.vente.repository.ModificationCommandeRepository;
import be.autoservplus.vente.repository.PaiementRepository;
import be.autoservplus.vente.web.dto.CommandeAdminDetail;
import be.autoservplus.vente.web.dto.CommandeAdminLigne;
import be.autoservplus.vente.web.dto.ModificationCommandeVue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Consultation des commandes depuis le back-office.
 *
 * <p>Les jours de la période sont ceux de l'atelier, comme pour l'export comptable : une
 * commande passée à 23 h 30 le dernier jour appartient à ce jour-là, quel que soit le
 * fuseau du serveur.</p>
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasRole('ADMINISTRATEUR')")
public class AdminCommandeService {

    private final CommandeRepository commandes;
    private final PaiementRepository paiements;
    private final ParametreAtelierRepository parametres;
    private final ModificationCommandeRepository modifications;
    private final AuteurCourant auteurCourant;
    private final Clock horloge;
    private final int taillePage;

    /** @param taillePage lignes par page des listes du back-office, 50 par défaut */
    public AdminCommandeService(CommandeRepository commandes, PaiementRepository paiements,
                                ParametreAtelierRepository parametres,
                                ModificationCommandeRepository modifications,
                                AuteurCourant auteurCourant, Clock horloge,
                                @Value("${autoservplus.back-office.taille-page:50}") int taillePage) {
        this.commandes = commandes;
        this.paiements = paiements;
        this.parametres = parametres;
        this.modifications = modifications;
        this.auteurCourant = auteurCourant;
        this.horloge = horloge;
        this.taillePage = taillePage;
    }

    /** Période saisie, complétée par les trente derniers jours de l'atelier. */
    public PeriodeFiltre periode(LocalDate du, LocalDate au) {
        return PeriodeFiltre.de(du, au, LocalDate.ofInstant(horloge.instant(), zone()));
    }

    /**
     * @param statut    {@code null} pour tous les statuts
     * @param recherche numéro de commande ou adresse du membre, en partie, sans casse
     * @param page      numéro de page à partir de zéro
     */
    public Page<CommandeAdminLigne> rechercher(PeriodeFiltre periode, StatutCommande statut,
                                               String recherche, int page) {
        PageRequest demande = PageRequest.of(Math.max(page, 0), taillePage);
        if (periode.inversee()) {
            return Page.empty(demande);
        }
        ZoneId zone = zone();
        return commandes.rechercherAdmin(periode.debutPour(recherche, zone), periode.finPour(recherche, zone),
                        statut == null ? EnumSet.allOf(StatutCommande.class) : EnumSet.of(statut),
                        motif(recherche), demande)
                .map(commande -> CommandeAdminLigne.de(commande, zone));
    }

    /** Commandes d'un membre, pour sa fiche (A12). */
    public List<CommandeAdminLigne> commandesDuMembre(UUID referenceMembre) {
        ZoneId zone = zone();
        return commandes.commandesDuMembre(referenceMembre).stream()
                .map(commande -> CommandeAdminLigne.de(commande, zone))
                .toList();
    }

    public CommandeAdminDetail detail(UUID reference) {
        Commande commande = commandes.findByReference(reference)
                .orElseThrow(() -> new RessourceIntrouvableException("Commande", reference.toString()));
        return CommandeAdminDetail.de(commande, commandes.lignesDe(commande),
                commandes.lignesServiceDe(commande), paiementAbouti(commande),
                commandes.aUneDemandeDeRetractation(commande.getId()), zone());
    }

    /**
     * Date de remise des pièces, saisie depuis le détail de la commande.
     *
     * @throws RefusTraduit date absente, commande sans pièce ou déjà visée par une demande
     *                      de rétractation, non payée, date future ou antérieure au paiement
     */
    @Transactional
    public void enregistrerRemise(UUID reference, LocalDate jour) {
        if (jour == null) {
            throw new RefusTraduit("admin.ventes.remise.erreur.obligatoire",
                    "Indiquez la date de remise.");
        }
        Commande commande = commandes.findByReference(reference)
                .orElseThrow(() -> new RessourceIntrouvableException("Commande", reference.toString()));
        if (commandes.lignesDe(commande).isEmpty()) {
            throw new RefusTraduit("admin.ventes.remise.erreur.sans-piece",
                    "Une commande sans pièce n'a pas de date de remise.");
        }
        // Une demande se juge sur la date connue quand elle a été faite : la corriger
        // ensuite déplacerait le délai sous les pieds du membre.
        if (commandes.aUneDemandeDeRetractation(commande.getId())) {
            throw new RefusTraduit("admin.ventes.remise.erreur.demande",
                    "Une demande de rétractation existe : la date de remise ne se modifie plus.");
        }
        Utilisateur auteur = auteurCourant.resoudre();
        if (auteur == null) {
            throw new IllegalStateException(
                    "Aucun auteur authentifié : une date de remise doit être imputable.");
        }
        ZoneId zone = zone();
        LocalDate avant = commande.getDateRemise();
        commande.enregistrerRemise(jour, LocalDate.ofInstant(horloge.instant(), zone),
                commande.getDatePaiement() == null ? jour
                        : LocalDate.ofInstant(commande.getDatePaiement(), zone));
        // Même transaction que la saisie : une date changée sans sa trace est ce que
        // l'historique existe pour empêcher. Une date renvoyée inchangée n'écrit rien.
        if (!jour.equals(avant)) {
            modifications.save(ModificationCommande.dateRemise(commande, avant, jour,
                    horloge.instant(), auteur));
        }
    }

    /** Historique des saisies de la commande, pour son détail. */
    public List<ModificationCommandeVue> historiqueDe(UUID reference) {
        ZoneId zone = zone();
        return modifications.historiqueDe(reference).stream()
                .map(modification -> ModificationCommandeVue.de(modification, zone))
                .toList();
    }

    /** Même règle que le détail du membre : REMBOURSE prouve que l'encaissement a eu lieu. */
    private Paiement paiementAbouti(Commande commande) {
        return paiements.findByCommandeAndStatutIn(commande,
                        List.of(StatutPaiement.REUSSI, StatutPaiement.REMBOURSE))
                .stream()
                .findFirst()
                .orElse(null);
    }

    private ZoneId zone() {
        return parametres.courants().zone();
    }

    /** Sans recherche, {@code %} rend tout sans paramètre nul à typer par la base. */
    static String motif(String recherche) {
        return recherche == null || recherche.isBlank()
                ? "%"
                : "%" + recherche.strip().toLowerCase(Locale.ROOT) + "%";
    }
}
