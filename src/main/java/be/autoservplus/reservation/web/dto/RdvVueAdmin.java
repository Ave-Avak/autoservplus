package be.autoservplus.reservation.web.dto;

import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.domain.StatutRdv;
import be.autoservplus.reservation.service.support.FormatageRdv;

import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Vue d un rendez-vous destinee au tableau de bord et aux ecrans admin du garage.
 *
 * <p>Contient les memes champs de presentation que {@link RdvVue}, plus le nom et
 * l email du membre (l admin voit qui a reserve) et cinq flags de transitions
 * possibles depuis l etat courant. Les flags sont derives directement de la machine
 * a etats ({@code statut.peutPasserA(...)}), le DTO ne peut donc pas diverger du
 * domaine : si {@link StatutRdv#peutPasserA} change demain, cette vue suit.</p>
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

    public static RdvVueAdmin de(Rdv rdv, ZoneId zone) {
        StatutRdv s = rdv.getStatut();
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
                s.peutPasserA(StatutRdv.HONORE),
                s.peutPasserA(StatutRdv.ABSENT));
    }
}
