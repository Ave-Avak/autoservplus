package be.autoservplus.identite.service;

import be.autoservplus.identite.domain.ChampCompte;
import be.autoservplus.identite.domain.Langue;
import be.autoservplus.identite.domain.ModificationCompte;
import be.autoservplus.identite.domain.StatutUtilisateur;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.ModificationCompteRepository;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.rgpd.service.ExportDonneesService;
import be.autoservplus.rgpd.service.SuppressionCompteService;
import be.autoservplus.rgpd.service.dto.ExportDonnees;
import be.autoservplus.support.SocleIntegration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Historique des modifications d'un compte (V57) : écrit par le profil et par le
 * changement d'adresse, restitué par l'export F22, vidé par l'anonymisation F23.
 */
@SpringBootTest
@Transactional
@DisplayName("Historique des modifications du compte (intégration)")
class HistoriqueModificationCompteIT extends SocleIntegration {

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);
    private static final String MOT_DE_PASSE = "phrase de passe du test";

    @PersistenceContext private EntityManager entites;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private ModificationCompteRepository modifications;
    @Autowired private PasswordEncoder encodeur;
    @Autowired private ProfilService profil;
    @Autowired private ChangementEmailService changementEmail;
    @Autowired private ExportDonneesService export;
    @Autowired private SuppressionCompteService suppression;
    @Autowired private JdbcTemplate jdbc;

    private Utilisateur membre;

    @BeforeEach
    void setUp() {
        String email = "historique-" + COMPTEUR.getAndIncrement() + "@exemple.be";
        membre = new Utilisateur(email, encodeur.encode(MOT_DE_PASSE), "Dupont", "Marie",
                TypeUtilisateur.MEMBRE);
        membre.confirmerAdresseEmail();
        membre.setStatut(StatutUtilisateur.ACTIF);
        membre = utilisateurs.saveAndFlush(membre);
        connecter(email);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static void connecter(String email) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                email, null, List.of(new SimpleGrantedAuthority("ROLE_MEMBRE"))));
    }

    private List<ModificationCompte> historique() {
        entites.flush();
        entites.clear();
        return modifications.historiqueDe(membre.getId());
    }

    private void enregistrerProfil(String nom, String rue, String codePostal, String localite) {
        profil.enregistrer(membre.getEmail(), "Marie", nom, null, rue, null, codePostal,
                localite, "Belgique", Langue.fr);
    }

    @Test
    @DisplayName("le profil modifié par le titulaire laisse une ligne par champ changé, sans motif")
    void profilParLeTitulaire() {
        enregistrerProfil("Durand", "Rue Neuve", "1000", "Bruxelles");

        assertThat(historique())
                .extracting(ModificationCompte::getChamp, ModificationCompte::getValeurAvant,
                        ModificationCompte::getValeurApres, ModificationCompte::getMotif,
                        ModificationCompte::parTitulaire)
                .containsExactlyInAnyOrder(
                        tuple(ChampCompte.NOM, "Dupont", "Durand", null, true),
                        tuple(ChampCompte.RUE, null, "Rue Neuve", null, true),
                        tuple(ChampCompte.CODE_POSTAL, null, "1000", null, true),
                        tuple(ChampCompte.LOCALITE, null, "Bruxelles", null, true));
    }

    @Test
    @DisplayName("un formulaire renvoyé sans changement n'écrit rien")
    void sansChangement() {
        enregistrerProfil("Dupont", null, null, null);

        assertThat(historique()).isEmpty();
    }

    @Test
    @DisplayName("le changement d'adresse de courriel confirmé est historisé, auteur titulaire")
    void changementAdresse() {
        String ancienne = membre.getEmail();
        String nouvelle = "nouvelle-" + ancienne;
        membre.demanderChangementEmail(nouvelle, JetonLien.empreinte("jeton-historique"),
                Instant.now().plus(1, ChronoUnit.HOURS));
        utilisateurs.saveAndFlush(membre);

        changementEmail.confirmer("jeton-historique");

        assertThat(historique())
                .extracting(ModificationCompte::getChamp, ModificationCompte::getValeurAvant,
                        ModificationCompte::getValeurApres, ModificationCompte::parTitulaire)
                .containsExactly(tuple(ChampCompte.EMAIL, ancienne, nouvelle, true));
    }

    @Test
    @DisplayName("l'export F22 restitue les modifications avec leurs valeurs")
    void export() {
        enregistrerProfil("Durand", null, null, null);
        entites.flush();

        ExportDonnees donnees = export.assembler(membre.getEmail());

        assertThat(donnees.donneesPersonnelles().modificationsDuCompte())
                .extracting(ExportDonnees.ModificationCompteExport::champ,
                        ExportDonnees.ModificationCompteExport::valeurAvant,
                        ExportDonnees.ModificationCompteExport::valeurApres,
                        ExportDonnees.ModificationCompteExport::parAdministrateur)
                .containsExactly(tuple("NOM", "Dupont", "Durand", false));
    }

    @Test
    @DisplayName("l'anonymisation F23 vide les valeurs et garde le champ et la date")
    void anonymisation() {
        enregistrerProfil("Durand", "Rue Neuve", "1000", "Bruxelles");
        entites.flush();

        suppression.supprimer(membre.getEmail(), MOT_DE_PASSE,
                SuppressionCompteService.MOT_DE_CONFIRMATION);

        List<ModificationCompte> apres = historique();
        assertThat(apres).hasSize(4);
        assertThat(apres).allSatisfy(ligne -> {
            assertThat(ligne.getValeurAvant()).isNull();
            assertThat(ligne.getValeurApres()).isNull();
            assertThat(ligne.getChamp()).isNotNull();
            assertThat(ligne.getHorodatage()).isNotNull();
        });
    }

    @Test
    @DisplayName("la base refuse une modification par un tiers sans motif")
    void motifExigeEnBase() {
        Utilisateur admin = new Utilisateur("admin-historique-" + COMPTEUR.getAndIncrement()
                + "@exemple.be", encodeur.encode(MOT_DE_PASSE), "Garage", "Chef",
                TypeUtilisateur.ADMINISTRATEUR);
        admin = utilisateurs.saveAndFlush(admin);

        Long cible = membre.getId();
        Long auteur = admin.getId();
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO historique_modification_compte
                    (utilisateur_id, champ, valeur_avant, valeur_apres, horodatage, auteur_id)
                VALUES (?, 'NOM', 'Dupont', 'Durand', now(), ?)
                """, cible, auteur))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_histo_compte_motif");
    }
}
