package be.autoservplus.rgpd.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Archive remise au membre par l'export F22 : le document structuré, puis les copies
 * des fichiers qu'il déclare (photos d'intervention).
 *
 * <p>Une archive et non un JSON seul : l'article 15, § 3 du RGPD vise une copie des
 * données, et une photo ne se restitue pas par sa description. Le format reste le
 * même qu'il y ait des photos ou non, pour qu'un export se relise toujours de la même
 * façon.</p>
 */
final class ArchiveExport {

    /** Nom du document structuré dans l'archive. */
    static final String DOCUMENT = "donnees.json";

    private ArchiveExport() {
    }

    /**
     * @param fichiers contenu de chaque fichier joint, par son chemin dans l'archive,
     *                 dans l'ordre où il doit y apparaître
     */
    static byte[] zipper(byte[] document, Map<String, byte[]> fichiers) {
        ByteArrayOutputStream tampon = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(tampon)) {
            ecrire(zip, DOCUMENT, document);
            for (Map.Entry<String, byte[]> fichier : fichiers.entrySet()) {
                ecrire(zip, fichier.getKey(), fichier.getValue());
            }
        } catch (IOException e) {
            // Une écriture en mémoire n'échoue pas en pratique : l'exception est celle de
            // l'API, pas d'un disque.
            throw new UncheckedIOException("Archive d'export impossible à produire", e);
        }
        return tampon.toByteArray();
    }

    private static void ecrire(ZipOutputStream zip, String nom, byte[] contenu) throws IOException {
        zip.putNextEntry(new ZipEntry(nom));
        zip.write(contenu);
        zip.closeEntry();
    }
}
