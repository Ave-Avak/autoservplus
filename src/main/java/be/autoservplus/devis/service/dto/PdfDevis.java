package be.autoservplus.devis.service.dto;

/** Document d'un devis prêt à servir : son nom de fichier et ses octets. */
public record PdfDevis(String nomDeFichier, byte[] contenu) {
}
