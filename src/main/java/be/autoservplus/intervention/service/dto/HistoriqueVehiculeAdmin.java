package be.autoservplus.intervention.service.dto;

import be.autoservplus.intervention.domain.Intervention;
import be.autoservplus.intervention.domain.LigneIntervention;
import be.autoservplus.intervention.domain.StatutIntervention;
import be.autoservplus.reservation.service.support.FormatageRdv;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Historique complet d'un véhicule pour le garage (F21) : statut technique, toutes les
 * lignes, refusées et en attente comprises, montants et note interne (A16).
 */
public record HistoriqueVehiculeAdmin(
        UUID vehiculeReference,
        String vehicule,
        String proprietaire,
        boolean vehiculeRetire,
        List<Entree> interventions) {

    public static HistoriqueVehiculeAdmin de(UUID vehiculeReference, String vehicule,
                                             String proprietaire, boolean vehiculeRetire,
                                             List<Intervention> interventions, ZoneId zone) {
        return new HistoriqueVehiculeAdmin(vehiculeReference, vehicule, proprietaire,
                vehiculeRetire, interventions.stream().map(it -> Entree.de(it, zone)).toList());
    }

    public record Entree(
            UUID reference,
            String numero,
            LocalDate date,
            StatutIntervention statut,
            String rdvNumero,
            List<Ligne> lignes,
            String totalFacturableTvac,
            String noteInterne) {

        static Entree de(Intervention it, ZoneId zone) {
            return new Entree(
                    it.getReference(),
                    it.getNumero(),
                    LocalDate.ofInstant(it.dateDeReference(), zone),
                    it.getStatut(),
                    it.getRdv() == null ? null : it.getRdv().getNumero(),
                    it.getLignes().stream().map(Ligne::de).toList(),
                    FormatageRdv.euros(it.totalFacturableTvac()),
                    it.getNoteInterne());
        }
    }

    /** {@code etat} est un suffixe de clé : initiale, ajoutee, attente, refusee. */
    public record Ligne(String type, String libelle, short quantite, String totalHtva,
                        String etat, boolean facturable) {

        static Ligne de(LigneIntervention l) {
            return new Ligne(l.getType().name(), l.getLibelleFige(), l.getQuantite(),
                    FormatageRdv.euros(l.totalHtva()), etat(l), l.estFacturable());
        }

        private static String etat(LigneIntervention l) {
            if (l.estRefusee()) return "refusee";
            if (l.estEnAttenteValidation()) return "attente";
            return l.estDuDevisInitial() ? "initiale" : "ajoutee";
        }
    }
}
