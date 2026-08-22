package be.autoservplus.rgpd.service;

import be.autoservplus.rgpd.service.dto.ExportDonnees;
import be.autoservplus.rgpd.service.dto.InformationsTraitement;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Format du fichier remis a la personne : nommage des champs, dates, montants et
 * encodage. Ce sont des engagements vis-a-vis d un destinataire exterieur au
 * projet, pas des details d implementation — d ou un test dedie.
 *
 * <p>Les assertions portent sur l arbre JSON relu, pas sur la chaine brute :
 * l indentation ne doit pas rendre le test cassant, seule la structure compte.
 */
@DisplayName("SerialiseurExportJson")
class SerialiseurExportJsonTest {

    private final SerialiseurExportJson serialiseur = new SerialiseurExportJson();
    private final ObjectMapper lecteur = new ObjectMapper();

    private static ExportDonnees exportMinimal() {
        return new ExportDonnees(
                Instant.parse("2026-08-22T07:30:00Z"),
                new ExportDonnees.DonneesPersonnelles(
                        new ExportDonnees.ProfilExport("Dupont", "Marie", "marie@exemple.be",
                                "+32470000000",
                                new ExportDonnees.AdresseExport("Rue Neuve", "12", "1000",
                                        "Bruxelles", "Belgique"),
                                "fr", true, "ACTIF", Instant.parse("2026-01-05T10:00:00Z")),
                        List.of(),
                        ExportDonnees.PanierExport.vide(),
                        List.of(new ExportDonnees.CommandeExport("CMD-2026-0001", "PAYEE",
                                Instant.parse("2026-03-01T08:00:00Z"),
                                Instant.parse("2026-03-01T08:05:00Z"), null, null,
                                new BigDecimal("39.98"), new BigDecimal("8.40"),
                                new BigDecimal("48.38"), List.of())),
                        List.of(new ExportDonnees.FactureExport("2026-0007",
                                Instant.parse("2026-03-01T08:05:00Z"),
                                new BigDecimal("39.98"), new BigDecimal("8.40"),
                                new BigDecimal("48.38"), "CMD-2026-0001", false)),
                        List.of(), List.of(), List.of(),
                        new ExportDonnees.ConnexionExport(
                                Instant.parse("2026-08-20T06:00:00Z"), true, (short) 0, null)),
                new InformationsTraitement("Responsable", List.of(), List.of(), List.of(),
                        List.of(), List.of(), "Exercice", "Note"),
                new ExportDonnees.Exclusions("mot de passe", "carte", "secrets"));
    }

    private String texte() {
        return new String(serialiseur.enJson(exportMinimal()), StandardCharsets.UTF_8);
    }

    private JsonNode arbre() throws Exception {
        return lecteur.readTree(texte());
    }

    @Test
    @DisplayName("nomme les champs en snake_case, lisibles hors de Java")
    void champsEnSnakeCase() throws Exception {
        JsonNode racine = arbre();

        assertThat(racine.has("donnees_personnelles")).isTrue();
        assertThat(racine.has("informations_traitement")).isTrue();
        assertThat(racine.path("donnees_personnelles").has("connexion_et_securite")).isTrue();
        assertThat(racine.path("donnees_personnelles").path("profil").has("date_creation_compte"))
                .isTrue();
        assertThat(racine.path("donnees_personnelles").path("profil").path("adresse")
                .has("code_postal")).isTrue();
        // Le nom Java ne doit jamais transparaitre dans le document livre.
        assertThat(texte()).doesNotContain("donneesPersonnelles").doesNotContain("codePostal");
    }

    @Test
    @DisplayName("ecrit les dates en ISO 8601 et non en horodatage numerique")
    void datesIso8601() throws Exception {
        JsonNode racine = arbre();

        assertThat(racine.path("genere_le").isTextual()).isTrue();
        assertThat(racine.path("genere_le").asText()).isEqualTo("2026-08-22T07:30:00Z");
        assertThat(racine.path("donnees_personnelles").path("profil")
                .path("date_creation_compte").asText()).isEqualTo("2026-01-05T10:00:00Z");
    }

    @Test
    @DisplayName("ecrit les montants en nombres JSON, a l'echelle des centimes")
    void montantsNumeriques() throws Exception {
        JsonNode commande = arbre().path("donnees_personnelles").path("commandes").get(0);

        // Un montant entre guillemets ne serait plus exploitable a la reprise
        // (portabilite, article 20) sans reparsing.
        assertThat(commande.path("montant_htva").isNumber()).isTrue();
        assertThat(commande.path("montant_htva").decimalValue()).isEqualByComparingTo("39.98");
        assertThat(commande.path("montant_tva").decimalValue()).isEqualByComparingTo("8.40");
        assertThat(commande.path("montant_tvac").decimalValue()).isEqualByComparingTo("48.38");
    }

    @Test
    @DisplayName("ecrit la facture avec son numero legal, sans chemin de fichier")
    void factureSansBinaire() throws Exception {
        JsonNode facture = arbre().path("donnees_personnelles").path("factures").get(0);

        assertThat(facture.path("numero").asText()).isEqualTo("2026-0007");
        assertThat(facture.path("date_emission").asText()).isEqualTo("2026-03-01T08:05:00Z");
        assertThat(facture.path("numero_commande").asText()).isEqualTo("CMD-2026-0001");
        assertThat(facture.path("montant_tvac").decimalValue()).isEqualByComparingTo("48.38");
        assertThat(facture.path("pdf_archive").asBoolean()).isFalse();
        // Metadonnees seulement : ni binaire encode, ni emplacement sur le serveur.
        assertThat(texte()).doesNotContain(".pdf");
    }

    @Test
    @DisplayName("conserve les champs vides plutot que de les omettre")
    void champsNulsConserves() throws Exception {
        // Une absence explicite informe la personne que la donnee n est pas
        // detenue ; un champ escamote laisse croire a un oubli.
        JsonNode commande = arbre().path("donnees_personnelles").path("commandes").get(0);

        assertThat(commande.has("date_annulation")).isTrue();
        assertThat(commande.path("date_annulation").isNull()).isTrue();
    }

    @Test
    @DisplayName("encode en UTF-8 et indente le document pour une lecture humaine")
    void encodageEtIndentation() {
        String document = texte();

        assertThat(document).contains("Bruxelles");
        assertThat(document.lines().count()).isGreaterThan(10);
    }
}
