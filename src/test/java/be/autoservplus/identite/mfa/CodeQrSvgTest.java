package be.autoservplus.identite.mfa;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("QR code en SVG inline")
class CodeQrSvgTest {

    @Test
    @DisplayName("produit un SVG accessible, à fond blanc, sans référence externe")
    void svgAutonome() {
        String svg = CodeQrSvg.svg("otpauth://totp/AutoServ%2B:a%40b.be?secret=MZXW6YTBOI",
                "Code QR à scanner");

        assertThat(svg).startsWith("<svg").endsWith("</svg>")
                .contains("role=\"img\"", "aria-label=\"Code QR à scanner\"", "fill=\"#ffffff\"")
                .contains("<path fill=\"#000000\" d=\"M")
                .doesNotContain("href", "https://");
        // Seule URL admise : l identifiant d espace de noms SVG, qui n est jamais charge.
        assertThat(svg.replace("xmlns=\"http://www.w3.org/2000/svg\"", "")).doesNotContain("http://");
    }

    @Test
    @DisplayName("échappe le libellé : il ne peut pas sortir de son attribut")
    void libelleEchappe() {
        String svg = CodeQrSvg.svg("x", "a\" onload=\"alert(1)<");

        assertThat(svg).contains("aria-label=\"a&quot; onload=&quot;alert(1)&lt;\"")
                .doesNotContain("onload=\"alert");
    }
}
