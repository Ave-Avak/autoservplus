package be.autoservplus.reservation.service.dto;

import be.autoservplus.reservation.web.dto.RdvVueAdmin;

import java.time.LocalDate;
import java.util.List;

/**
 * Agenda du back-office sur une période de jours, bornes comprises.
 *
 * @param periodeInversee vrai quand la fin demandée précède le début : la liste est
 *                        alors vide et la page affiche un message plutôt qu'une erreur
 */
public record AgendaAdmin(LocalDate du, LocalDate au, boolean periodeInversee,
                          List<RdvVueAdmin> rendezVous) {
}
