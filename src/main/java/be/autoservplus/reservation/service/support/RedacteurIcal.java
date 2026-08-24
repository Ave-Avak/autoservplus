package be.autoservplus.reservation.service.support;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Mise en forme d un evenement au format iCalendar (RFC 5545).
 *
 * <p><b>Ecrit a la main, sans bibliotheque.</b> Un seul {@code VEVENT} sans
 * recurrence, sans fuseau embarque et sans piece jointe demande trois regles de
 * serialisation — echappement, pliage de ligne, horodatage UTC — toutes enoncees
 * ci-dessous. Ajouter une dependance pour les trois aurait coute davantage a
 * justifier qu a ecrire : une bibliotheque de calendrier traine son propre modele
 * objet, ses formats de date et sa licence, pour un fichier de vingt lignes.</p>
 *
 * <p>Les trois regles, chacune un piege classique :</p>
 * <ul>
 *   <li><b>Fins de ligne CRLF</b> (RFC 5545 §3.1). Un fichier en LF seul est
 *       accepte par certains clients et rejete par d autres ; l ecrire correctement
 *       coute un caractere.</li>
 *   <li><b>Pliage a 75 octets</b> (§3.1), et bien <b>octets</b>, pas caracteres :
 *       le pliage doit se faire sur la representation UTF-8, et jamais au milieu
 *       d une sequence multi-octets — sans quoi un « é » coupe en deux produit un
 *       fichier invalide. C est la raison du calcul sur {@code byte[]} plus bas.</li>
 *   <li><b>Echappement des valeurs de texte</b> (§3.3.11) : la barre oblique
 *       inverse, le point-virgule et la virgule doivent etre echappes, et un retour
 *       a la ligne devient {@code \n}. Une adresse belge contient une virgule ;
 *       sans echappement, elle scinderait la valeur de {@code LOCATION} en deux.</li>
 * </ul>
 *
 * <p><b>Tous les instants sont ecrits en UTC</b> (suffixe {@code Z}, forme dite
 * « UTC date-time » §3.3.5). C est le format qui dispense d embarquer un
 * {@code VTIMEZONE} : le client de calendrier convertit lui-meme vers le fuseau du
 * lecteur, ce qui est exactement le bon comportement pour un membre qui consulte
 * son agenda depuis l etranger. Le projet stockant deja en UTC, aucune conversion
 * n intervient ici.</p>
 */
public final class RedacteurIcal {

    private static final String CRLF = "\r\n";
    private static final int OCTETS_PAR_LIGNE = 75;

    /** Forme « UTC date-time » de la RFC 5545 : 20260916T080000Z. */
    private static final DateTimeFormatter HORODATAGE_UTC =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    /**
     * Identifie le producteur du fichier (§3.7.3). La forme {@code -//...//...//EN}
     * est celle des identifiants de produit prives ; le suffixe de langue est
     * conventionnel et ne decrit pas la langue du contenu.
     */
    private static final String PRODUCTEUR = "-//AutoServ+//Rendez-vous//EN";

    private RedacteurIcal() {
        // utilitaire non instanciable
    }

