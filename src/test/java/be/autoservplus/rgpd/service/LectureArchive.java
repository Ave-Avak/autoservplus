package be.autoservplus.rgpd.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Lecture de l'archive d'export F22 dans les tests : ses entrées, et le document JSON. */
public final class LectureArchive {

    private LectureArchive() {
    }

    /** Entrées de l'archive, dans leur ordre d'écriture. */
    public static Map<String, byte[]> entrees(byte[] archive) {
        Map<String, byte[]> entrees = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entree;
            while ((entree = zip.getNextEntry()) != null) {
                entrees.put(entree.getName(), zip.readAllBytes());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return entrees;
    }

    /** Document structuré de l'archive. */
    public static String document(byte[] archive) {
        byte[] json = entrees(archive).get(ArchiveExport.DOCUMENT);
        if (json == null) {
            throw new AssertionError("l'archive ne contient pas " + ArchiveExport.DOCUMENT);
        }
        return new String(json, StandardCharsets.UTF_8);
    }
}
