package be.autoservplus.legal.service;

import be.autoservplus.identite.domain.Langue;
import be.autoservplus.legal.domain.TypeDocumentVersionne;
import be.autoservplus.legal.repository.VersionDocumentRepository;
import be.autoservplus.legal.service.dto.TexteArchiveVue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Resolution des versions de documents sur la base reellement migree (F24).
 */
@SpringBootTest
@Testcontainers
@DisplayName("Versionnage des documents (integration)")
class VersionnageDocumentsIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private VersionsDocumentsService versionsDocuments;
    @Autowired private VersionDocumentRepository versions;
    @Autowired private JdbcTemplate jdbc;

    @Nested
    @DisplayName("Amorcage V33")
    class Amorcage {

        @Test
        @DisplayName("gele les trois documents consentis dans les trois langues, versions remplacees comprises")
        void troisLanguesParVersion() {
            // Une version = un jeu de trois textes publies ensemble. Le compte total croit
            // donc de trois a chaque publication, et non de un : V33 amorce trois documents,
            // V35 publie une seconde version des CGV.
            assertThat(versions.count())
                    .isEqualTo((TypeDocumentVersionne.values().length + 1)
                            * (long) Langue.values().length);

            for (TypeDocumentVersionne type : TypeDocumentVersionne.values()) {
                String courante = versionsDocuments.versionCourante(type);
                assertThat(versions.findByTypeDocumentAndVersionOrderByLangue(type, courante))
                        .as("Version en vigueur de %s incomplete : un membre pourrait se voir "
                                + "opposer un texte dans une langue qu il n a pas lue", type)
                        .hasSize(Langue.values().length);
            }
        }

        @Test
        @DisplayName("n ouvre le CHECK qu aux documents pour lesquels une preuve est reellement ecrite")
        void checkFermeSurLesTroisTypes() {
            // POLITIQUE_CONFIDENTIALITE et NEWSLETTER existent au CHECK de consentement
            // sans qu aucune ligne ne soit jamais ecrite pour eux : leur versionner un
            // texte creerait une version qu aucune preuve ne resout, et laisserait croire
            // l inverse. Le refus vient de la base, pas d une convention de code.
            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO version_document
                        (type_document, version, langue, date_effet, contenu, empreinte)
                    VALUES ('POLITIQUE_CONFIDENTIALITE', 'CONF-2026-01', 'fr', now(), 'x', repeat('a', 64))
                    """))
                    .hasMessageContaining("ck_version_document_type");
        }

        @Test
        @DisplayName("refuse deux fois la meme version d un document dans une meme langue")
        void unicite() {
            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO version_document
                        (type_document, version, langue, date_effet, contenu, empreinte)
                    VALUES ('CGV', 'CGV-2026-01', 'fr', now(), 'doublon', repeat('b', 64))
                    """))
                    .hasMessageContaining("uq_version_document");
        }
    }

    @Nested
    @DisplayName("Version en vigueur")
    class EnVigueur {

        @Test
        @DisplayName("resout la version en vigueur, y compris apres une publication")
        void continuite() {
            // CGV a change de version avec V35 (article 9, conservation portee a dix ans) ;
            // les deux autres documents portent encore l identifiant des constantes que F24
            // a remplacees, ce qui reste l engagement de compatibilite pris a sa livraison.
            assertThat(versionsDocuments.versionCourante(TypeDocumentVersionne.CGV))
                    .isEqualTo("CGV-2026-02");
            assertThat(versionsDocuments.versionCourante(TypeDocumentVersionne.COOKIES))
                    .isEqualTo("COOKIES-2026-01");
            assertThat(versionsDocuments.versionCourante(TypeDocumentVersionne.RENONCIATION_RETRACTATION))
                    .isEqualTo("VI53-2026-01");
        }

        @Test
        @DisplayName("une version retiree du jeu resolvable n est plus servie comme courante")
        void versionRetireeNonResolue() {
            // actif = false ne supprime pas : la ligne reste consultable a l archive. Ce qui
            // doit cesser, c est qu elle soit proposee comme le texte du jour.
            assertThat(versionsDocuments.versionCourante(TypeDocumentVersionne.CGV))
                    .isNotEqualTo("CGV-2026-01");
            assertThat(versionsDocuments.archive(TypeDocumentVersionne.CGV, "CGV-2026-01",
                            Locale.FRENCH))
                    .as("La version remplacee doit rester consultable : des preuves la designent")
                    .isPresent();
        }

        /**
         * Le point que F24 devait rendre vrai : une preuve de consentement ne designe plus
         * un numero en l air. On confronte ici l ENSEMBLE des valeurs distinctes
         * reellement presentes en base — celles du seed comme celles ecrites par les
         * parcours — a la table des versions.
         */
        @Test
        @Transactional
        @DisplayName("toute version_acceptee presente en base resout vers un texte archive")
        void toutesLesPreuvesResolvent() {
            jdbc.update("""
                    INSERT INTO consentement
                        (utilisateur_id, type_document, version_acceptee, accorde, date_consentement)
                    SELECT u.id, 'CGV', 'CGV-2026-01', TRUE, now()
                    FROM utilisateur u WHERE u.email = 'admin@autoservplus.be'
                    """);

            List<String> orphelines = jdbc.queryForList("""
                    SELECT DISTINCT c.version_acceptee
                    FROM consentement c
                    WHERE NOT EXISTS (SELECT 1 FROM version_document v
                                      WHERE v.version = c.version_acceptee)
                    ORDER BY 1
                    """, String.class);

            assertThat(orphelines)
                    .as("Ces versions acceptees ne designent aucun texte archive : la preuve "
                            + "dirait QU ON a accepte, jamais QUOI")
                    .isEmpty();
        }

        @Test
        @Transactional
        @DisplayName("une version datee dans le futur n est pas encore en vigueur")
        void publicationAnticipee() {
            // Publier a l avance est la pratique attendue quand un changement de conditions
            // doit etre annonce avant de s appliquer. Sans le filtre sur date_effet, la
            // nouvelle version prendrait effet a l instant de son insertion — et des
            // membres accepteraient un texte annonce comme pas encore applicable.
            for (Langue langue : Langue.values()) {
                jdbc.update("""
                        INSERT INTO version_document
                            (type_document, version, langue, date_effet, contenu, empreinte)
                        VALUES ('CGV', 'CGV-2027-01', ?, now() + interval '30 days', 'futur',
                                repeat('c', 64))
                        """, langue.name());
            }

            assertThat(versionsDocuments.versionCourante(TypeDocumentVersionne.CGV))
                    .as("La version annoncee pour dans trente jours ne s applique pas aujourd hui")
                    .isEqualTo("CGV-2026-02");
        }
    }

    @Nested
    @DisplayName("Consultation du texte archive")
    class Archive {

        @Test
        @DisplayName("sert la langue demandee")
        void langueDemandee() {
            TexteArchiveVue vue = versionsDocuments
                    .archive(TypeDocumentVersionne.CGV, "CGV-2026-01", Locale.forLanguageTag("nl"))
                    .orElseThrow();

            assertThat(vue.langue()).isEqualTo("nl");
            assertThat(vue.languesDisponibles()).containsExactlyInAnyOrder("fr", "nl", "en");
            assertThat(vue.contenu()).isNotBlank();
        }

        @Test
        @DisplayName("retombe sur le francais pour une langue non servie, sans pretendre l avoir servie")
        void langueNonServie() {
            TexteArchiveVue vue = versionsDocuments
                    .archive(TypeDocumentVersionne.CGV, "CGV-2026-01", Locale.GERMAN)
                    .orElseThrow();

            assertThat(vue.langue())
                    .as("Le lecteur doit savoir quelle langue il lit reellement")
                    .isEqualTo("fr");
        }

        @Test
        @DisplayName("sert encore le texte d origine d une version remplacee")
        void versionRemplacee() {
            // Le point que V35 met a l epreuve : apres publication de CGV-2026-02, la preuve
            // d un membre qui a accepte 2026-01 doit toujours pouvoir montrer CE qu il a
            // accepte — donc l ancienne redaction, celle qui annoncait sept ans.
            TexteArchiveVue vue = versionsDocuments
                    .archive(TypeDocumentVersionne.CGV, "CGV-2026-01", Locale.FRENCH)
                    .orElseThrow();

            assertThat(vue.contenu()).contains("conservées sept ans");
            assertThat(vue.actif())
                    .as("Elle est archivee, donc plus en vigueur — et le lecteur doit le savoir")
                    .isFalse();

            TexteArchiveVue courante = versionsDocuments
                    .archive(TypeDocumentVersionne.CGV, "CGV-2026-02", Locale.FRENCH)
                    .orElseThrow();
            assertThat(courante.contenu()).contains("conservées dix ans");
            assertThat(courante.actif()).isTrue();
        }

        @Test
        @DisplayName("une version inconnue reste vide plutot que de rendre un texte approchant")
        void versionInconnue() {
            assertThat(versionsDocuments.archive(TypeDocumentVersionne.CGV, "CGV-1999-99", Locale.FRENCH))
                    .isEmpty();
        }
    }
}
