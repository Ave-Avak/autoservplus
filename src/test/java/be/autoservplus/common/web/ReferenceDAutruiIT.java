package be.autoservplus.common.web;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PrestationRepository;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.intervention.domain.Intervention;
import be.autoservplus.intervention.repository.InterventionRepository;
import be.autoservplus.intervention.service.GenerateurNumeroIntervention;
import be.autoservplus.reservation.domain.Motorisation;
import be.autoservplus.reservation.domain.PosteAtelier;
import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.domain.Vehicule;
import be.autoservplus.reservation.repository.PosteAtelierRepository;
import be.autoservplus.reservation.repository.RdvRepository;
import be.autoservplus.reservation.repository.VehiculeRepository;
import be.autoservplus.support.SocleIntegration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * La référence d'un autre membre répond 404, jamais 403, sur chaque nature d'écran membre
 * que les tests de leur module ne couvraient pas : rendez-vous, véhicule, intervention.
 * Commandes, factures, avoirs, annulation, messages, notifications et fichier d'agenda
 * ont déjà le leur.
 *
 * <p>Chaque adresse est d'abord ouverte par sa propriétaire, qui ne reçoit pas 404 : sans
 * ce contrôle, une adresse fausse répondrait 404 à tout le monde et le test passerait
 * sans rien prouver.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Référence d'un autre membre : 404 (intégration)")
class ReferenceDAutruiIT extends SocleIntegration {

    private static final String MARIE = "marie.autrui@exemple.be";
    private static final String PAUL = "paul.autrui@exemple.be";

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private VehiculeRepository vehicules;
    @Autowired private CategorieRepository categories;
    @Autowired private PrestationRepository prestations;
    @Autowired private PosteAtelierRepository postes;
    @Autowired private RdvRepository rdvs;
    @Autowired private InterventionRepository interventions;
    @Autowired private GenerateurNumeroIntervention numeros;

    private List<String> adressesDeMarie;

    @BeforeEach
    void preparer() {
        Utilisateur marie = new Utilisateur(MARIE, "$2a$12$h", "Dupont", "Marie", TypeUtilisateur.MEMBRE);
        marie.confirmerAdresseEmail();
        marie = utilisateurs.save(marie);
        Utilisateur paul = new Utilisateur(PAUL, "$2a$12$h", "Martin", "Paul", TypeUtilisateur.MEMBRE);
        paul.confirmerAdresseEmail();
        utilisateurs.save(paul);

        Vehicule golf = vehicules.save(new Vehicule(marie, "1-AUT-001", "VW", "Golf", Motorisation.DIESEL));
        Categorie entretien = categories.save(new Categorie("IT-AUT", "Entretien", TypeCategorie.SERVICE));
        Prestation vidange = prestations.save(new Prestation(entretien, "IT-AUT-VID", "Vidange",
                new BigDecimal("49.00"), 30));
        PosteAtelier pont = postes.save(new PosteAtelier("Pont autrui"));
        Rdv rdv = rdvs.saveAndFlush(new Rdv("RDV-AUT-0001", marie, golf, pont,
                LocalDate.of(2026, 12, 1).atTime(10, 0).atZone(ZoneId.of("Europe/Brussels")).toInstant(),
                Duration.ofMinutes(30), List.of(vidange), null));
        Intervention intervention = interventions.saveAndFlush(new Intervention(numeros.prochain(), rdv));

        adressesDeMarie = List.of(
                "/mes-rendez-vous/" + rdv.getReference(),
                "/mes-vehicules/" + golf.getReference() + "/modifier",
                "/mes-vehicules/" + golf.getReference() + "/historique",
                "/mes-interventions/" + intervention.getReference(),
                "/mes-interventions/" + intervention.getReference() + "/statut");
    }

    private int statut(String adresse, String membre) throws Exception {
        RequestPostProcessor qui = user(membre).roles("MEMBRE");
        return mvc.perform(get(adresse).with(qui)).andReturn().getResponse().getStatus();
    }

    @Test
    @DisplayName("la propriétaire ouvre chaque écran, un autre membre reçoit 404 et jamais 403")
    void autreMembre() throws Exception {
        for (String adresse : adressesDeMarie) {
            assertThat(statut(adresse, MARIE)).as("propriétaire sur " + adresse).isNotIn(403, 404).isLessThan(500);
            assertThat(statut(adresse, PAUL)).as("autre membre sur " + adresse).isEqualTo(404);
        }
    }
}
