package be.autoservplus.reservation.web;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PrestationRepository;
import be.autoservplus.identite.domain.Langue;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.reservation.domain.Motorisation;
import be.autoservplus.reservation.domain.PosteAtelier;
import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.domain.Vehicule;
import be.autoservplus.reservation.repository.PosteAtelierRepository;
import be.autoservplus.reservation.repository.RdvRepository;
import be.autoservplus.reservation.repository.VehiculeRepository;
import be.autoservplus.reservation.service.ExportAgendaService;
import be.autoservplus.reservation.service.dto.FichierAgenda;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Export iCalendar d un rendez-vous (F38), de l ecran au fichier servi.
 *
 * <p>Test d integration parce que trois choses ne se verifient qu ensemble : que la
 * route est ouverte au membre proprietaire, que le service resout bien l identite du
 * garage depuis la configuration, et que la reponse porte les en-tetes qui font
 * qu un navigateur remet le fichier a l application de calendrier plutot que de
 * l afficher.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@DisplayName("Export iCalendar d un rendez-vous (integration)")
class ExportAgendaIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    /** Le numero de rendez-vous est unique en base ; chaque test forge le sien. */
    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);

    private static final Instant DEBUT = Instant.parse("2026-09-16T08:00:00Z");

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private VehiculeRepository vehicules;
    @Autowired private CategorieRepository categories;
    @Autowired private PrestationRepository prestations;
    @Autowired private PosteAtelierRepository postes;
    @Autowired private RdvRepository rdvs;
    @Autowired private ExportAgendaService exportAgenda;

    private Utilisateur marie;
    private Utilisateur paul;
    private Vehicule golf;
    private Vehicule clio;
    private Prestation vidange;

    @BeforeEach
    void setUp() {
        String suffixe = UUID.randomUUID().toString().substring(0, 8);
        marie = utilisateurs.save(new Utilisateur("marie@exemple.be", "$2a$12$h", "Dupont", "Marie", TypeUtilisateur.MEMBRE));
        paul = utilisateurs.save(new Utilisateur("paul@exemple.be", "$2a$12$h", "Martin", "Paul", TypeUtilisateur.MEMBRE));
        golf = vehicules.save(new Vehicule(marie, "1-ABC-123", "Volkswagen", "Golf", Motorisation.DIESEL));
        clio = vehicules.save(new Vehicule(paul, "1-XYZ-789", "Renault", "Clio", Motorisation.ESSENCE));

        Categorie entretien = categories.save(new Categorie("IT-AG-" + suffixe, "Entretien", TypeCategorie.SERVICE));
        vidange = prestations.save(new Prestation(entretien, "IT-AG-VID-" + suffixe, "Vidange",
                new BigDecimal("49.00"), 60));
    }

    private Rdv rdvConfirme(Utilisateur membre, Vehicule vehicule) {
        Rdv rdv = rdvBrut(membre, vehicule);
        rdv.confirmer();
        return rdvs.saveAndFlush(rdv);
    }

    /**
     * Annule le pliage a 75 octets de la RFC 5545 avant toute recherche de texte.
     * Sans cela, un test ne prouverait rien de fiable : la valeur cherchee peut se
     * trouver a cheval sur deux lignes, et l assertion echouerait sur un fichier
     * parfaitement valide — ou pire, passerait par hasard tant que la valeur reste
     * courte, pour casser le jour ou le nom du garage s allonge.
     */
    private static String deplier(String ics) {
        return ics.replace("\r\n ", "");
    }

    private Rdv rdvBrut(Utilisateur membre, Vehicule vehicule) {
        // Chaque rendez-vous prend son propre poste : la contrainte d exclusion
        // btree_gist refuserait deux intervalles identiques sur le meme poste.
        PosteAtelier propre = postes.save(
                new PosteAtelier("Pont " + UUID.randomUUID().toString().substring(0, 8)));
        String numero = "RDV-2026-%04d".formatted(COMPTEUR.getAndIncrement());
        return new Rdv(numero, membre, vehicule, propre, DEBUT, Duration.ofMinutes(30),
                List.of(vidange), null);
    }

    @Nested
    @DisplayName("Contenu du fichier")
    @WithMockUser(username = "marie@exemple.be")
    class Contenu {

        @Test
        @DisplayName("est un calendrier RFC 5545 servi en text/calendar, en piece jointe")
        void entetesEtEnveloppe() throws Exception {
            Rdv rdv = rdvConfirme(marie, golf);

            mvc.perform(get("/mes-rendez-vous/{ref}/agenda.ics", rdv.getReference())
                            .header("Accept-Language", "fr"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith("text/calendar"))
                    // attachment et non inline : le fichier doit etre remis a
                    // l application de calendrier, pas affiche dans le navigateur.
                    .andExpect(header().string("Content-Disposition",
                            containsString("attachment")))
                    .andExpect(header().string("Content-Disposition",
                            containsString(".ics")))
                    .andExpect(content().string(containsString("BEGIN:VCALENDAR")))
                    .andExpect(content().string(containsString("END:VCALENDAR")));
        }

        @Test
        @DisplayName("porte le resume, le lieu de la configuration et les instants UTC")
        void champsDuVevent() throws Exception {
            Rdv rdv = rdvConfirme(marie, golf);

            String ics = deplier(telecharger(rdv));

            assertThat(ics)
                    .contains("SUMMARY:Rendez-vous au AutoServ+ SRL")
                    // L adresse vient de autoservplus.garage.* ; ses virgules sont
                    // echappees, sans quoi LOCATION serait scinde en trois valeurs.
                    .contains("LOCATION:AutoServ+ SRL\\, Rue de l'Atelier 12\\, 1000 Bruxelles\\, Belgique")
                    .contains("DTSTART:20260916T080000Z")
                    .contains("DTEND:20260916T090000Z")
                    .contains("TRIGGER:-PT24H");
        }

        /**
         * Le lien de retour doit etre ABSOLU : le fichier est lu dans l agenda du
         * membre, hors du site, ou un chemin relatif ne designe rien. Il vient de
         * {@code autoservplus.url-publique} et non de l URL de la requete, pour que
         * le fichier telecharge et celui joint au courriel soient identiques.
         */
        @Test
        @DisplayName("contient un lien absolu vers la fiche du rendez-vous")
        void lienDeRetourAbsolu() throws Exception {
            Rdv rdv = rdvConfirme(marie, golf);

            String ics = deplier(telecharger(rdv));

            assertThat(ics)
                    .contains("URL:http://localhost:8080/mes-rendez-vous/" + rdv.getReference())
                    .contains("Vidange")
                    .contains("Volkswagen Golf (1-ABC-123)");
        }

        /**
         * L UID est la reference du rendez-vous : deux telechargements successifs
         * decrivent le meme evenement, que le client de calendrier met a jour au lieu
         * de dupliquer. Un identifiant tire au hasard remplirait l agenda de doublons.
         */
        @Test
        @DisplayName("emploie un UID stable d un telechargement a l autre")
        void uidStable() throws Exception {
            Rdv rdv = rdvConfirme(marie, golf);

            String premier = deplier(telecharger(rdv));
            String second = deplier(telecharger(rdv));

            assertThat(premier).contains("UID:" + rdv.getReference() + "@autoservplus");
            assertThat(ligne(premier, "UID:")).isEqualTo(ligne(second, "UID:"));
        }

        private String telecharger(Rdv rdv) throws Exception {
            return mvc.perform(get("/mes-rendez-vous/{ref}/agenda.ics", rdv.getReference())
                            .header("Accept-Language", "fr"))
                    .andReturn().getResponse().getContentAsString();
        }

        private String ligne(String ics, String prefixe) {
            return ics.lines().filter(l -> l.startsWith(prefixe)).findFirst().orElseThrow();
        }
    }

    @Nested
    @DisplayName("Langue")
    @WithMockUser(username = "marie@exemple.be")
    class LangueServie {

        @Test
        @DisplayName("suit la langue de la requete pour le resume et le nom du fichier")
        void resumeEtNomTraduits() throws Exception {
            Rdv rdv = rdvConfirme(marie, golf);

            mvc.perform(get("/mes-rendez-vous/{ref}/agenda.ics?lang=nl", rdv.getReference()))
                    .andExpect(content().string(containsString("SUMMARY:Afspraak bij AutoServ+ SRL")))
                    .andExpect(header().string("Content-Disposition", containsString("afspraak-")));
        }

        /**
         * Pour le courriel, la langue vient du <b>profil</b> du membre et non de la
         * requete : c est l administrateur qui declenche l envoi, le document part
         * chez le membre. Meme regle que pour les factures PDF.
         */
        @Test
        @DisplayName("le fichier joint au courriel suit la langue du profil, pas celle de l admin")
        void courrielSuitLeProfil() {
            Utilisateur neerlandophone = utilisateurs.save(
                    new Utilisateur("jan@exemple.be", "$2a$12$h", "Jansen", "Jan", TypeUtilisateur.MEMBRE));
            neerlandophone.setLangue(Langue.nl);
            Vehicule polo = vehicules.save(
                    new Vehicule(neerlandophone, "1-JAN-001", "Volkswagen", "Polo", Motorisation.ESSENCE));

            FichierAgenda fichier = exportAgenda.pourLeCourriel(rdvConfirme(neerlandophone, polo));

            assertThat(deplier(fichier.contenu())).contains("SUMMARY:Afspraak bij AutoServ+ SRL");
            assertThat(fichier.nomFichier()).startsWith("afspraak-");
        }
    }

    @Nested
    @DisplayName("Acces")
    class Acces {

        /**
         * Le rendez-vous d un autre membre repond 404 et non 403 : le patron du
         * projet ne revele pas l existence d une ressource dont on n est pas
         * titulaire.
         */
        @Test
        @WithMockUser(username = "marie@exemple.be")
        @DisplayName("le rendez-vous d un autre membre repond 404")
        void rdvDAutrui() throws Exception {
            Rdv dePaul = rdvConfirme(paul, clio);

            mvc.perform(get("/mes-rendez-vous/{ref}/agenda.ics", dePaul.getReference()))
                    .andExpect(status().isNotFound());
        }

        /**
         * Une demande encore EN_ATTENTE n a pas de date arretee. Deposee dans
         * l agenda du membre, elle s y installerait comme un fait et rien ne
         * viendrait l en retirer si le garage refusait : le fichier vit chez lui.
         */
        @Test
        @WithMockUser(username = "marie@exemple.be")
        @DisplayName("un rendez-vous non confirme repond 404")
        void rdvNonConfirme() throws Exception {
            Rdv enAttente = rdvs.saveAndFlush(rdvBrut(marie, golf));

            mvc.perform(get("/mes-rendez-vous/{ref}/agenda.ics", enAttente.getReference()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("un visiteur anonyme est renvoye vers la connexion")
        void anonyme() throws Exception {
            Rdv rdv = rdvConfirme(marie, golf);

            mvc.perform(get("/mes-rendez-vous/{ref}/agenda.ics", rdv.getReference()).with(anonymous()))
                    .andExpect(status().is3xxRedirection());
        }
    }

    @Nested
    @DisplayName("Ecran de detail")
    @WithMockUser(username = "marie@exemple.be")
    class EcranDetail {

        @Test
        @DisplayName("propose le lien d export sur un rendez-vous confirme")
        void lienPresentSiConfirme() throws Exception {
            Rdv rdv = rdvConfirme(marie, golf);

            mvc.perform(get("/mes-rendez-vous/{ref}", rdv.getReference())
                            .header("Accept-Language", "fr"))
                    .andExpect(content().string(containsString("agenda.ics")))
                    .andExpect(content().string(containsString("Ajouter à mon agenda")));
        }

        /**
         * Le bouton et le controleur appliquent la meme condition : sans ce test,
         * l ecran pourrait proposer un lien que la route refuse — ou l inverse.
         */
        @Test
        @DisplayName("ne le propose pas sur une demande en attente")
        void lienAbsentSiEnAttente() throws Exception {
            Rdv enAttente = rdvs.saveAndFlush(rdvBrut(marie, golf));

            String page = mvc.perform(get("/mes-rendez-vous/{ref}", enAttente.getReference())
                            .header("Accept-Language", "fr"))
                    .andReturn().getResponse().getContentAsString();

            assertThat(page).doesNotContain("agenda.ics");
        }
    }

    /** Le service reste appelable hors requete HTTP : c est le chemin du courriel. */
    @Test
    @DisplayName("le service produit le fichier hors contexte web")
    void horsRequete() {
        Rdv rdv = rdvConfirme(marie, golf);

        FichierAgenda fichier = exportAgenda.pourLeMembre(
                rdv.getReference(), "marie@exemple.be", Locale.FRENCH);

        assertThat(fichier.nomFichier()).endsWith(".ics");
        assertThat(fichier.contenu()).startsWith("BEGIN:VCALENDAR");
        assertThat(fichier.octets()).isNotEmpty();
    }
}
