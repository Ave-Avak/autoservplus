package be.autoservplus.stockage.service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Optional;

/**
 * Largeur et hauteur d'une image en pixels, lues dans l'en-tête du fichier.
 *
 * <p>La page qui déclare les dimensions d'une image lui réserve sa place avant le
 * chargement : la mise en page ne saute pas quand l'image arrive. Seul l'en-tête est lu,
 * jamais l'image entière : décoder un fichier de plusieurs mégaoctets pour deux nombres
 * coûterait de la mémoire à chaque dépôt.</p>
 *
 * <p>JPEG et PNG passent par le lecteur d'ImageIO, qui s'arrête à l'en-tête. Le JDK ne lit
 * pas le WebP : ses trois variantes (VP8 avec pertes, VP8L sans pertes, VP8X étendue)
 * portent leurs dimensions à une place fixe, lue ici sans dépendance.</p>
 */
public record DimensionsImage(int largeur, int hauteur) {

    public DimensionsImage {
        if (largeur <= 0 || hauteur <= 0) {
            throw new IllegalArgumentException("Dimensions d'image strictement positives attendues.");
        }
    }

    /**
     * Dimensions lues dans les premiers octets du fichier, ou rien si le format n'est pas
     * reconnu ou si l'en-tête est illisible. Une image sans dimensions reste affichable.
     */
    public static Optional<DimensionsImage> lire(byte[] contenu) {
        if (contenu == null) {
            return Optional.empty();
        }
        return TypeMedia.reconnaitre(contenu).flatMap(type -> type == TypeMedia.WEBP
                ? depuisEnTeteWebp(contenu)
                : depuisImageIo(contenu));
    }

    private static Optional<DimensionsImage> depuisImageIo(byte[] contenu) {
        try (ImageInputStream flux = ImageIO.createImageInputStream(new ByteArrayInputStream(contenu))) {
            Iterator<ImageReader> lecteurs = ImageIO.getImageReaders(flux);
            if (!lecteurs.hasNext()) {
                return Optional.empty();
            }
            ImageReader lecteur = lecteurs.next();
            try {
                lecteur.setInput(flux, true, true);
                return creer(lecteur.getWidth(0), lecteur.getHeight(0));
            } finally {
                lecteur.dispose();
            }
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /**
     * Le bloc qui suit l'en-tête RIFF (octets 12 à 15) dit la variante. VP8X porte la
     * taille de la toile sur 24 bits, moins un. VP8L porte largeur et hauteur sur 14 bits
     * chacune, moins un, après un octet de signature. VP8 les porte sur 14 bits après sa
     * signature de trame.
     */
    private static Optional<DimensionsImage> depuisEnTeteWebp(byte[] b) {
        if (b.length < 30) {
            return Optional.empty();
        }
        String bloc = new String(b, 12, 4, StandardCharsets.US_ASCII);
        return switch (bloc) {
            case "VP8X" -> creer(1 + entier24(b, 24), 1 + entier24(b, 27));
            case "VP8L" -> {
                int bits = (b[21] & 0xFF) | (b[22] & 0xFF) << 8 | (b[23] & 0xFF) << 16 | (b[24] & 0xFF) << 24;
                yield creer(1 + (bits & 0x3FFF), 1 + ((bits >>> 14) & 0x3FFF));
            }
            case "VP8 " -> creer(entier16(b, 26) & 0x3FFF, entier16(b, 28) & 0x3FFF);
            default -> Optional.empty();
        };
    }

    private static int entier24(byte[] b, int depart) {
        return (b[depart] & 0xFF) | (b[depart + 1] & 0xFF) << 8 | (b[depart + 2] & 0xFF) << 16;
    }

    private static int entier16(byte[] b, int depart) {
        return (b[depart] & 0xFF) | (b[depart + 1] & 0xFF) << 8;
    }

    private static Optional<DimensionsImage> creer(int largeur, int hauteur) {
        return largeur > 0 && hauteur > 0
                ? Optional.of(new DimensionsImage(largeur, hauteur))
                : Optional.empty();
    }
}
