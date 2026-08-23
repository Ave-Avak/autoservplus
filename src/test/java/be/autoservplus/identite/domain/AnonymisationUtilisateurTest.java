package be.autoservplus.identite.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.context.support.StaticMessageSource;

import java.time.Instant;
import java.util.Locale;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Anonymisation du compte (F23, article 17 RGPD) : ce que l entite garantit
 * elle-meme, sans service ni base.
 */
@DisplayName("Utilisateur — anonymisation")
class AnonymisationUtilisateurTest {

    private static final Instant MAINTENANT = Instant.parse("2026-08-23T10:00:00Z");
    private static final String JETON = "anonyme-42@supprime.invalid";
    private static final String HACHAGE = "$2a$12$abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLMNOPQRS";

    private Utilisateur marie;

    @BeforeEach
    void setUp() {
        marie = new Utilisateur("marie@exemple.be", "$2a$12$empreinte", "Dupont", "Marie",
                TypeUtilisateur.MEMBRE);
        marie.setTelephone("+32 470 12 34 56");
        marie.setRue("Rue de la Loi");
        marie.setNumeroRue("16");
        marie.setCodePostal("1000");
        marie.setLocalite("Bruxelles");
        marie.setPays("France");
        marie.confirmerAdresseEmail();
        marie.enregistrerConnexionReussie(MAINTENANT.minusSeconds(3600));
    }

    @Test
    @DisplayName("vide toute donnee personnelle et pose le marqueur")
    void anonymisationComplete() {
        marie.anonymiser(JETON, HACHAGE, MAINTENANT);

        assertThat(marie.getEmail()).isEqualTo(JETON);
        assertThat(marie.getMotDePasseHache()).isEqualTo(HACHAGE);
        assertThat(marie.getTelephone()).isNull();
        assertThat(marie.getRue()).isNull();
        assertThat(marie.getNumeroRue()).isNull();
        assertThat(marie.getCodePostal()).isNull();
        assertThat(marie.getLocalite()).isNull();
        assertThat(marie.getAnonymiseLe()).isEqualTo(MAINTENANT);
        assertThat(marie.estAnonymise()).isTrue();
        assertThat(marie.getStatut()).isEqualTo(StatutUtilisateur.SUPPRIME);
    }

