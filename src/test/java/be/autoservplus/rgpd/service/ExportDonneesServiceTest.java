package be.autoservplus.rgpd.service;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.identite.domain.Consentement;
import be.autoservplus.identite.domain.Langue;
import be.autoservplus.identite.domain.TypeDocumentConsentement;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.intervention.domain.Intervention;
import be.autoservplus.reservation.domain.Motorisation;
import be.autoservplus.reservation.domain.PosteAtelier;
import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.domain.Vehicule;
import be.autoservplus.rgpd.repository.CommandeExportRepository;
import be.autoservplus.rgpd.repository.ConsentementExportRepository;
import be.autoservplus.rgpd.repository.InterventionExportRepository;
import be.autoservplus.rgpd.repository.RdvExportRepository;
import be.autoservplus.rgpd.repository.VehiculeExportRepository;
import be.autoservplus.rgpd.service.dto.ExportDonnees;
import be.autoservplus.rgpd.service.dto.FichierExport;
import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.domain.LignePanier;
import be.autoservplus.vente.domain.Panier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Agregation de l export du droit d acces (F22).
 *
 * <p>Le {@link CatalogueTraitements} n est pas bouchonne : le rappel legal fait
 * partie de ce que l export doit contenir, un bouchon rendrait la verification
 * vide de sens. Le serialiseur non plus — les assertions d exclusion (mot de passe,
 * carte bancaire) portent sur le document reellement produit, seul endroit ou une
 * fuite serait visible.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ExportDonneesService")
class ExportDonneesServiceTest {

    private static final String EMAIL = "marie@exemple.be";
    private static final String EMPREINTE =
            "$2a$12$abcdefghijklmnopqrstuvOQ0RvNbYQ6cUqf6mE1nGxU8rWJl3Xy";
    private static final Instant MAINTENANT = Instant.parse("2026-08-22T07:30:00Z");
    private static final Instant CREATION_COMPTE = Instant.parse("2026-01-05T10:00:00Z");

    @Mock private UtilisateurRepository utilisateurs;
    @Mock private VehiculeExportRepository vehicules;
    @Mock private RdvExportRepository rendezVous;
    @Mock private InterventionExportRepository interventions;
    @Mock private CommandeExportRepository commandes;
    @Mock private ConsentementExportRepository consentements;
    @Mock private PasswordEncoder encodeur;

    private ExportDonneesService service;
    private RegistreExportsRecents registre;
    private final SerialiseurExportJson serialiseur = new SerialiseurExportJson();

    private Utilisateur marie;
    private Vehicule golf;
    private Prestation vidange;
    private Piece plaquettes;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("i18n/messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);

        Clock horloge = Clock.fixed(MAINTENANT, ZoneId.of("Europe/Brussels"));
        registre = new RegistreExportsRecents(horloge);
        service = new ExportDonneesService(utilisateurs, vehicules, rendezVous, interventions,
                commandes, consentements, new CatalogueTraitements(messages), serialiseur,
                registre, encodeur, horloge);

        marie = new Utilisateur(EMAIL, EMPREINTE, "Dupont", "Marie", TypeUtilisateur.MEMBRE);
        marie.setTelephone("+32470000000");
        marie.setRue("Rue Neuve");
        marie.setNumeroRue("12");
        marie.setCodePostal("1000");
        marie.setLocalite("Bruxelles");
        marie.setLangue(Langue.fr);
        // created_at est pose par l auditing JPA, absent d un test unitaire.
        ReflectionTestUtils.setField(marie, "createdAt", CREATION_COMPTE);

        golf = new Vehicule(marie, "1-ABC-123", "VW", "Golf", Motorisation.DIESEL);
        golf.setAnnee((short) 2019);
        golf.mettreAJourKilometrage(120_000);
        golf.setNumeroChassis("WVWZZZ1KZAW000001");
        ReflectionTestUtils.setField(golf, "createdAt", CREATION_COMPTE);

        Categorie entretien = new Categorie("ENT", "Entretien", TypeCategorie.SERVICE);
        vidange = new Prestation(entretien, "VID", "Vidange", new BigDecimal("49.00"), 30);
        Categorie freinage = new Categorie("FRE", "Freinage", TypeCategorie.PIECE);
        plaquettes = new Piece(freinage, "FRE-001", "Plaquettes avant", new BigDecimal("19.99"));
        plaquettes.setQuantiteStock(10);
    }

