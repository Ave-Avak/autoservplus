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
        @DisplayName("gele les trois documents consentis, dans les trois langues")
        void neufLignes() {
            assertThat(versions.count()).isEqualTo(TypeDocumentVersionne.values().length
                    * (long) Langue.values().length);
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
        @DisplayName("resout les identifiants que portaient les constantes supprimees")
        void continuite() {
            assertThat(versionsDocuments.versionCourante(TypeDocumentVersionne.CGV))
                    .isEqualTo("CGV-2026-01");
            assertThat(versionsDocuments.versionCourante(TypeDocumentVersionne.COOKIES))
                    .isEqualTo("COOKIES-2026-01");
            assertThat(versionsDocuments.versionCourante(TypeDocumentVersionne.RENONCIATION_RETRACTATION))
                    .isEqualTo("VI53-2026-01");
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
                    .isEqualTo("CGV-2026-01");
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
        @DisplayName("une version inconnue reste vide plutot que de rendre un texte approchant")
        void versionInconnue() {
            assertThat(versionsDocuments.archive(TypeDocumentVersionne.CGV, "CGV-1999-99", Locale.FRENCH))
                    .isEmpty();
        }
    }
}
