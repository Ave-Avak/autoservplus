package be.autoservplus.reservation.web.dto;

import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.domain.StatutRdv;
import be.autoservplus.reservation.service.support.FormatageRdv;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Vue d un rendez-vous destinee au tableau de bord et aux ecrans admin du garage.
 *
 * <p>Contient les memes champs de presentation que {@link RdvVue}, plus le nom et
 * l email du membre (l admin voit qui a reserve) et cinq flags de transitions
 * possibles depuis l etat courant. Confirmation et annulation suivent les gardes de
 * l'entité, qui les refusent une fois le début passé. Les autres flags sont derives de la
 * machine a etats, avec deux gardes temporelles supplementaires :
 * {@code peutMarquerHonore} exige aussi que le RDV ait commence
 * ({@code debut <= maintenant} : on n accueille pas un client avant l heure),
 * et {@code peutMarquerAbsent} exige que le creneau soit ecoule
 * ({@code fin < maintenant} : on ne declare absent qu apres la fin, sinon le
 * client peut encore arriver en retard).</p>
 *
 * <p>{@code debut} et {@code fin} sont les heures locales de l'atelier, pour les
 * écrans traduits qui les formatent selon la langue de l'interface. Les champs
 * {@code jourLisible} et {@code heureDebut} restent pour les écrans en français.</p>
 */
public record RdvVueAdmin(
        UUID reference,
        String numero,
        String statut,
        String statutLisible,
        String jourLisible,
        String heureDebut,
        String heureFin,
        LocalDateTime debut,
        LocalDateTime fin,
        String poste,
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
                LocalDateTime.ofInstant(rdv.getDebut(), zone),
                LocalDateTime.ofInstant(rdv.getFin(), zone),
                rdv.getPoste().getLibelle(),
                rdv.getMembre().nomComplet(),
                rdv.getMembre().getEmail(),
                rdv.getVehicule().getMarque() + " " + rdv.getVehicule().getModele()
                        + " (" + rdv.getVehicule().getPlaque() + ")",
                rdv.getLignes().stream().map(l -> l.getPrestation().getLibelle()).toList(),
                FormatageRdv.euros(rdv.montantTvac()),
                rdv.getCommentaire(),
                rdv.getMotifRefus(),
                rdv.peutEtreConfirme(maintenant),
                s.peutPasserA(StatutRdv.REFUSE),
                rdv.peutEtreAnnuleParLeGarage(maintenant),
                s.peutPasserA(StatutRdv.HONORE) && debutAtteint,
                s.peutPasserA(StatutRdv.ABSENT) && finPassee);
    }
}
