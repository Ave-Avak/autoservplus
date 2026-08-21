package be.autoservplus.reservation.web.dto;

import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.domain.StatutRdv;
import be.autoservplus.reservation.service.support.FormatageRdv;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Vue d un rendez-vous destinee au tableau de bord et aux ecrans admin du garage.
 *
 * <p>Contient les memes champs de presentation que {@link RdvVue}, plus le nom et
 * l email du membre (l admin voit qui a reserve) et cinq flags de transitions
 * possibles depuis l etat courant. Les flags de transition sont derives de la
 * machine a etats, avec deux gardes temporelles supplementaires :
 * {@code peutMarquerHonore} exige aussi que le RDV ait commence
 * ({@code debut <= maintenant} : on n accueille pas un client avant l heure),
 * et {@code peutMarquerAbsent} exige que le creneau soit ecoule
 * ({@code fin < maintenant} : on ne declare absent qu apres la fin, sinon le
 * client peut encore arriver en retard).</p>
 */
public record RdvVueAdmin(
        UUID reference,
        String numero,
        String statut,
        String statutLisible,
        String jourLisible,
        String heureDebut,
        String heureFin,
        String membreNom,
        String membreEmail,
        String vehicule,
        List<String> prestations,
        String montantTvac,
        String commentaire,
        String motifRefus,
        boolean peutConfirmer,
        boolean peutRefuser,
        boolean peutAnnuler,
        boolean peutMarquerHonore,
        boolean peutMarquerAbsent) {

    public static RdvVueAdmin de(Rdv rdv, ZoneId zone, Instant maintenant) {
        StatutRdv s = rdv.getStatut();
        boolean debutAtteint = !rdv.getDebut().isAfter(maintenant);
        boolean finPassee = rdv.getFin().isBefore(maintenant);
        return new RdvVueAdmin(
                rdv.getReference(),
                rdv.getNumero(),
                s.name(),
                FormatageRdv.statutLisible(s),
                FormatageRdv.jourLisible(rdv.getDebut(), zone),
                FormatageRdv.heureLisible(rdv.getDebut(), zone),
                FormatageRdv.heureLisible(rdv.getFin(), zone),
                rdv.getMembre().nomComplet(),
                rdv.getMembre().getEmail(),
                rdv.getVehicule().getMarque() + " " + rdv.getVehicule().getModele()
                        + " (" + rdv.getVehicule().getPlaque() + ")",
                rdv.getLignes().stream().map(l -> l.getPrestation().getLibelle()).toList(),
                FormatageRdv.euros(rdv.montantTvac()),
                rdv.getCommentaire(),
                rdv.getMotifRefus(),
                s.peutPasserA(StatutRdv.CONFIRME),
                s.peutPasserA(StatutRdv.REFUSE),
                s.peutPasserA(StatutRdv.ANNULE),
                s.peutPasserA(StatutRdv.HONORE) && debutAtteint,
                s.peutPasserA(StatutRdv.ABSENT) && finPassee);
    }
}
