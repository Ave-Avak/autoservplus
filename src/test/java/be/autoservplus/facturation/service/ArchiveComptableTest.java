package be.autoservplus.facturation.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Archivage sur disque : emplacement, relecture, et refus de tout nom de fichier
 * qui ne serait pas un numero de facture.
 */
@DisplayName("ArchiveComptable")
class ArchiveComptableTest {

    private static final byte[] PDF = "%PDF-1.4 contenu".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path racine;

    private ArchiveComptable archive() {
        return new ArchiveComptable(racine.toString());
    }

    @Test
    @DisplayName("classe la facture par exercice et retourne un chemin RELATIF")
    void classementParExercice() {
        String chemin = archive().archiver((short) 2026, "2026-0042", PDF);

        // Chemin relatif en base : deplacer l archive ne rend pas caduques des
        // milliers de lignes facture.chemin_pdf.
        assertThat(chemin).isEqualTo("2026/2026-0042.pdf");
        assertThat(racine.resolve(chemin)).exists().hasBinaryContent(PDF);
    }

    @Test
    @DisplayName("relit exactement ce qui a ete archive")
    void relecture() {
        ArchiveComptable archive = archive();
        String chemin = archive.archiver((short) 2026, "2026-0001", PDF);

        assertThat(archive.lire(chemin)).hasValue(PDF);
    }

    @Test
    @DisplayName("ne laisse aucun fichier temporaire derriere elle")
    void aucunResidu() throws Exception {
        archive().archiver((short) 2026, "2026-0001", PDF);

        try (var fichiers = Files.list(racine.resolve("2026"))) {
            assertThat(fichiers.map(f -> f.getFileName().toString()))
                    .containsExactly("2026-0001.pdf");
        }
    }

    @Test
    @DisplayName("un fichier disparu de l'archive se signale, il ne casse pas")
    void fichierDisparu() {
        // Le service regenere alors : mieux vaut un document reconstruit qu un
        // client sans facture.
        assertThat(archive().lire("2026/2026-9999.pdf")).isEmpty();
    }

    @Test
    @DisplayName("un chemin qui sortirait de l'archive n'est jamais lu")
    void refuseLaTraverseeDeRepertoire() {
        assertThat(archive().lire("../../etc/passwd")).isEmpty();
    }

    @Test
    @DisplayName("seul un numero de facture peut composer un nom de fichier")
    void refuseUnNumeroInvalide() {
        assertThatThrownBy(() -> archive().archiver((short) 2026, "../../evasion", PDF))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalide");
    }

    @Test
    @DisplayName("reemettre le meme numero remplace le fichier au lieu d'en creer un second")
    void archivageIdempotent() {
        ArchiveComptable archive = archive();
        archive.archiver((short) 2026, "2026-0001", PDF);
        byte[] regenere = "%PDF-1.4 reconstruit".getBytes(StandardCharsets.UTF_8);

        String chemin = archive.archiver((short) 2026, "2026-0001", regenere);

        assertThat(archive.lire(chemin)).hasValue(regenere);
    }
}