    /**
     * Compte trouve, toutes les sections vides : le socle de la plupart des cas.
     *
     * <p>Bouchons {@code lenient} a dessein : chaque test remplace la ou les
     * sections qui l interessent, et un bouchon « vide » ainsi eclipse ferait
     * echouer le mode strict pour une raison sans rapport avec ce qui est teste.
     */
    private void compteSansDonnees() {
        lenient().when(utilisateurs.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(marie));
        lenient().when(vehicules.pourMembre(EMAIL)).thenReturn(List.of());
        lenient().when(rendezVous.pourMembre(EMAIL)).thenReturn(List.of());
        lenient().when(interventions.pourMembre(EMAIL)).thenReturn(List.of());
        lenient().when(commandes.pourMembre(EMAIL)).thenReturn(List.of());
        lenient().when(consentements.pourMembre(EMAIL)).thenReturn(List.of());
    }

    private Rdv rdvDeMarie() {
        return new Rdv("RDV-2026-0001", marie, golf, new PosteAtelier("Pont 1"),
                Instant.parse("2026-06-10T08:00:00Z"), Duration.ofMinutes(30),
                List.of(vidange), "Bruit a l'avant");
    }

    /**
     * Commande de deux plaquettes, nee de la conversion d un panier comme en
     * production (la conversion <b>deplace</b> la ligne, elle ne la recopie pas).
     * L identifiant est pose a la main : le regroupement des lignes par commande
     * s appuie dessus, et JPA ne l attribue pas hors persistance.
     */
    private Commande commandeAvecLignes() {
        Panier panier = new Panier(marie);
        panier.ajouterPiece(plaquettes, 2);
        Commande commande = new Commande("CMD-2026-0001", marie,
                new BigDecimal("39.98"), new BigDecimal("8.40"), new BigDecimal("48.38"),
                Instant.parse("2026-03-01T08:00:00Z"));
        ReflectionTestUtils.setField(commande, "id", 1L);
        commande.reprendreLignes(List.copyOf(panier.getLignes()));
        return commande;
    }

    /** Lignes desormais rattachees a la commande, telles que le repository les rendrait. */
    private List<LignePanier> lignesDe(Commande commande) {
        Panier panier = new Panier(marie);
        panier.ajouterPiece(plaquettes, 2);
        List<LignePanier> lignes = List.copyOf(panier.getLignes());
        commande.reprendreLignes(lignes);
        return lignes;
    }

    private String documentJson(ExportDonnees export) {
        return new String(serialiseur.enJson(export), StandardCharsets.UTF_8);
    }

    @Nested
    @DisplayName("Contenu")
    class Contenu {

        @Test
        @DisplayName("restitue le profil, l'adresse structuree et la date de creation du compte")
        void profil() {
            compteSansDonnees();

            ExportDonnees.ProfilExport profil =
                    service.assembler(EMAIL).donneesPersonnelles().profil();

            assertThat(profil.nom()).isEqualTo("Dupont");
            assertThat(profil.prenom()).isEqualTo("Marie");
            assertThat(profil.email()).isEqualTo(EMAIL);
            assertThat(profil.telephone()).isEqualTo("+32470000000");
            assertThat(profil.adresse().rue()).isEqualTo("Rue Neuve");
            assertThat(profil.adresse().numero()).isEqualTo("12");
            assertThat(profil.adresse().codePostal()).isEqualTo("1000");
            assertThat(profil.adresse().localite()).isEqualTo("Bruxelles");
            assertThat(profil.adresse().pays()).isEqualTo("Belgique");
            assertThat(profil.langue()).isEqualTo("fr");
            assertThat(profil.statutCompte()).isEqualTo("EN_ATTENTE_VALIDATION");
            assertThat(profil.dateCreationCompte()).isEqualTo(CREATION_COMPTE);
        }

        @Test
        @DisplayName("horodate l'export a l'horloge injectee")
        void horodatage() {
            compteSansDonnees();

            assertThat(service.assembler(EMAIL).genereLe()).isEqualTo(MAINTENANT);
        }

