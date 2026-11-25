package be.autoservplus.support;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.facturation.domain.Facture;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.intervention.domain.Intervention;
import be.autoservplus.reservation.domain.Motorisation;
import be.autoservplus.reservation.domain.PosteAtelier;
import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.domain.Vehicule;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/** Factures construites sans base, pour les tests unitaires de la facturation. */
public final class FacturesDeTest {

    private FacturesDeTest() {
    }

    /** Facture d'atelier d'une intervention née d'un rendez-vous : 100,00 HTVA, 121,00 TVAC. */
    public static Facture factureDAtelier(Instant emission) {
        Utilisateur paul = MembresDeTest.membre("paul@exemple.be", "$2a$12$h", "Martin", "Paul");
        Vehicule golf = new Vehicule(paul, "1-ABC-123", "VW", "Golf", Motorisation.DIESEL);
        Prestation vidange = new Prestation(new Categorie("ATE", "Atelier", TypeCategorie.SERVICE),
                "VID", "Vidange", new BigDecimal("100.00"), 30);
        Intervention intervention = new Intervention("INT-2026-0007", new Rdv("RDV-2026-0001", paul, golf,
                new PosteAtelier("Pont 1"), emission, Duration.ofMinutes(30), List.of(vidange), null));
        return Facture.pourIntervention("2026-0050", (short) 2026, 50, intervention, paul,
                new BigDecimal("100.00"), new BigDecimal("21.00"), new BigDecimal("121.00"),
                new BigDecimal("21.00"), emission, LocalDate.ofInstant(emission, ZoneOffset.UTC));
    }
}
