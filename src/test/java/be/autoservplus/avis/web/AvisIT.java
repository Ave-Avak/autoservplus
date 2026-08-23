package be.autoservplus.avis.web;

import be.autoservplus.avis.domain.Avis;
import be.autoservplus.avis.repository.AvisRepository;
import be.autoservplus.avis.service.AdminAvisService;
import be.autoservplus.avis.service.AvisService;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.repository.PrestationRepository;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.intervention.domain.Intervention;
import be.autoservplus.intervention.repository.InterventionRepository;
import be.autoservplus.intervention.service.GenerateurNumeroIntervention;
import be.autoservplus.intervention.service.InterventionService;
import be.autoservplus.reservation.domain.Motorisation;
import be.autoservplus.reservation.domain.PosteAtelier;
import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.domain.Vehicule;
import be.autoservplus.reservation.repository.PosteAtelierRepository;
import be.autoservplus.reservation.repository.RdvRepository;
import be.autoservplus.reservation.repository.VehiculeRepository;
import be.autoservplus.rgpd.service.SuppressionCompteService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Avis de bout en bout (BL-4), sur un PostgreSQL reel.
 *
 * <p>Trois choses ne peuvent se verifier qu ici : les contraintes du socle V7 jamais
 * exercees jusqu a present ({@code ck_avis_note}, {@code uq_avis_intervention}),
 * l <b>extension de F23</b> qui neutralise le commentaire a l anonymisation, et la
 * double garde de l ecran de moderation (URL puis methode).</p>
 *
 * <p>Sans {@code @Transactional} de classe : l anonymisation flushe et enchaine
 * plusieurs ecritures, qu une transaction de test rollbackee masquerait.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@WithMockUser(username = "fixture@exemple.be")