        @Test
        @DisplayName("restitue les vehicules avec leurs champs metier")
        void vehicules() {
            compteSansDonnees();
            when(vehicules.pourMembre(EMAIL)).thenReturn(List.of(golf));

            List<ExportDonnees.VehiculeExport> exportes =
                    service.assembler(EMAIL).donneesPersonnelles().vehicules();

            assertThat(exportes).singleElement().satisfies(vehicule -> {
                assertThat(vehicule.plaque()).isEqualTo("1-ABC-123");
                assertThat(vehicule.marque()).isEqualTo("VW");
                assertThat(vehicule.modele()).isEqualTo("Golf");
                assertThat(vehicule.motorisation()).isEqualTo("DIESEL");
                assertThat(vehicule.annee()).isEqualTo((short) 2019);
                assertThat(vehicule.kilometrage()).isEqualTo(120_000);
                assertThat(vehicule.numeroChassis()).isEqualTo("WVWZZZ1KZAW000001");
                assertThat(vehicule.dateAjout()).isEqualTo(CREATION_COMPTE);
            });
        }

        @Test
        @DisplayName("restitue les rendez-vous avec leurs prestations et leurs montants")
        void rendezVous() {
            compteSansDonnees();
            when(rendezVous.pourMembre(EMAIL)).thenReturn(List.of(rdvDeMarie()));

            List<ExportDonnees.RdvExport> exportes =
                    service.assembler(EMAIL).donneesPersonnelles().rendezVous();

            assertThat(exportes).singleElement().satisfies(rdv -> {
                assertThat(rdv.numero()).isEqualTo("RDV-2026-0001");
                assertThat(rdv.statut()).isEqualTo("EN_ATTENTE");
                assertThat(rdv.vehicule()).isEqualTo("1-ABC-123");
                assertThat(rdv.commentaire()).isEqualTo("Bruit a l'avant");
                assertThat(rdv.montantHtva()).isEqualByComparingTo("49.00");
                assertThat(rdv.prestations()).singleElement().satisfies(prestation -> {
                    assertThat(prestation.libelle()).isEqualTo("Vidange");
                    assertThat(prestation.prixUnitaireHtva()).isEqualByComparingTo("49.00");
                });
            });
        }

        @Test
        @DisplayName("restitue les commandes avec leurs lignes et le detail HTVA / TVA / TVAC")
        void commandes() {
            compteSansDonnees();
            Commande commande = commandeAvecLignes();
            when(commandes.pourMembre(EMAIL)).thenReturn(List.of(commande));
            when(commandes.lignesDe(List.of(commande))).thenReturn(lignesDe(commande));

            List<ExportDonnees.CommandeExport> exportees =
                    service.assembler(EMAIL).donneesPersonnelles().commandes();

            assertThat(exportees).singleElement().satisfies(cmd -> {
                assertThat(cmd.numero()).isEqualTo("CMD-2026-0001");
                assertThat(cmd.statut()).isEqualTo("EN_ATTENTE_PAIEMENT");
                assertThat(cmd.montantHtva()).isEqualByComparingTo("39.98");
                assertThat(cmd.montantTva()).isEqualByComparingTo("8.40");
                assertThat(cmd.montantTvac()).isEqualByComparingTo("48.38");
                assertThat(cmd.lignes()).singleElement().satisfies(ligne -> {
                    assertThat(ligne.libelle()).isEqualTo("Plaquettes avant");
                    assertThat(ligne.quantite()).isEqualTo((short) 2);
                    assertThat(ligne.totalHtva()).isEqualByComparingTo("39.98");
                    assertThat(ligne.totalTva()).isEqualByComparingTo("8.40");
                    assertThat(ligne.totalTvac()).isEqualByComparingTo("48.38");
                });
            });
        }

        @Test
        @DisplayName("n'interroge pas les lignes quand le membre n'a aucune commande")
        void aucuneCommande() {
            compteSansDonnees();

            assertThat(service.assembler(EMAIL).donneesPersonnelles().commandes()).isEmpty();
            verify(commandes, never()).lignesDe(any());
        }

