package be.autoservplus.reservation.service.dto;

import java.nio.charset.StandardCharsets;

/**
 * Fichier iCalendar produit pour un rendez-vous : son nom propose et son contenu.
 *
 * <p>Le contenu reste une chaine et non des octets : c est la forme dans laquelle le
 * redacteur le produit, et la seule ou il reste lisible en test. La conversion en
 * UTF-8 est faite ici, en un point unique, pour que le fichier telecharge et le
 * fichier joint au courriel soient <b>rigoureusement identiques</b> — les clients de
 * calendrier reconnaissent un evenement a son UID et remplacent l existant : deux
 * variantes du meme rendez-vous se recouvriraient au lieu de coexister, mais rien ne
 * garantirait laquelle survit.</p>
 */
public record FichierAgenda(String nomFichier, String contenu) {

    public static final String TYPE_MIME = "text/calendar";

    public byte[] octets() {
        return contenu.getBytes(StandardCharsets.UTF_8);
    }
}
