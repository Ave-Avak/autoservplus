package be.autoservplus.reservation.web.dto;

import be.autoservplus.i18n.LanguesSupportees;
import be.autoservplus.reservation.domain.JoursFeries;
import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.domain.StatutRdv;
import be.autoservplus.reservation.service.support.FormatageRdv;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Vue d un rendez-vous destinee au tableau de bord et aux ecrans admin du garage.
 *
 * <p>Contient les memes champs de presentation que {@link RdvVue}, plus le nom et
 * l email du membre (l admin voit qui a reserve) et cinq flags de transitions
 * possibles depuis l etat courant. Chaque flag reprend la garde de l'entité qui
 * porte la transition, machine à états et conditions de temps comprises : un bouton
 * affiché est un POST accepté.</p>
 *
 * <p>{@code debut} et {@code fin} sont les heures locales de l'atelier, pour les
 * écrans traduits qui les formatent selon la langue de l'interface. Les champs
 * {@code jourLisible} et {@code heureDebut} restent pour les écrans en français.</p>
 */
public record RdvVueAdmin(
        UUID reference,
        String numero,
        String statut,
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
        boolean peutMarquerAbsent,
        boolean peutDeplacer,
        boolean jourFerie) {

    public static RdvVueAdmin de(Rdv rdv, ZoneId zone, Instant maintenant) {
        StatutRdv s = rdv.getStatut();
        // Un rendez-vous ne fige aucun libellé : la prestation porte son nom courant, dans
        // la langue de l'écran de l'administrateur.
        Locale langue = LanguesSupportees.deLEcran();
        return new RdvVueAdmin(
                rdv.getReference(),
                rdv.getNumero(),
                s.name(),
                FormatageRdv.heureLisible(rdv.getDebut(), zone),
                FormatageRdv.heureLisible(rdv.getFin(), zone),
                LocalDateTime.ofInstant(rdv.getDebut(), zone),
                LocalDateTime.ofInstant(rdv.getFin(), zone),
                rdv.getPoste().getLibelle(),
                rdv.getMembre().nomComplet(),
                rdv.getMembre().getEmail(),
                rdv.getVehicule().getMarque() + " " + rdv.getVehicule().getModele()
                        + " (" + rdv.getVehicule().getPlaque() + ")",
                rdv.getLignes().stream().map(l -> l.getPrestation().libelle(langue).texte()).toList(),
                FormatageRdv.eurosEcran(rdv.montantTvac()),
                rdv.getCommentaire(),
                rdv.getMotifRefus(),
                rdv.peutEtreConfirme(maintenant),
                s.peutPasserA(StatutRdv.REFUSE),
                rdv.peutEtreAnnuleParLeGarage(maintenant),
                rdv.peutEtreMarqueHonore(maintenant),
                rdv.peutEtreMarqueAbsent(maintenant),
                rdv.peutEtreModifieParLeGarage(maintenant),
                JoursFeries.estFerie(LocalDate.ofInstant(rdv.getDebut(), zone)));
    }
}
