package be.autoservplus.rgpd.service;

import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.identite.domain.Consentement;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.rgpd.repository.CommandeExportRepository;
import be.autoservplus.rgpd.repository.ConsentementExportRepository;
import be.autoservplus.rgpd.repository.InterventionExportRepository;
import be.autoservplus.rgpd.repository.RdvExportRepository;
import be.autoservplus.rgpd.repository.VehiculeExportRepository;
import be.autoservplus.rgpd.service.dto.ExportDonnees;
import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.domain.LignePanier;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Export des donnees personnelles d un membre (F22 — droit d acces, article 15
 * RGPD).
 *
 * <p>Service d agregation <b>strictement en lecture</b> : {@code readOnly = true}
 * sur toute la classe, et les repositories du module etendent
 * {@code Repository} sans methode d ecriture. Exercer un droit d acces ne modifie
 * rien.
 *
 * <p><b>Etancheite</b> : l identite vient du contexte de securite, transmise en
 * adresse de courriel par le controleur ({@code @AuthenticationPrincipal}), jamais
 * d un parametre de requete. Chaque requete du module filtre sur cette adresse : il
 * n existe aucun chemin par lequel l export d un membre pourrait contenir la donnee
 * d un autre — l ownership n est pas verifie apres coup, il est la condition meme du
 * chargement.
 *
 * <p><b>Chargement</b> : {@code open-in-view} etant desactive, chaque requete
 * charge d avance ce que la conversion en objet de transfert lira. Le cout est
 * borne — une requete par section, deux pour les commandes et leurs lignes —
 * independamment du volume : aucun N+1.
 *
 * <p><b>Perimetre non couvert en V1</b>, faute d entite JPA (les tables existent
 * au schema mais aucun code ne les alimente) : avis, messagerie, notifications.
 * L export ne comporte donc pas ces sections plutot que des tableaux vides
 * trompeurs. Les factures suivront le module facturation ; les commandes payees,
 * montants compris, sont deja restituees ici.
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("isAuthenticated()")
public class ExportDonneesService {

    private final UtilisateurRepository utilisateurs;
    private final VehiculeExportRepository vehicules;
    private final RdvExportRepository rendezVous;
    private final InterventionExportRepository interventions;
    private final CommandeExportRepository commandes;
    private final ConsentementExportRepository consentements;
    private final CatalogueTraitements catalogue;
    private final Clock horloge;

    public ExportDonneesService(UtilisateurRepository utilisateurs,
                                VehiculeExportRepository vehicules,
                                RdvExportRepository rendezVous,
                                InterventionExportRepository interventions,
                                CommandeExportRepository commandes,
                                ConsentementExportRepository consentements,
                                CatalogueTraitements catalogue,
                                Clock horloge) {
        this.utilisateurs = utilisateurs;
        this.vehicules = vehicules;
        this.rendezVous = rendezVous;
        this.interventions = interventions;
        this.commandes = commandes;
        this.consentements = consentements;
        this.catalogue = catalogue;
        this.horloge = horloge;
    }

    /**
     * Assemble l export complet du membre : ses donnees, le rappel legal du
     * traitement, et les notes d exclusion.
     *
     * <p>Le rappel legal et les exclusions sont resolus dans la <b>langue du
     * membre</b> (colonne {@code langue}), pas dans celle de la requete : le
     * fichier se relit longtemps apres la session qui l a produit.
     *
     * @throws RessourceIntrouvableException si l adresse ne correspond a aucun
     *         compte — impossible pour un utilisateur authentifie, mais un export
     *         ne se construit pas sur un titulaire suppose
     */
    public ExportDonnees assembler(String email) {
        Utilisateur membre = utilisateurs.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur", email));
        Locale langue = Locale.forLanguageTag(membre.getLangue().name());
        List<Consentement> preuves = consentements.pourMembre(email);

        return new ExportDonnees(
                Instant.now(horloge),
                new ExportDonnees.DonneesPersonnelles(
                        ExportDonnees.ProfilExport.de(membre, preuves),
                        vehicules.pourMembre(email).stream()
                                .map(ExportDonnees.VehiculeExport::de).toList(),
                        commandesAvecLignes(email),
                        rendezVous.pourMembre(email).stream()
                                .map(ExportDonnees.RdvExport::de).toList(),
                        interventions.pourMembre(email).stream()
                                .map(ExportDonnees.InterventionExport::de).toList(),
                        preuves.stream().map(ExportDonnees.ConsentementExport::de).toList(),
                        ExportDonnees.ConnexionExport.de(membre)),
                catalogue.informationsTraitement(langue),
                catalogue.exclusions(langue));
    }

    /**
     * Commandes du membre, chacune avec ses lignes. Les lignes de toutes les
     * commandes sont ramenees en une seule requete puis regroupees en memoire :
     * deux requetes au total, quel que soit le nombre de commandes.
     *
     * <p>Le cas « aucune commande » sort avant la seconde requete : un
     * {@code IN ()} vide n a pas de sens et n a pas a etre soumis a la base.
     */
    private List<ExportDonnees.CommandeExport> commandesAvecLignes(String email) {
        List<Commande> commandesDuMembre = commandes.pourMembre(email);
        if (commandesDuMembre.isEmpty()) {
            return List.of();
        }
        Map<Long, List<LignePanier>> lignesParCommande = commandes.lignesDe(commandesDuMembre)
                .stream()
                .collect(Collectors.groupingBy(ligne -> ligne.getCommande().getId()));

        return commandesDuMembre.stream()
                .map(commande -> ExportDonnees.CommandeExport.de(commande,
                        lignesParCommande.getOrDefault(commande.getId(), List.of())))
                .toList();
    }
}
