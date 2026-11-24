package be.autoservplus.galerie.web;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PrestationRepository;
import be.autoservplus.galerie.domain.Photo;
import be.autoservplus.galerie.repository.PhotoRepository;
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
import be.autoservplus.stockage.service.StockageMedia;
import be.autoservplus.support.SocleIntegration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Photos d'intervention : servies au titulaire et au back-office seulement, jamais par
 * l'adresse publique des images, jamais mises en cache partagé (V68).
 *
 * <p>Avant V68, une photo d'intervention se lisait par {@code /images/galerie/{id}},
 * ouverte à tous, en comptant de un en un. Chaque test ci-dessous ferme l'un des
 * chemins par lesquels un tiers pourrait encore l'atteindre.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Photos d'intervention (intégration)")
class PhotoInterventionIT extends SocleIntegration {

    private static final String MARIE = "marie.photos@exemple.be";
    private static final String PAUL = "paul.photos@exemple.be";
    private static final byte[] JPEG = jpegMinimal();

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private VehiculeRepository vehicules;
    @Autowired private CategorieRepository categories;
    @Autowired private PrestationRepository prestations;
    @Autowired private PosteAtelierRepository postes;
    @Autowired private RdvRepository rdvs;
    @Autowired private InterventionRepository interventions;
    @Autowired private GenerateurNumeroIntervention numeros;
    @Autowired private PhotoRepository photos;
    @Autowired private StockageMedia stockage;

    private UUID intervention;
    private UUID photo;
    private UUID interventionDePaul;
    private UUID photoDePaul;

    @BeforeEach
    void preparer() {
        Utilisateur marie = utilisateurs.save(new Utilisateur(MARIE, "$2a$12$h",
                "Dupont", "Marie", TypeUtilisateur.MEMBRE));
        Utilisateur paul = utilisateurs.save(new Utilisateur(PAUL, "$2a$12$h",
                "Martin", "Paul", TypeUtilisateur.MEMBRE));
        Categorie entretien = categories.save(new Categorie("IT-PHO", "Entretien", TypeCategorie.SERVICE));
        Prestation vidange = prestations.save(new Prestation(entretien, "IT-PHO-VID", "Vidange photos",
                new BigDecimal("49.00"), 30));
        PosteAtelier pont = postes.save(new PosteAtelier("Pont photos"));

        Intervention deMarie = interventionDe(marie, "1-PHO-001", "RDV-PHO-0001", pont, vidange, 10);
        Intervention dePaul = interventionDe(paul, "1-PHO-002", "RDV-PHO-0002", pont, vidange, 14);
        intervention = deMarie.getReference();
        interventionDePaul = dePaul.getReference();
        photo = deposer(deMarie);
        photoDePaul = deposer(dePaul);
    }

    private static byte[] jpegMinimal() {
        byte[] contenu = new byte[64];
        contenu[0] = (byte) 0xFF;
        contenu[1] = (byte) 0xD8;
        contenu[2] = (byte) 0xFF;
        return contenu;
    }

    private Intervention interventionDe(Utilisateur membre, String plaque, String numeroRdv,
                                        PosteAtelier poste, Prestation prestation, int heure) {
        Vehicule vehicule = vehicules.save(new Vehicule(membre, plaque, "VW", "Golf", Motorisation.DIESEL));
        Rdv rdv = rdvs.saveAndFlush(new Rdv(numeroRdv, membre, vehicule, poste,
                LocalDate.of(2026, 12, 1).atTime(heure, 0).atZone(ZoneId.of("Europe/Brussels")).toInstant(),
                Duration.ofMinutes(30), List.of(prestation), null));
        return interventions.saveAndFlush(new Intervention(numeros.prochain(), rdv));
    }

    private UUID deposer(Intervention it) {
        String chemin = stockage.enregistrer(
                new MockMultipartFile("fichiers", "avant.jpg", "image/jpeg", JPEG), "interventions");
        return photos.saveAndFlush(Photo.pourIntervention(it, chemin, "Plaquettes usées", (short) 0))
                .getReference();
    }

    private static RequestPostProcessor marie() {
        return user(MARIE).roles("MEMBRE");
    }

    private static RequestPostProcessor admin() {
        return user("admin.photos@exemple.be").roles("ADMINISTRATEUR");
    }

    @Test
    @DisplayName("le titulaire lit la photo de son intervention, sans cache partagé")
    void titulaire() throws Exception {
        mvc.perform(get("/mes-interventions/{i}/photos/{p}", intervention, photo).with(marie()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("Cache-Control", allOf(
                        containsString("no-store"), containsString("private"))));
    }

    @Test
    @DisplayName("un autre membre reçoit 404, comme pour une référence inconnue")
    void autreMembre() throws Exception {
        mvc.perform(get("/mes-interventions/{i}/photos/{p}", interventionDePaul, photoDePaul).with(marie()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("la photo d'une autre intervention ne se lit pas sous la sienne")
    void photoDUneAutreIntervention() throws Exception {
        mvc.perform(get("/mes-interventions/{i}/photos/{p}", intervention, photoDePaul).with(marie()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/admin/interventions/{i}/photos/{p}", intervention, photoDePaul).with(admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("un visiteur est renvoyé à la connexion")
    void visiteur() throws Exception {
        mvc.perform(get("/mes-interventions/{i}/photos/{p}", intervention, photo))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/connexion"));
    }

    @Test
    @DisplayName("l'adresse publique des images ne sert pas une photo d'intervention, même au garage")
    void adressePublique() throws Exception {
        mvc.perform(get("/images/galerie/{p}", photo))
                .andExpect(status().isNotFound());
        mvc.perform(get("/images/galerie/{p}", photo).with(admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("le back-office lit la photo, sans cache partagé, et un membre n'atteint pas sa route")
    void backOffice() throws Exception {
        mvc.perform(get("/admin/interventions/{i}/photos/{p}", intervention, photo).with(admin()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")));
        mvc.perform(get("/admin/interventions/{i}/photos/{p}", intervention, photo).with(marie()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("la suppression par l'adresse d'une autre fiche ne touche pas la photo")
    void suppressionHorsDeSaFiche() throws Exception {
        mvc.perform(post("/admin/galerie/interventions/{i}/{p}/supprimer", intervention, photoDePaul)
                        .with(admin()).with(csrf()))
                .andExpect(status().isNotFound());

        assertThat(photos.findAll()).anyMatch(p -> p.getReference().equals(photoDePaul));
    }
}