        @Test
        @DisplayName("restitue les interventions au statut percu par le membre (RM-16)")
        void interventions() {
            compteSansDonnees();
            Intervention intervention = new Intervention("INT-2026-0001", rdvDeMarie());
            intervention.demarrer(MAINTENANT);
            intervention.suspendre();
            when(interventions.pourMembre(EMAIL)).thenReturn(List.of(intervention));

            List<ExportDonnees.InterventionExport> exportees =
                    service.assembler(EMAIL).donneesPersonnelles().interventions();

            assertThat(exportees).singleElement().satisfies(interv -> {
                assertThat(interv.numero()).isEqualTo("INT-2026-0001");
                // SUSPENDUE est une mecanique interne d'atelier : le membre lit
                // « En cours », dans l'export comme sur son ecran de suivi.
                assertThat(interv.statut()).isEqualTo("En cours");
                assertThat(interv.rendezVous()).isEqualTo("RDV-2026-0001");
                assertThat(interv.vehicule()).isEqualTo("1-ABC-123");
                assertThat(interv.montantDevisInitialHtva()).isEqualByComparingTo("49.00");
                assertThat(interv.lignes()).singleElement().satisfies(ligne -> {
                    assertThat(ligne.libelle()).isEqualTo("Vidange");
                    assertThat(ligne.type()).isEqualTo("MAIN_OEUVRE");
                    assertThat(ligne.accordMembre()).isNull();
                });
            });
        }

        @Test
        @DisplayName("joint le rappel legal du traitement, dans la langue du membre")
        void informationsTraitement() {
            compteSansDonnees();
            marie.setLangue(Langue.nl);

            ExportDonnees export = service.assembler(EMAIL);

            assertThat(export.informationsTraitement().finalites()).isNotEmpty();
            assertThat(export.informationsTraitement().destinataires()).isNotEmpty();
            assertThat(export.informationsTraitement().dureesConservation()).isNotEmpty();
            assertThat(export.informationsTraitement().droits()).isNotEmpty();
            assertThat(export.informationsTraitement().responsableTraitement())
                    .contains("verwerking");
        }
    }

    @Nested
    @DisplayName("Consentements")
    class Consentements {

        @Test
        @DisplayName("restitue l'adresse IP et l'horodatage : ce sont des donnees du membre")
        void adresseIpRestituee() {
            compteSansDonnees();
            when(consentements.pourMembre(EMAIL)).thenReturn(List.of(
                    Consentement.acceptation(marie, TypeDocumentConsentement.CGV,
                            Consentement.CGV_VERSION_COURANTE, "81.240.10.7",
                            Instant.parse("2026-03-01T07:59:00Z"))));

            List<ExportDonnees.ConsentementExport> exportes =
                    service.assembler(EMAIL).donneesPersonnelles().consentements();

            assertThat(exportes).singleElement().satisfies(preuve -> {
                assertThat(preuve.typeDocument()).isEqualTo("CGV");
                assertThat(preuve.versionAcceptee()).isEqualTo(Consentement.CGV_VERSION_COURANTE);
                assertThat(preuve.accorde()).isTrue();
                assertThat(preuve.adresseIp()).isEqualTo("81.240.10.7");
                assertThat(preuve.dateConsentement())
                        .isEqualTo(Instant.parse("2026-03-01T07:59:00Z"));
            });
        }

        @Test
        @DisplayName("l'IP du consentement figure aussi dans le document JSON livre")
        void adresseIpDansLeJson() {
            compteSansDonnees();
            when(consentements.pourMembre(EMAIL)).thenReturn(List.of(
                    Consentement.acceptation(marie, TypeDocumentConsentement.CGV,
                            Consentement.CGV_VERSION_COURANTE, "81.240.10.7", MAINTENANT)));

            assertThat(documentJson(service.assembler(EMAIL)))
                    .contains("\"adresse_ip\"")
                    .contains("81.240.10.7");
        }

        @Test
        @DisplayName("le consentement marketing suit la derniere preuve NEWSLETTER, retrait compris")
        void marketingSuitLeRetrait() {
            compteSansDonnees();
            Consentement inscription = Consentement.acceptation(marie,
                    TypeDocumentConsentement.NEWSLETTER, "NL-2026-01", "81.240.10.7",
                    Instant.parse("2026-02-01T10:00:00Z"));
            // La table est append-only : un retrait s'ecrit en nouvelle ligne
            // accorde = false. Aucune fabrique publique ne le fait encore (F25) —
            // le test simule la ligne que la base sait deja porter.
            Consentement retrait = Consentement.acceptation(marie,
                    TypeDocumentConsentement.NEWSLETTER, "NL-2026-01", "81.240.10.7",
                    Instant.parse("2026-05-01T10:00:00Z"));
            ReflectionTestUtils.setField(retrait, "accorde", false);
            when(consentements.pourMembre(EMAIL)).thenReturn(List.of(inscription, retrait));

            ExportDonnees export = service.assembler(EMAIL);

            assertThat(export.donneesPersonnelles().profil().consentementMarketing()).isFalse();
            // L'historique complet reste restitue : le retrait ne fait pas
            // disparaitre la preuve de l'acceptation initiale.
            assertThat(export.donneesPersonnelles().consentements()).hasSize(2);
        }

