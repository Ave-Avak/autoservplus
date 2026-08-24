package be.autoservplus.reservation.service.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Conformite du fichier iCalendar produit (F38, RFC 5545).
 *
 * <p>Les cas verifies sont ceux qu un fichier ecrit a la main rate en silence :
 * fins de ligne, echappement, pliage sur les octets. Aucun ne se voit a la lecture
 * du fichier — ils se voient a l import, chez le membre, dans un client de
 * calendrier qu on n a pas sous la main.</p>
 */
@DisplayName("Redacteur iCalendar (RFC 5545)")
class RedacteurIcalTest {

    private static final Instant DEBUT = Instant.parse("2026-09-16T08:00:00Z");
    private static final Instant FIN = Instant.parse("2026-09-16T09:00:00Z");
    private static final Instant PRODUCTION = Instant.parse("2026-08-24T10:15:00Z");

    private static EvenementIcal evenement(String resume, String lieu, String description) {
        return new EvenementIcal("abc@autoservplus", PRODUCTION, DEBUT, FIN,
                resume, lieu, description, "https://exemple.be/mes-rendez-vous/abc",
                Duration.ofHours(24));
    }

    private static EvenementIcal nominal() {
        return evenement("Rendez-vous au Garage", "Rue 12, 1000 Bruxelles", "Prestations : Vidange");
    }

    @Nested
    @DisplayName("Structure")
    class Structure {

        @Test
        @DisplayName("ouvre et ferme le calendrier et l evenement")
        void enveloppe() {
            String ics = RedacteurIcal.calendrier(nominal());

            assertThat(ics)
                    .startsWith("BEGIN:VCALENDAR")
                    .contains("VERSION:2.0")
                    .contains("BEGIN:VEVENT")
                    .contains("END:VEVENT")
                    .endsWith("END:VCALENDAR\r\n");
        }

        /**
         * Toutes les lignes se terminent par CRLF (§3.1). Un fichier en LF seul est
         * tolere par certains clients et rejete par d autres : le defaut ne se
         * manifeste alors que chez une partie des membres, ce qui est le pire cas
         * possible pour le diagnostiquer.
         */
        @Test
        @DisplayName("termine chaque ligne par CRLF")
        void finsDeLigneCrlf() {
            String ics = RedacteurIcal.calendrier(nominal());

            assertThat(ics.replace("\r\n", "")).doesNotContain("\n");
        }

        @Test
        @DisplayName("horodate en UTC, suffixe Z, sans VTIMEZONE")
        void horodatageUtc() {
            String ics = RedacteurIcal.calendrier(nominal());

            assertThat(ics)
                    .contains("DTSTART:20260916T080000Z")
                    .contains("DTEND:20260916T090000Z")
                    .contains("DTSTAMP:20260824T101500Z")
                    .doesNotContain("VTIMEZONE");
        }

        @Test
        @DisplayName("produit un rappel DISPLAY 24 h avant le debut")
        void rappelVeille() {
            String ics = RedacteurIcal.calendrier(nominal());

            assertThat(ics)
                    .contains("BEGIN:VALARM")
                    .contains("ACTION:DISPLAY")
                    .contains("TRIGGER:-PT24H")
                    .contains("END:VALARM");
        }

        @Test
        @DisplayName("omet le VALARM quand aucun rappel n est demande")
        void sansRappel() {
            EvenementIcal sansAlarme = new EvenementIcal("abc@autoservplus", PRODUCTION, DEBUT, FIN,
                    "Rendez-vous", "Rue 12", "Prestations", "https://exemple.be/x", null);

            assertThat(RedacteurIcal.calendrier(sansAlarme)).doesNotContain("VALARM");
        }
    }

    @Nested
    @DisplayName("Echappement des valeurs de texte (§3.3.11)")
    class Echappement {

        /**
         * Le cas reel : toute adresse belge contient une virgule. Non echappee, elle
         * scinde la valeur de LOCATION en deux valeurs, et l adresse affichee dans
         * l agenda perd tout ce qui suit la premiere virgule.
         */
        @Test
        @DisplayName("echappe la virgule d une adresse")
        void virguleDAdresse() {
            String ics = RedacteurIcal.calendrier(
                    evenement("Rendez-vous", "Rue de l Atelier 12, 1000 Bruxelles, Belgique", "d"));

            assertThat(ics).contains("LOCATION:Rue de l Atelier 12\\, 1000 Bruxelles\\, Belgique");
        }

