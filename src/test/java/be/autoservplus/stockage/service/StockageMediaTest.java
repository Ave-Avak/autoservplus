package be.autoservplus.stockage.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Fondation upload : la surface la plus exposee du lot.
 *
 * <p>Ce test verifie surtout ce que le stockage doit <b>refuser</b>. Un depot accepte
 * a tort met un fichier arbitraire sur le disque du serveur, et le nom que le client
 * propose ne doit jamais l atteindre.</p>
 */
@DisplayName("StockageMedia (fondation upload)")
class StockageMediaTest {

    @TempDir
    Path racine;

    private static final byte[] EN_TETE_JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] EN_TETE_PNG = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private StockageMedia stockage() {
        return new StockageMedia(racine.toString(), 1024 * 1024);
    }

    private static byte[] corps(byte[] entete, int tailleTotale) {
        byte[] contenu = new byte[Math.max(tailleTotale, entete.length)];
        System.arraycopy(entete, 0, contenu, 0, entete.length);
        return contenu;
    }

    private static MockMultipartFile fichier(String nom, byte[] contenu) {
        return new MockMultipartFile("fichier", nom, "image/jpeg", contenu);
    }

    @Nested
    @DisplayName("Acceptation")
    class Acceptation {

        @Test
        @DisplayName("enregistre un JPEG et rend un chemin relatif")
        void jpegAccepte() {
            String chemin = stockage().enregistrer(
                    fichier("photo.jpg", corps(EN_TETE_JPEG, 64)), "prestations");

            assertThat(chemin).startsWith("prestations/").endsWith(".jpg");
            assertThat(racine.resolve(chemin)).exists();
        }

        @Test
        @DisplayName("enregistre un PNG malgre un Content-Type annonce en JPEG")
        void typeReelPrimeSurLAnnonce() {
            // Le client annonce image/jpeg ; le contenu est un PNG. C'est le contenu
            // qui doit decider, sinon l'annonce suffirait a faire passer n'importe quoi.
            String chemin = stockage().enregistrer(
                    fichier("mensonge.jpg", corps(EN_TETE_PNG, 64)), "pieces");

            assertThat(chemin).endsWith(".png");
        }

        @Test
        @DisplayName("relit et supprime ce qu il a enregistre")
        void cycleComplet() {
            StockageMedia stockage = stockage();
            String chemin = stockage.enregistrer(
                    fichier("photo.jpg", corps(EN_TETE_JPEG, 64)), "prestations");

            assertThat(stockage.existe(chemin)).isTrue();
            assertThat(stockage.lire(chemin)).hasSize(64);
            assertThat(stockage.typeMimeDe(chemin)).isEqualTo("image/jpeg");

            stockage.supprimer(chemin);
            assertThat(stockage.existe(chemin)).isFalse();
        }

        @Test
        @DisplayName("supprimer un fichier absent ne leve pas")
        void suppressionIdempotente() {
            assertThat(racine).exists();
            stockage().supprimer("prestations/inexistant.jpg");
        }
    }

    @Nested
    @DisplayName("Nom de fichier")
    class NomDeFichier {

        @Test
        @DisplayName("n utilise jamais le nom d origine")
        void nomFabrique() {
            String chemin = stockage().enregistrer(
                    fichier("ma photo de vidange.jpg", corps(EN_TETE_JPEG, 64)), "prestations");

            assertThat(chemin).doesNotContain("ma photo").doesNotContain("vidange");
        }

        @Test
        @DisplayName("un nom de traversee de repertoire n atteint pas le disque")
        void traverseeNeutralisee() {
            String chemin = stockage().enregistrer(
                    fichier("../../../etc/passwd.jpg", corps(EN_TETE_JPEG, 64)), "prestations");

            assertThat(chemin)
                    .as("le nom du client n est pas reutilise, la traversee est sans objet")
                    .startsWith("prestations/")
                    .doesNotContain("..");
            assertThat(racine.resolve(chemin).normalize()).startsWith(racine);
        }

        @Test
        @DisplayName("deux depots du meme fichier ne se marchent pas dessus")
        void nomsUniques() {
            StockageMedia stockage = stockage();
            byte[] contenu = corps(EN_TETE_JPEG, 64);

            String premier = stockage.enregistrer(fichier("photo.jpg", contenu), "prestations");
            String second = stockage.enregistrer(fichier("photo.jpg", contenu), "prestations");

            assertThat(premier).isNotEqualTo(second);
        }

        @Test
        @DisplayName("un sous-dossier tentant de sortir de la racine est refuse")
        void evasionParLeSousDossier() {
            assertThatThrownBy(() -> stockage().enregistrer(
                    fichier("photo.jpg", corps(EN_TETE_JPEG, 64)), "../ailleurs"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Refus")
    class Refus {

        @Test
        @DisplayName("refuse un fichier vide")
        void fichierVide() {
            assertThatThrownBy(() -> stockage().enregistrer(
                    fichier("vide.jpg", new byte[0]), "prestations"))
                    .isInstanceOf(TypeFichierRefuseException.class);
        }

        @Test
        @DisplayName("refuse un contenu qui n est pas une image admise")
        void contenuNonImage() {
            byte[] script = "<?php system($_GET[0]); ?>".getBytes(StandardCharsets.UTF_8);

            assertThatThrownBy(() -> stockage().enregistrer(
                    fichier("innocent.jpg", script), "prestations"))
                    .isInstanceOf(TypeFichierRefuseException.class);
        }

        @Test
        @DisplayName("refuse un SVG, qui est du XML capable de porter du script")
        void svgRefuse() {
            byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script/></svg>"
                    .getBytes(StandardCharsets.UTF_8);

            assertThatThrownBy(() -> stockage().enregistrer(
                    fichier("logo.svg", svg), "prestations"))
                    .isInstanceOf(TypeFichierRefuseException.class);
        }

        @Test
        @DisplayName("refuse un fichier plus gros que le plafond")
        void tropVolumineux() throws IOException {
            StockageMedia petit = new StockageMedia(racine.toString(), 100);
            ByteArrayOutputStream gros = new ByteArrayOutputStream();
            gros.write(EN_TETE_JPEG);
            gros.write(new byte[500]);

            assertThatThrownBy(() -> petit.enregistrer(
                    fichier("gros.jpg", gros.toByteArray()), "prestations"))
                    .isInstanceOf(FichierTropVolumineuxException.class);
        }

        @Test
        @DisplayName("le message de refus ne rejoue pas le nom fourni par le client")
        void messageSansEchoDuClient() {
            assertThatThrownBy(() -> stockage().enregistrer(
                    fichier("<script>alert(1)</script>.jpg", new byte[]{1, 2, 3, 4}),
                    "prestations"))
                    .isInstanceOf(TypeFichierRefuseException.class)
                    .hasMessageNotContaining("script");
        }
    }

    @Nested
    @DisplayName("Reconnaissance des types")
    class Reconnaissance {

        @Test
        @DisplayName("un RIFF qui n est pas du WebP est refuse")
        void riffNonWebp() {
            // RIFF est aussi le conteneur de AVI et WAV : la seule signature RIFF ne
            // suffit pas a conclure qu'il s'agit d'une image.
            byte[] wav = new byte[12];
            System.arraycopy(new byte[]{0x52, 0x49, 0x46, 0x46}, 0, wav, 0, 4);
            System.arraycopy("WAVE".getBytes(StandardCharsets.US_ASCII), 0, wav, 8, 4);

            assertThat(TypeMedia.reconnaitre(wav)).isEmpty();
        }

        @Test
        @DisplayName("un WebP complet est reconnu")
        void webpReconnu() {
            byte[] webp = new byte[12];
            System.arraycopy(new byte[]{0x52, 0x49, 0x46, 0x46}, 0, webp, 0, 4);
            System.arraycopy("WEBP".getBytes(StandardCharsets.US_ASCII), 0, webp, 8, 4);

            assertThat(TypeMedia.reconnaitre(webp)).contains(TypeMedia.WEBP);
        }

        @Test
        @DisplayName("un en-tete trop court ne reconnait rien")
        void enteteTropCourt() {
            assertThat(TypeMedia.reconnaitre(new byte[]{(byte) 0xFF})).isEmpty();
            assertThat(TypeMedia.reconnaitre(null)).isEmpty();
        }
    }
}
