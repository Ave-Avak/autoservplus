package be.autoservplus.pilotage.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Chiffre d'affaires facturé hors TVA d'une journée de l'atelier, zéro un jour sans facture (A25). */
public record JourChiffre(LocalDate jour, BigDecimal htva) {
}