        @Test
        @DisplayName("echappe point-virgule et barre oblique inverse")
        void pointVirguleEtBarre() {
            String ics = RedacteurIcal.calendrier(evenement("a;b", "c\\d", "e"));

            assertThat(ics).contains("SUMMARY:a\\;b").contains("LOCATION:c\\\\d");
        }

        @Test
        @DisplayName("transforme un retour a la ligne de description en \\n litteral")
        void retourALaLigne() {
            String ics = RedacteurIcal.calendrier(evenement("r", "l", "ligne 1\nligne 2"));

            assertThat(ics).contains("DESCRIPTION:ligne 1\\nligne 2");
            // Le retour a la ligne d origine ne doit PAS survivre tel quel : il
            // terminerait la propriete et rendrait la suite illisible.
            assertThat(ics).doesNotContain("DESCRIPTION:ligne 1\r\nligne 2");
        }

        /**
         * L URL est de type URI, pas TEXT : l echapper transformerait ses separateurs
         * en litteraux et le lien deviendrait inutilisable. La distinction est facile
         * a perdre lors d une refactorisation qui « uniformiserait » l ecriture des
         * proprietes.
         */
        @Test
        @DisplayName("n echappe pas l URL, qui n est pas une valeur de texte")
        void urlNonEchappee() {
            EvenementIcal avecParametres = new EvenementIcal("abc@autoservplus", PRODUCTION, DEBUT, FIN,
                    "r", "l", "d", "https://exemple.be/x?a=1&b=2", null);

            assertThat(RedacteurIcal.calendrier(avecParametres))
                    .contains("URL:https://exemple.be/x?a=1&b=2");
        }
    }

    @Nested
    @DisplayName("Pliage des lignes longues (§3.1)")
    class Pliage {

        @Test
        @DisplayName("aucune ligne ne depasse 75 octets")
        void limiteRespectee() {
            String tresLong = "Rendez-vous au ".repeat(20);
            String ics = RedacteurIcal.calendrier(evenement(tresLong, "l", tresLong));

            for (String ligne : ics.split("\r\n")) {
                assertThat(ligne.getBytes(StandardCharsets.UTF_8).length)
                        .as("longueur de la ligne : %s", ligne)
                        .isLessThanOrEqualTo(75);
            }
        }

        @Test
        @DisplayName("les lignes de continuation commencent par une espace")
        void continuationsMarquees() {
            String ics = RedacteurIcal.calendrier(evenement("x".repeat(200), "l", "d"));

            String[] lignes = ics.split("\r\n");
            long continuations = Arrays.stream(lignes).filter(l -> l.startsWith(" ")).count();
            assertThat(continuations).isPositive();
        }

        /**
         * Le point le plus facile a rater : le pliage porte sur les OCTETS UTF-8, et
         * un caractere accentue en occupe deux. Couper au milieu d une sequence
         * produit un fichier que le client rejette. Le controle est indirect mais
         * concluant : si une coupure tombait mal, la chaine repliee ne redonnerait
         * pas le texte d origine.
         */
        @Test
        @DisplayName("ne coupe jamais un caractere accentue en deux")
        void pasDeCoupureAuMilieuDUnCaractere() {
            String accentue = "Rendez-vous à l'atelier — révision élémentaire ".repeat(4);

            String plie = RedacteurIcal.plier("SUMMARY:" + RedacteurIcal.echapper(accentue));

            assertThat(plie.replace("\r\n ", ""))
                    .isEqualTo("SUMMARY:" + RedacteurIcal.echapper(accentue));
            for (String ligne : plie.split("\r\n")) {
                assertThat(ligne.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(75);
            }
        }

        @Test
        @DisplayName("laisse intacte une ligne qui tient dans la limite")
        void ligneCourteInchangee() {
            assertThat(RedacteurIcal.plier("VERSION:2.0")).isEqualTo("VERSION:2.0");
        }
    }
}
