package be.autoservplus.stockage.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Dimensions lues dans l'en-tête d'une image")
class DimensionsImageTest {

    private static byte[] image(String format, int largeur, int hauteur) throws IOException {
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(largeur, hauteur, BufferedImage.TYPE_INT_RGB), format, sortie);
        return sortie.toByteArray();
    }

    /** En-tête RIFF de 30 octets dont le bloc nommé commence à l'octet 12. */
    private static byte[] webp(String bloc, byte[] apresLeNom) {
        byte[] b = new byte[30];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, b, 0, 4);
        System.arraycopy("WEBP".getBytes(StandardCharsets.US_ASCII), 0, b, 8, 4);
        System.arraycopy(bloc.getBytes(StandardCharsets.US_ASCII), 0, b, 12, 4);
        System.arraycopy(apresLeNom, 0, b, 16, apresLeNom.length);
        return b;
    }

    @ParameterizedTest
    @ValueSource(strings = {"png", "jpg"})
    @DisplayName("JPEG et PNG, par le lecteur d'ImageIO")
    void jpegEtPng(String format) throws IOException {
        assertThat(DimensionsImage.lire(image(format, 64, 48)))
                .contains(new DimensionsImage(64, 48));
    }

    @Test
    @DisplayName("WebP étendu (VP8X) : toile sur 24 bits, moins un")
    void webpEtendu() {
        byte[] suite = new byte[14];
        // octets 24 à 26 : 1023, octets 27 à 29 : 767
        suite[8] = (byte) 0xFF; suite[9] = 0x03;
        suite[11] = (byte) 0xFF; suite[12] = 0x02;
        assertThat(DimensionsImage.lire(webp("VP8X", suite))).contains(new DimensionsImage(1024, 768));
    }

    @Test
    @DisplayName("WebP sans pertes (VP8L) : deux valeurs de 14 bits, moins un")
    void webpSansPertes() {
        int bits = (480 - 1) | (360 - 1) << 14;
        byte[] suite = new byte[14];
        suite[4] = 0x2F;
        suite[5] = (byte) bits; suite[6] = (byte) (bits >> 8); suite[7] = (byte) (bits >> 16); suite[8] = (byte) (bits >> 24);
        assertThat(DimensionsImage.lire(webp("VP8L", suite))).contains(new DimensionsImage(480, 360));
    }

    @Test
    @DisplayName("WebP avec pertes (VP8) : deux valeurs de 14 bits après la signature de trame")
    void webpAvecPertes() {
        byte[] suite = new byte[14];
        suite[10] = (byte) (640 & 0xFF); suite[11] = (byte) (640 >> 8);
        suite[12] = (byte) (480 & 0xFF); suite[13] = (byte) (480 >> 8);
        assertThat(DimensionsImage.lire(webp("VP8 ", suite))).contains(new DimensionsImage(640, 480));
    }

    @Test
    @DisplayName("un contenu inconnu, tronqué ou absent ne donne rien, sans lever")
    void illisible() {
        byte[] jpegTronque = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        assertThat(DimensionsImage.lire("du texte qui n'est pas une image".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(DimensionsImage.lire(jpegTronque)).isEmpty();
        assertThat(DimensionsImage.lire(webp("VP8Z", new byte[14]))).isEmpty();
        assertThat(DimensionsImage.lire(null)).isEmpty();
    }

    @Test
    @DisplayName("des dimensions nulles ou négatives sont refusées")
    void dimensionsPositives() {
        assertThatThrownBy(() -> new DimensionsImage(0, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DimensionsImage(10, -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