        @Test
        @DisplayName("aucune preuve NEWSLETTER vaut consentement marketing refuse")
        void marketingAbsentVautRefus() {
            compteSansDonnees();

            assertThat(service.assembler(EMAIL).donneesPersonnelles().profil()
                    .consentementMarketing()).isFalse();
        }
    }

    @Nested
    @DisplayName("Exclusions")
    class Exclusions {

        @Test
        @DisplayName("le document ne contient jamais le mot de passe, meme hache")
        void aucunMotDePasse() {
            compteSansDonnees();

            String document = documentJson(service.assembler(EMAIL));

            assertThat(document)
                    .doesNotContain(EMPREINTE)
                    .doesNotContain("$2a$")
                    .doesNotContain("mot_de_passe_hache")
                    .doesNotContain("motDePasseHache");
        }

        @Test
        @DisplayName("le document ne contient aucun secret technique ni jeton")
        void aucunSecretTechnique() {
            compteSansDonnees();
            marie.enregistrerJetonVerification("jeton-secret-a-ne-jamais-exporter",
                    MAINTENANT.plus(Duration.ofHours(1)));

            String document = documentJson(service.assembler(EMAIL));

            assertThat(document)
                    .doesNotContain("jeton-secret-a-ne-jamais-exporter")
                    .doesNotContain("jeton_verification");
        }

        @Test
        @DisplayName("annonce explicitement l'absence de donnees de carte bancaire")
        void noteSurLesDonneesBancaires() {
            compteSansDonnees();

            ExportDonnees export = service.assembler(EMAIL);

            assertThat(export.exclusions().donneesBancaires())
                    .isNotBlank()
                    .containsIgnoringCase("carte bancaire");
            assertThat(export.exclusions().motDePasse()).isNotBlank();
            assertThat(export.exclusions().secretsTechniques()).isNotBlank();
        }

        @Test
        @DisplayName("restitue les donnees de connexion sans les jetons associes")
        void donneesDeConnexion() {
            compteSansDonnees();
            marie.enregistrerConnexionReussie(Instant.parse("2026-08-20T06:00:00Z"));
            marie.confirmerAdresseEmail();

            ExportDonnees.ConnexionExport connexion =
                    service.assembler(EMAIL).donneesPersonnelles().connexionEtSecurite();

            assertThat(connexion.derniereConnexion())
                    .isEqualTo(Instant.parse("2026-08-20T06:00:00Z"));
            assertThat(connexion.emailVerifie()).isTrue();
            assertThat(connexion.tentativesEchoueesEnCours()).isZero();
            assertThat(connexion.compteVerrouilleJusquA()).isNull();
        }
    }

    @Nested
    @DisplayName("Etancheite")
    class Etancheite {

        @Test
        @DisplayName("interroge chaque source avec la seule adresse du membre connecte")
        void toutesLesRequetesPortentSurLeMembreConnecte() {
            compteSansDonnees();

            service.assembler(EMAIL);

            // Aucune section ne peut ramener la donnee d'un tiers : l'adresse du
            // membre connecte est la condition de chaque chargement, pas un filtre
            // applique apres coup. L'etancheite reelle, cote SQL, est prouvee par
            // ExportDonneesIT sur deux membres en base.
            verify(utilisateurs).findByEmailIgnoreCase(EMAIL);
            verify(vehicules).pourMembre(EMAIL);
            verify(rendezVous).pourMembre(EMAIL);
            verify(interventions).pourMembre(EMAIL);
            verify(commandes).pourMembre(EMAIL);
            verify(consentements).pourMembre(EMAIL);
        }

        @Test
        @DisplayName("refuse d'exporter pour une adresse sans compte")
        void adresseInconnue() {
            when(utilisateurs.findByEmailIgnoreCase("inconnu@exemple.be"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.assembler("inconnu@exemple.be"))
                    .isInstanceOf(RessourceIntrouvableException.class);
        }
    }

    @Nested
    @DisplayName("Gardes de l'export")
    class Gardes {

        private void motDePasseValide() {
            lenient().when(encodeur.matches("MotDePasseDeMarie!", EMPREINTE)).thenReturn(true);
        }

