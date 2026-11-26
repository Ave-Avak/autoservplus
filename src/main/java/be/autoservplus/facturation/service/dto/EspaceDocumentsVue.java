package be.autoservplus.facturation.service.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Espace documents du membre : ses factures, ses notes de crédit et ses interventions,
 * de la plus récente à la plus ancienne, filtrées sur une année si elle est choisie.
 *
 * @param annees années où le membre a au moins un document, la plus récente d'abord
 * @param annee  année retenue, nulle quand tout est affiché
 */
public record EspaceDocumentsVue(List<FactureDuMembre> factures, List<AvoirDuMembre> avoirs,
                                 List<DossierDuMembre> dossiers, List<Integer> annees, Integer annee) {

    public boolean estVide() {
        return factures.isEmpty() && avoirs.isEmpty() && dossiers.isEmpty();
    }

    /**
     * Facture vue du membre.
     *
     * @param cleOrigine   clé i18n de l'origine, commande ou intervention
     * @param numeroAvoir  note de crédit qui annule la facture, nulle sinon
     */
    public record FactureDuMembre(UUID reference, String numero, LocalDate dateEmission, String cleOrigine,
                                  String numeroOrigine, String montantTvac, String numeroAvoir) {

        public boolean estAnnulee() {
            return numeroAvoir != null;
        }
    }

    public record AvoirDuMembre(UUID reference, String numero, LocalDate dateEmission, String numeroFacture,
                                String montantTvac) {
    }

    /** Intervention du membre, avec le statut tel qu'il le perçoit (RM-16). */
    public record DossierDuMembre(UUID reference, String numero, LocalDate date, String cleStatut,
                                  String vehicule) {
    }
}
