package be.autoservplus.reservation.service.dto;

import be.autoservplus.reservation.domain.PosteAtelier;

import java.time.Instant;

/**
 * Heure de depart proposee au membre, avec le nombre de postes encore libres.
 *
 * <p>Le poste retenu est le premier libre dans l ordre d affichage ; l admin pourra
 * reaffecter ensuite. Le membre ne choisit pas le poste : il ne voit qu une heure.</p>
 */
public record CreneauDisponible(Instant debut, Instant fin, PosteAtelier poste, int postesLibres) {
}