package be.autoservplus.intervention.web.dto;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.intervention.domain.Intervention;
import be.autoservplus.reservation.domain.Motorisation;
import be.autoservplus.reservation.domain.PosteAtelier;
import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.domain.Vehicule;
import be.autoservplus.vente.domain.Commande;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Origine et titulaire d'une intervention au back-office : rendez-vous, commande ou entrée
 * directe. La liste en déduisait l'origine de la présence d'un membre, et présentait une
 * intervention née d'une commande comme une entrée directe.
 */
class InterventionVueAdminTest {

    private static final Instant DEBUT = Instant.parse("2026-09-14T08:00:00Z");
    private static final ZoneId BRUXELLES = ZoneId.of("Europe/Brussels");

    private final Utilisateur marie = new Utilisateur("marie@exemple.be", "$2a$12$h", "Dupont", "Marie", TypeUtilisateur.MEMBRE);
    private final Vehicule golf = new Vehicule(marie, "1-ABC-123", "VW", "Golf", Motorisation.DIESEL);
    private final Prestation vidange = new Prestation(
            new Categorie("ENT", "Entretien", TypeCategorie.SERVICE), "VID", "Vidange", new BigDecimal("49.00"), 30);

    /** Sans rendez-vous : le schéma admet une intervention rattachée au seul véhicule (V32). */
    private Intervention sansRendezVous(Commande commande) {
        Intervention it = BeanUtils.instantiateClass(Intervention.class);
        ReflectionTestUtils.setField(it, "numero", "INT-2026-0007");
        ReflectionTestUtils.setField(it, "vehicule", golf);
        ReflectionTestUtils.setField(it, "commande", commande);
        return it;
    }

    @Test
    @DisplayName("née d'un rendez-vous : son numéro, le membre du rendez-vous")
    void depuisUnRendezVous() {
        Rdv rdv = new Rdv("RDV-2026-0001", marie, golf, new PosteAtelier("Pont 1"), DEBUT,
                Duration.ofMinutes(30), List.of(vidange), null);

        InterventionVueAdmin vue = InterventionVueAdmin.de(new Intervention("INT-2026-0001", rdv), BRUXELLES);

        assertThat(vue.rdvNumero()).isEqualTo("RDV-2026-0001");
        assertThat(vue.commandeNumero()).isNull();
        assertThat(vue.membreNom()).isEqualTo("Marie Dupont");
    }

    @Test
    @DisplayName("née d'une commande : son numéro, le membre de la commande")
    void depuisUneCommande() {
        Commande commande = new Commande("CMD-2026-0012", marie,
                new BigDecimal("65.00"), new BigDecimal("13.65"), new BigDecimal("78.65"), DEBUT);

        InterventionVueAdmin vue = InterventionVueAdmin.de(sansRendezVous(commande), BRUXELLES);

        assertThat(vue.rdvNumero()).isNull();
        assertThat(vue.commandeNumero()).isEqualTo("CMD-2026-0012");
        assertThat(vue.membreNom()).isEqualTo("Marie Dupont");
        assertThat(vue.membreEmail()).isEqualTo("marie@exemple.be");
    }

    @Test
    @DisplayName("entrée directe : aucune origine, le propriétaire du véhicule")
    void entreeDirecte() {
        InterventionVueAdmin vue = InterventionVueAdmin.de(sansRendezVous(null), BRUXELLES);

        assertThat(vue.rdvNumero()).isNull();
        assertThat(vue.commandeNumero()).isNull();
        assertThat(vue.membreNom()).isEqualTo("Marie Dupont");
    }
}
