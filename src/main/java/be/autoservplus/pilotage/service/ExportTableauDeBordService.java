package be.autoservplus.pilotage.service;

import be.autoservplus.comptabilite.service.RedacteurCsv;
import be.autoservplus.pilotage.service.dto.JourChiffre;
import be.autoservplus.pilotage.service.dto.LigneClassement;
import be.autoservplus.pilotage.service.dto.MontantCategorie;
import be.autoservplus.pilotage.service.dto.PeriodeTableau;
import be.autoservplus.pilotage.service.dto.RepartitionStatut;
import be.autoservplus.pilotage.service.dto.TableauDeBord;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

/**
 * Export CSV du tableau de bord (A25) : les indicateurs d'une période, et le chiffre
 * d'affaires quotidien des trente derniers jours.
 *
 * <p>Les valeurs sont celles de l'écran, relues par {@link TableauDeBordService} : un
 * export qui recalculerait de son côté finirait par donner un autre chiffre que la
 * page d'où il est demandé.</p>
 *
 * <p>Les libellés suivent la langue de l'administrateur qui exporte, comme l'export
 * comptable. Les dates sont en jj/mm/aaaa et les montants à la virgule décimale, quelle
 * que soit cette langue : le fichier s'ouvre dans un tableur réglé pour la Belgique.</p>
 */
@Service
@PreAuthorize("hasRole('ADMINISTRATEUR')")
public class ExportTableauDeBordService {

    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final TableauDeBordService tableauDeBord;
    private final MessageSource messages;

    public ExportTableauDeBordService(TableauDeBordService tableauDeBord, MessageSource messages) {
        this.tableauDeBord = tableauDeBord;
        this.messages = messages;
    }

    /** Une ligne par indicateur : rubrique, indicateur, valeur. */
    public String indicateursEnCsv(PeriodeTableau periode) {
        TableauDeBord bord = tableauDeBord.pour(periode);
        RedacteurCsv csv = new RedacteurCsv(msg("admin.tableau.export.colonne.rubrique"),
                msg("admin.tableau.export.colonne.indicateur"), msg("admin.tableau.export.colonne.valeur"));

        String rubriquePeriode = msg("admin.tableau.periode");
        csv.ligne(rubriquePeriode, msg("admin.ventes.filtre.du"), periode.du().format(JOUR));
        csv.ligne(rubriquePeriode, msg("admin.ventes.filtre.au"), periode.au().format(JOUR));

        String finances = msg("admin.tableau.finances");
        csv.ligne(finances, msg("admin.tableau.ca-htva"), bord.chiffreAffaire().htva());
        csv.ligne(finances, msg("admin.tableau.ca-tvac"), bord.chiffreAffaire().tvac());
        csv.ligne(finances, msg("admin.tableau.factures"), bord.chiffreAffaire().nombre());
        csv.ligne(finances, msg("admin.tableau.avoirs"), bord.avoirs().htva());
        csv.ligne(finances, msg("admin.tableau.ca-net"), bord.chiffreAffaireNetHtva());
        csv.ligne(finances, msg("admin.tableau.commandes-payees"), bord.commandesPayees().nombre());
        csv.ligne(finances, msg("admin.tableau.panier-moyen"), bord.panierMoyenHtva());

        String clients = msg("admin.tableau.clients");
        csv.ligne(clients, msg("admin.tableau.nouveaux-clients"), bord.nouveauxClients());

        String rendezVous = msg("admin.tableau.rdv");
        csv.ligne(rendezVous, msg("admin.tableau.rdv.total"), bord.annulations().total());
        for (RepartitionStatut statut : bord.rendezVous()) {
            csv.ligne(rendezVous, msg("rdv.statut." + statut.statut()), statut.nombre());
        }
        csv.ligne(rendezVous, msg("admin.tableau.annulations.membre"), bord.annulations().parLeMembre());
        csv.ligne(rendezVous, msg("admin.tableau.annulations.garage"), bord.annulations().parLeGarage());
        csv.ligne(rendezVous, msg("admin.tableau.export.annulation"), bord.annulations().taux());
        csv.ligne(rendezVous, msg("admin.tableau.export.occupation"), bord.tauxOccupation());

        String prestations = msg("admin.tableau.top-prestations.titre");
        for (LigneClassement ligne : bord.topPrestations()) {
            csv.ligne(prestations, ligne.libelle(), ligne.quantite());
        }
        String pieces = msg("admin.tableau.top-pieces.titre");
        for (LigneClassement ligne : bord.topPieces()) {
            csv.ligne(pieces, ligne.libelle(), ligne.quantite());
        }
        String categories = msg("admin.tableau.categories");
        for (MontantCategorie categorie : bord.parCategorie()) {
            csv.ligne(categories, categorie.categorie(), categorie.htva());
        }
        return csv.texte();
    }

    /** Une ligne par jour, les trente derniers jours, aujourd'hui compris. */
    public String chiffreQuotidienEnCsv() {
        RedacteurCsv csv = new RedacteurCsv(msg("admin.tableau.colonne.jour"),
                msg("admin.tableau.ca-htva"));
        for (JourChiffre jour : tableauDeBord.chiffreDesTrenteDerniersJours()) {
            csv.ligne(jour.jour().format(JOUR), jour.htva());
        }
        return csv.texte();
    }

    private String msg(String cle) {
        return messages.getMessage(cle, null, LocaleContextHolder.getLocale());
    }
}