    @Test
    @DisplayName("la ligne reste vivante : deleted_at n'est jamais pose")
    void pasDeSuppressionLogique() {
        marie.anonymiser(JETON, HACHAGE, MAINTENANT);

        // Le @SQLRestriction de l entite masquerait la ligne, et une facture
        // conservee sept ans ne pourrait plus resoudre son titulaire.
        assertThat(marie.estSupprime()).isFalse();
        assertThat(marie.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("les champs NOT NULL recoivent un marqueur lisible, pas du vide")
    void marqueurLisible() {
        marie.anonymiser(JETON, HACHAGE, MAINTENANT);

        // nom et prenom portent @NotBlank : une chaine vide echouerait a la
        // validation. Le couple retenu compose exactement ce qu affichent les
        // ecrans qui lisent encore ce compte par relation.
        assertThat(marie.getPrenom()).isNotBlank();
        assertThat(marie.getNom()).isNotBlank();
        assertThat(marie.nomComplet()).isEqualTo("Client supprimé");
        // pays est NOT NULL : marqueur d absence, PAS un pays de substitution.
        // Reecrire « Belgique » n anonymiserait pas, cela affirmerait une residence
        // peut-etre fausse sur un dossier que la personne ne peut plus corriger.
        assertThat(marie.getPays())
                .isEqualTo(Utilisateur.PAYS_ANONYME)
                .isNotEqualTo("Belgique")
                .isNotEqualTo("France");
    }

    @Test
    @DisplayName("le marqueur se resout dans la langue du document")
    void marqueurTraduit() {
        StaticMessageSource messages = new StaticMessageSource();
        messages.addMessage(Utilisateur.CLE_MARQUEUR_ANONYME, Locale.FRENCH, "Client supprimé");
        messages.addMessage(Utilisateur.CLE_MARQUEUR_ANONYME, new Locale("nl"), "Verwijderde klant");

        // Avant anonymisation, le vrai nom : il ne se traduit pas.
        assertThat(marie.nomComplet(messages, new Locale("nl"))).isEqualTo("Marie Dupont");

        marie.anonymiser(JETON, HACHAGE, MAINTENANT);

        // Apres, le marqueur dans la langue du document : une facture regeneree pour
        // un client neerlandophone ne doit pas afficher « Client supprime ».
        assertThat(marie.nomComplet(messages, new Locale("nl"))).isEqualTo("Verwijderde klant");
        assertThat(marie.nomComplet(messages, Locale.FRENCH)).isEqualTo("Client supprimé");
    }

    @Test
    @DisplayName("le marqueur stocke et la valeur francaise de la cle ne peuvent pas diverger")
    void marqueurStockeCoherentAvecLaCle() throws Exception {
        // Le marqueur existe sous deux formes : les constantes stockees en base, que
        // nomComplet() compose pour le back-office, et la cle i18n que resolvent les
        // documents. Ce test interdit qu elles s ecartent l une de l autre.
        Properties fr = new Properties();
        try (var flux = new java.io.InputStreamReader(
                java.nio.file.Files.newInputStream(
                        java.nio.file.Path.of("src/main/resources/i18n/messages.properties")),
                java.nio.charset.StandardCharsets.UTF_8)) {
            fr.load(flux);
        }

        marie.anonymiser(JETON, HACHAGE, MAINTENANT);

        assertThat(fr.getProperty(Utilisateur.CLE_MARQUEUR_ANONYME))
                .as("La valeur francaise de la cle doit valoir ce que nomComplet() compose")
                .isEqualTo(marie.nomComplet());
    }

    @Test
    @DisplayName("efface aussi les traces de connexion")
    void tracesDeConnexionEffacees() {
        marie.enregistrerEchecConnexion(3, MAINTENANT.plusSeconds(900));
        marie.enregistrerJetonVerification("jeton", MAINTENANT.plusSeconds(60));

        marie.anonymiser(JETON, HACHAGE, MAINTENANT);

        // Donnees comportementales, pas comptables : elles partent avec le reste.
        assertThat(marie.getDerniereConnexion()).isNull();
        assertThat(marie.getTentativesEchouees()).isZero();
        assertThat(marie.getVerrouilleJusquA()).isNull();
        assertThat(marie.getJetonVerification()).isNull();
        assertThat(marie.isEmailVerifie()).isFalse();
    }

    @Test
    @DisplayName("un compte deja anonymise ne se re-anonymise pas")
    void pasDeDoubleAnonymisation() {
        marie.anonymiser(JETON, HACHAGE, MAINTENANT);

        assertThatThrownBy(() -> marie.anonymiser(JETON, HACHAGE, MAINTENANT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("deja anonymise");
    }

    @Test
    @DisplayName("un administrateur ne s'anonymise pas : ses decisions sont tracees")
    void administrateurRefuse() {
        Utilisateur admin = new Utilisateur("admin@autoservplus.be", "$2a$12$empreinte",
                "Garage", "Admin", TypeUtilisateur.ADMINISTRATEUR);

        // decide_par sur demande_annulation, auteur_id sur les historiques : vider
        // ces comptes romprait la tracabilite des decisions du garage.
        assertThatThrownBy(() -> admin.anonymiser(JETON, HACHAGE, MAINTENANT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("administrateur");
        assertThat(admin.getEmail()).isEqualTo("admin@autoservplus.be");
    }

    @Test
    @DisplayName("refuse un jeton, un hachage ou une date absents")
    void argumentsObligatoires() {
        assertThatThrownBy(() -> marie.anonymiser(null, HACHAGE, MAINTENANT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> marie.anonymiser(JETON, null, MAINTENANT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> marie.anonymiser(JETON, HACHAGE, null))
                .isInstanceOf(NullPointerException.class);
    }
}