@DisplayName("Avis (integration)")
class AvisIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);
    private static final String MOT_DE_PASSE = "MotDePasseTresLong2026!";

    @Autowired private MockMvc mvc;
    @Autowired private AvisRepository avis;
    @Autowired private AvisService service;
    @Autowired private AdminAvisService adminAvis;
    @Autowired private SuppressionCompteService suppression;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private VehiculeRepository vehicules;
    @Autowired private RdvRepository rdvs;
    @Autowired private PosteAtelierRepository postes;
    @Autowired private PrestationRepository prestations;
    @Autowired private InterventionRepository interventions;
    @Autowired private InterventionService interventionService;
    @Autowired private GenerateurNumeroIntervention numeros;
    @Autowired private PasswordEncoder encodeur;
    @Autowired private TransactionTemplate transactions;

    /** Membre, vehicule, RDV et intervention menee jusqu a TERMINEE. */
    private Contexte contexteNotable() {
        int n = COMPTEUR.getAndIncrement();
        String email = "avis" + n + "@exemple.be";
        UUID reference = transactions.execute(statut -> {
            Utilisateur membre = utilisateurs.saveAndFlush(new Utilisateur(
                    email, encodeur.encode(MOT_DE_PASSE), "Test", "Alex", TypeUtilisateur.MEMBRE));
            Vehicule vehicule = vehicules.saveAndFlush(new Vehicule(
                    membre, "9-AV%03d-99".formatted(n), "Renault", "Clio", Motorisation.ESSENCE));
            Prestation prestation = prestations.findByActifTrueOrderByLibelleAsc().get(0);
            PosteAtelier poste = postes.findAll().get(0);
            Rdv rdv = rdvs.saveAndFlush(new Rdv("RDV-IT-AVIS-%04d".formatted(n), membre, vehicule,
                    poste, Instant.parse("2027-03-01T06:00:00Z").plus(Duration.ofHours(n)),
                    Duration.ofMinutes(30), List.of(prestation), null));
            return interventions.saveAndFlush(new Intervention(numeros.prochain(), rdv))
                    .getReference();
        });
        // demarrer et terminer sont reserves a l administrateur. C'est la fixture, pas
        // l'objet du test : on lui donne son propre contexte plutot que d'imposer le
        // role ADMINISTRATEUR aux tests qui doivent justement s'en passer.
        enAdministrateur(() -> {
            interventionService.demarrer(reference);
            interventionService.terminer(reference);
        });
        return new Contexte(email, reference);
    }

    private void enAdministrateur(Runnable action) {
        SecurityContext avant = SecurityContextHolder.getContext();
        try {
            SecurityContextHolder.setContext(new SecurityContextImpl(
                    new UsernamePasswordAuthenticationToken("fixture@autoservplus.be", "n/a",
                            List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRATEUR")))));
            action.run();
        } finally {
            SecurityContextHolder.setContext(avant);
        }
    }

    private record Contexte(String email, UUID referenceIntervention) {}

    @Nested
    @DisplayName("Depot et contraintes du socle")
    class Depot {

        @Test
        @WithMockUser
        @DisplayName("la base accepte un avis sur une intervention terminee")
        void depotAccepte() {
            Contexte c = contexteNotable();

            Avis depose = service.deposer(c.email(), c.referenceIntervention(),
                    (short) 5, "Travail impeccable.");

            assertThat(depose.getId()).isNotNull();
            assertThat(depose.isPublie()).isTrue();
        }

        @Test
        @WithMockUser
        @DisplayName("uq_avis_intervention interdit le second avis, meme si le controle applicatif est contourne")
        void unSeulAvisParIntervention() {
            Contexte c = contexteNotable();
            service.deposer(c.email(), c.referenceIntervention(), (short) 4, "Premier.");

            // Ecriture directe par le repository : on court-circuite volontairement la
            // garde du service pour verifier que la BASE tient seule. C'est elle qui
            // protege de la course entre deux onglets, pas le controle applicatif.
            assertThatThrownBy(() -> transactions.executeWithoutResult(statut -> {
                Utilisateur membre = utilisateurs.findByEmailIgnoreCase(c.email()).orElseThrow();
                Intervention it = interventions.findByReference(c.referenceIntervention())
                        .orElseThrow();
                avis.saveAndFlush(new Avis(membre, it, (short) 1, "Second.", Instant.now()));
            })).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @WithMockUser
        @DisplayName("la note alimente la moyenne publique de la prestation travaillee")
        void moyennePublique() {
            Contexte c = contexteNotable();
            UUID prestation = transactions.execute(statut ->
                    prestations.findByActifTrueOrderByLibelleAsc().get(0).getReference());

            service.deposer(c.email(), c.referenceIntervention(), (short) 5, null);

            assertThat(service.synthese(prestation).aDesAvis()).isTrue();
        }
    }

    @Nested
    @DisplayName("Extension de F23 : anonymisation du commentaire")
    class Anonymisation {

        @Test
        @WithMockUser
        @DisplayName("le commentaire est efface, la note est conservee et l avis reste publie")
        void commentaireNeutralise() {
            Contexte c = contexteNotable();
            Avis depose = service.deposer(c.email(), c.referenceIntervention(),
                    (short) 5, "Merci Alex Test, plaque 9-AV001-99.");
            Long id = depose.getId();

            suppression.supprimer(c.email(), MOT_DE_PASSE,
                    SuppressionCompteService.MOT_DE_CONFIRMATION);

            Avis apres = avis.findById(id).orElseThrow();
            assertThat(apres.getCommentaire())
                    .as("le champ libre pouvait contenir nom et plaque")
                    .isNull();
            assertThat(apres.getNote())
                    .as("un chiffre de 1 a 5 ne designe personne et nourrit une moyenne deja affichee")
                    .isEqualTo((short) 5);
            assertThat(apres.isPublie())
                    .as("depublier serait une decision commerciale, pas une mesure RGPD")
                    .isTrue();
        }

        @Test
        @WithMockUser
        @DisplayName("une suppression sans avis se deroule normalement")
        void aucunAvisADepublier() {
            Contexte c = contexteNotable();

            suppression.supprimer(c.email(), MOT_DE_PASSE,
                    SuppressionCompteService.MOT_DE_CONFIRMATION);

            assertThat(utilisateurs.findByEmailIgnoreCase(c.email())).isEmpty();
        }
    }

    @Nested
    @DisplayName("Moderation : double garde")
    class Moderation {

        @Test
        @WithMockUser(username = "membre@exemple.be", roles = "MEMBRE")
        @DisplayName("niveau URL : un membre n atteint pas /admin/avis")
        void urlReserveeAuxAdministrateurs() throws Exception {
            mvc.perform(get("/admin/avis"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = "admin@autoservplus.be", roles = "ADMINISTRATEUR")
        @DisplayName("l administrateur consulte la liste, masques compris")
        void administrateurAutorise() throws Exception {
            mvc.perform(get("/admin/avis")).andExpect(status().isOk());
        }

        @Test
        @WithMockUser(username = "membre@exemple.be", roles = "MEMBRE")
        @DisplayName("niveau methode : le service refuse meme sans passer par l URL")
        void serviceRedouble() {
            Contexte c = contexteNotable();
            Avis depose = service.deposer(c.email(), c.referenceIntervention(), (short) 3, null);

            // Appel direct au service, sans requete HTTP : si la protection d URL
            // /admin/** venait a sauter, c'est cette garde qui tient encore.
            assertThatThrownBy(() -> adminAvis.masquer(depose.getReference()))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @WithMockUser(username = "admin@autoservplus.be", roles = "ADMINISTRATEUR")
        @DisplayName("masquer retire de la fiche publique sans effacer la ligne")
        void masquageRetireDeLaSynthese() throws Exception {
            Contexte c = contexteNotable();
            // deposer identifie le membre par son parametre, pas par le contexte de
            // securite : l administrateur authentifie ici satisfait isAuthenticated()
            // et l ownership est verifie contre c.email().
            Avis depose = service.deposer(c.email(), c.referenceIntervention(),
                    (short) 2, "A revoir.");

            mvc.perform(post("/admin/avis/{ref}/masquer", depose.getReference())
                            .with(user("admin@autoservplus.be").roles("ADMINISTRATEUR"))
                            .with(csrf()))
                    .andExpect(status().is3xxRedirection());

            Avis apres = avis.findById(depose.getId()).orElseThrow();
            assertThat(apres.isPublie()).isFalse();
            assertThat(apres.getCommentaire())
                    .as("masquer n est pas supprimer : le garage doit pouvoir justifier le retrait")
                    .isEqualTo("A revoir.");
        }
    }
}