    /** Le calendrier complet, pret a etre servi ou joint a un courriel. */
    public static String calendrier(EvenementIcal evenement) {
        StringBuilder ics = new StringBuilder();
        ligne(ics, "BEGIN", "VCALENDAR");
        ligne(ics, "VERSION", "2.0");
        ligne(ics, "PRODID", PRODUCTEUR);
        ligne(ics, "CALSCALE", "GREGORIAN");
        // PUBLISH et non REQUEST : le garage diffuse une information, il n envoie pas
        // une invitation a laquelle le membre devrait repondre. REQUEST ferait
        // apparaitre des boutons « Accepter / Refuser » dans l agenda, alors que la
        // decision se prend sur le site — et une reponse partie du calendrier
        // n arriverait nulle part.
        ligne(ics, "METHOD", "PUBLISH");

        ligne(ics, "BEGIN", "VEVENT");
        ligne(ics, "UID", evenement.uid());
        ligne(ics, "DTSTAMP", HORODATAGE_UTC.format(evenement.horodatage()));
        ligne(ics, "DTSTART", HORODATAGE_UTC.format(evenement.debut()));
        ligne(ics, "DTEND", HORODATAGE_UTC.format(evenement.fin()));
        ligne(ics, "SUMMARY", echapper(evenement.resume()));
        ligne(ics, "LOCATION", echapper(evenement.lieu()));
        ligne(ics, "DESCRIPTION", echapper(evenement.description()));
        // URL porte une valeur de type URI : elle n est PAS echappee comme du texte,
        // sous peine de transformer les separateurs de l adresse en litteraux.
        ligne(ics, "URL", evenement.url());
        ligne(ics, "STATUS", "CONFIRMED");

        if (evenement.rappelAvant() != null) {
            ligne(ics, "BEGIN", "VALARM");
            ligne(ics, "ACTION", "DISPLAY");
            ligne(ics, "DESCRIPTION", echapper(evenement.resume()));
            ligne(ics, "TRIGGER", declencheurAvant(evenement.rappelAvant()));
            ligne(ics, "END", "VALARM");
        }

        ligne(ics, "END", "VEVENT");
        ligne(ics, "END", "VCALENDAR");
        return ics.toString();
    }

    /**
     * Duree relative negative de la RFC 5545 §3.3.6, exprimee en heures :
     * {@code -PT24H}. Le signe porte la semantique « avant le debut » ; le format
     * {@code Duration.toString()} de Java produit {@code PT24H} mais aussi des
     * formes que la RFC ignore (fractions de seconde), d ou la construction
     * explicite.
     */
    private static String declencheurAvant(Duration delai) {
        return "-PT" + delai.toHours() + "H";
    }

    /** Echappement des valeurs de type TEXT (RFC 5545 §3.3.11). */
    static String echapper(String valeur) {
        if (valeur == null) {
            return "";
        }
        return valeur
                // La barre oblique inverse EN PREMIER : la traiter apres les autres
                // re-echapperait les echappements qu on vient d introduire.
                .replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n");
    }

    private static void ligne(StringBuilder ics, String propriete, String valeur) {
        ics.append(plier(propriete + ":" + valeur)).append(CRLF);
    }

    /**
     * Pliage a 75 octets (RFC 5545 §3.1) : les lignes suivantes commencent par une
     * espace, que le lecteur retire pour reconstituer la valeur.
     *
     * <p>Le decoupage se fait sur les octets UTF-8 et recule tant que l octet en
     * tete de la ligne suivante est un octet de continuation ({@code 10xxxxxx}) :
     * c est ainsi qu un caractere accentue n est jamais coupe en deux. Le budget de
     * la premiere ligne est de 75 octets, celui des suivantes de 74, l espace de
     * continuation comptant dans la limite.</p>
     */
    static String plier(String ligne) {
        byte[] octets = ligne.getBytes(StandardCharsets.UTF_8);
        if (octets.length <= OCTETS_PAR_LIGNE) {
            return ligne;
        }
        StringBuilder plie = new StringBuilder();
        int position = 0;
        boolean premiere = true;
        while (position < octets.length) {
            int budget = premiere ? OCTETS_PAR_LIGNE : OCTETS_PAR_LIGNE - 1;
            int fin = Math.min(position + budget, octets.length);
            while (fin > position && fin < octets.length && estContinuation(octets[fin])) {
                fin--;
            }
            if (!premiere) {
                plie.append(CRLF).append(' ');
            }
            plie.append(new String(octets, position, fin - position, StandardCharsets.UTF_8));
            position = fin;
            premiere = false;
        }
        return plie.toString();
    }

    /** Octet de continuation UTF-8 : bits de poids fort {@code 10}. */
    private static boolean estContinuation(byte octet) {
        return (octet & 0xC0) == 0x80;
    }
}
