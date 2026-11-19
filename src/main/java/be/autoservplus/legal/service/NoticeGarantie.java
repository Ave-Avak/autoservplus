package be.autoservplus.legal.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Notice harmonisée de l'Union européenne sur la garantie légale de conformité (art.
 * VI.45 § 1er, 12° CDE, règlement d'exécution (UE) 2025/1960, annexe I).
 *
 * <p>Les fichiers sont ceux de la Commission, jamais redessinés : l'annexe I interdit de
 * modifier le moindre élément de la notice. Le SVG en couleur sert la page, la page 1 du
 * PDF en couleur part en pièce jointe du courriel de confirmation. Source et empreintes
 * dans {@code THIRD-PARTY.md}.</p>
 */
@Component
public class NoticeGarantie {

    /** Langues dont la notice est livrée. Toute autre langue reçoit la notice française. */
    private static final String[] LANGUES = {"fr", "nl", "en"};
    private static final String LANGUE_PAR_DEFAUT = "fr";

    private final Map<String, byte[]> pdfs = new ConcurrentHashMap<>();

    /** Code de la langue de la notice servie pour cette locale. */
    public String langue(Locale locale) {
        String demandee = locale == null ? LANGUE_PAR_DEFAUT : locale.getLanguage();
        for (String langue : LANGUES) {
            if (langue.equals(demandee)) {
                return langue;
            }
        }
        return LANGUE_PAR_DEFAUT;
    }

    /** Adresse du SVG en couleur, servi comme image et jamais inséré dans la page. */
    public String image(Locale locale) {
        return "/images/notice-garantie/notice-garantie-" + langue(locale) + ".svg";
    }

    /** Page 1 du PDF de la Commission, la version en couleur, telle qu'elle a été extraite. */
    public byte[] pdf(Locale locale) {
        return pdfs.computeIfAbsent(langue(locale), NoticeGarantie::lire).clone();
    }

    public String nomFichierPdf(Locale locale) {
        return "notice-garantie-" + langue(locale) + ".pdf";
    }

    private static byte[] lire(String langue) {
        try (InputStream flux = new ClassPathResource(
                "notice-garantie/notice-garantie-" + langue + ".pdf").getInputStream()) {
            return flux.readAllBytes();
        } catch (IOException absent) {
            throw new UncheckedIOException("Notice de garantie introuvable pour la langue " + langue, absent);
        }
    }
}
