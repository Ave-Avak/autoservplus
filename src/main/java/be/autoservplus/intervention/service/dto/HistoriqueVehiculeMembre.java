package be.autoservplus.intervention.service.dto;

import be.autoservplus.intervention.domain.Intervention;
import be.autoservplus.intervention.domain.LigneIntervention;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Historique d'entretien d'un véhicule tel que son propriétaire le voit (F21).
 *
 * <p>Le statut n'existe ici que sous sa clé perçue (RM-16) : le statut technique ne
 * quitte pas le service. La note interne de l'atelier (A16) n'a aucun champ, ni ici ni
 * dans {@link Entree} : ce qui n'est pas dans le type ne peut pas fuiter au rendu.</p>
 */
public record HistoriqueVehiculeMembre(
        UUID vehiculeReference,
        String vehicule,
        List<Entree> interventions) {

    public static HistoriqueVehiculeMembre de(UUID vehiculeReference, String vehicule,
                                              List<Intervention> interventions, ZoneId zone) {
        return new HistoriqueVehiculeMembre(vehiculeReference, vehicule,
                interventions.stream().map(it -> Entree.de(it, zone)).toList());
    }

    /**
     * Une intervention de l'historique. Les lignes sont les seules facturables, comme
     * sur l'écran de suivi : une ligne refusée n'a pas été exécutée, une ligne en
     * attente pas encore acceptée.
     */
    public record Entree(
            UUID reference,
            String numero,
            LocalDate date,
            String clePercue,
            List<Ligne> lignes) {

        static Entree de(Intervention it, ZoneId zone) {
            return new Entree(
                    it.getReference(),
                    it.getNumero(),
                    LocalDate.ofInstant(it.dateDeReference(), zone),
                    it.getStatut().clePercue(),
                    it.getLignes().stream()
                            .filter(LigneIntervention::estFacturable)
                            .map(Ligne::de)
                            .toList());
        }
    }

    /** Prestation ou pièce, sans prix unitaire, au même niveau de détail que le suivi. */
    public record Ligne(String type, String libelle, short quantite) {

        static Ligne de(LigneIntervention l) {
            return new Ligne(l.getType().name(), l.getLibelleFige(), l.getQuantite());
        }
    }
}
