package be.autoservplus.identite.mfa;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.Map;

/**
 * Rend un QR code en SVG inline, cote serveur.
 *
 * <p><b>SVG inline plutot qu une image {@code data:}.</b> La politique de securite du
 * contenu admet aujourd hui {@code img-src data:}, mais l enrolement n en depend pas :
 * resserrer un jour cette directive ne casserait rien. Les attributs {@code fill} sont des
 * attributs de presentation, que {@code style-src 'self'} ne bloque pas. Aucun service
 * externe n est appele — l URI encodee porte le secret.</p>
 *
 * <p><b>Fond blanc explicite</b> : en mode sombre, un QR code sans fond se dessinerait
 * noir sur sombre, et les applications ne le liraient plus.</p>
 */
public final class CodeQrSvg {

    /** Marge de quatre modules : la zone de silence que la norme exige autour du code. */
    private static final int MARGE = 4;

    private CodeQrSvg() {
    }

    /**
     * @param contenu           texte a encoder
     * @param libelleAccessible annonce du lecteur d ecran ; echappe ici
     */
    public static String svg(String contenu, String libelleAccessible) {
        BitMatrix matrice;
        try {
            matrice = new QRCodeWriter().encode(contenu, BarcodeFormat.QR_CODE, 0, 0,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                            EncodeHintType.MARGIN, MARGE));
        } catch (WriterException echec) {
            throw new IllegalStateException("QR code impossible a produire", echec);
        }
        int largeur = matrice.getWidth();
        int hauteur = matrice.getHeight();
        StringBuilder trace = new StringBuilder();
        for (int y = 0; y < hauteur; y++) {
            for (int x = 0; x < largeur; x++) {
                if (matrice.get(x, y)) {
                    trace.append('M').append(x).append(' ').append(y).append("h1v1h-1z");
                }
            }
        }
        return """
                <svg xmlns="http://www.w3.org/2000/svg" class="code-qr" role="img" \
                aria-label="%s" viewBox="0 0 %d %d" shape-rendering="crispEdges">\
                <rect width="%d" height="%d" fill="#ffffff"/>\
                <path fill="#000000" d="%s"/></svg>"""
                .formatted(echapper(libelleAccessible), largeur, hauteur, largeur, hauteur, trace);
    }

    private static String echapper(String texte) {
        return texte.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