        @Test
        @DisplayName("produit un fichier JSON date du jour quand les deux gardes passent")
        void exportNominal() {
            compteSansDonnees();
            motDePasseValide();

            FichierExport fichier = service.exporter(EMAIL, "MotDePasseDeMarie!");

            assertThat(fichier.nom()).isEqualTo("mes-donnees-2026-08-22.json");
            assertThat(new String(fichier.contenu(), StandardCharsets.UTF_8))
                    .contains("\"donnees_personnelles\"")
                    .contains("\"informations_traitement\"");
        }

        @Test
        @DisplayName("verifie le mot de passe par l'encodeur, jamais par egalite de chaines")
        void reauthentificationParEncodeur() {
            compteSansDonnees();
            motDePasseValide();

            service.exporter(EMAIL, "MotDePasseDeMarie!");

            verify(encodeur).matches("MotDePasseDeMarie!", EMPREINTE);
        }

        @Test
        @DisplayName("mauvais mot de passe : refus, et aucun octet produit")
        void mauvaisMotDePasse() {
            compteSansDonnees();
            when(encodeur.matches("mauvais", EMPREINTE)).thenReturn(false);

            assertThatThrownBy(() -> service.exporter(EMAIL, "mauvais"))
                    .isInstanceOf(ReauthentificationEchoueeException.class);
            // Aucune source n'est meme interrogee : la garde precede l'agregation.
            verify(vehicules, never()).pourMembre(any());
        }

        @Test
        @DisplayName("mot de passe absent : refus sans solliciter l'encodeur")
        void motDePasseAbsent() {
            compteSansDonnees();

            assertThatThrownBy(() -> service.exporter(EMAIL, null))
                    .isInstanceOf(ReauthentificationEchoueeException.class);
            assertThatThrownBy(() -> service.exporter(EMAIL, ""))
                    .isInstanceOf(ReauthentificationEchoueeException.class);
            // BCrypt leve sur un mot de passe nul : la garde le traite comme un
            // echec metier, pas comme une panne technique.
            verify(encodeur, never()).matches(any(), any());
        }

        @Test
        @DisplayName("un echec de mot de passe ne consomme pas le quota de 24 heures")
        void echecNeConsommePasLeQuota() {
            compteSansDonnees();
            motDePasseValide();
            when(encodeur.matches("mauvais", EMPREINTE)).thenReturn(false);

            assertThatThrownBy(() -> service.exporter(EMAIL, "mauvais"))
                    .isInstanceOf(ReauthentificationEchoueeException.class);

            // Le membre reste en droit d'exporter : sinon, un tiers pourrait le
            // priver de son droit d'acces en saisissant un mot de passe au hasard.
            assertThat(service.attenteRestante(EMAIL)).isEmpty();
            assertThat(service.exporter(EMAIL, "MotDePasseDeMarie!").contenu()).isNotEmpty();
        }

        @Test
        @DisplayName("un second export dans les 24 heures est refuse, avec le temps restant")
        void secondExportRefuse() {
            compteSansDonnees();
            motDePasseValide();
            service.exporter(EMAIL, "MotDePasseDeMarie!");

            assertThatThrownBy(() -> service.exporter(EMAIL, "MotDePasseDeMarie!"))
                    .isInstanceOf(ExportTropRecentException.class)
                    .satisfies(refus -> assertThat(
                            ((ExportTropRecentException) refus).getAttenteRestante())
                            .isEqualTo(RegistreExportsRecents.DELAI_ENTRE_EXPORTS));
        }

        @Test
        @DisplayName("la limite est propre a chaque membre")
        void limitePropreAuMembre() {
            compteSansDonnees();
            motDePasseValide();
            service.exporter(EMAIL, "MotDePasseDeMarie!");

            assertThat(service.attenteRestante(EMAIL)).isPresent();
            assertThat(service.attenteRestante("jean@exemple.be")).isEmpty();
        }

        @Test
        @DisplayName("expose la date du dernier export pour l'affichage de confirmation")
        void dernierExportExpose() {
            compteSansDonnees();
            motDePasseValide();

            assertThat(service.dernierExport(EMAIL)).isEmpty();
            service.exporter(EMAIL, "MotDePasseDeMarie!");
            assertThat(service.dernierExport(EMAIL)).contains(MAINTENANT);
        }
    }
}
