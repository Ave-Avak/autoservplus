import com.sass_lang.embedded_protocol.OutputStyle;
import de.larsgrefer.sass.embedded.CompileSuccess;
import de.larsgrefer.sass.embedded.SassCompiler;
import de.larsgrefer.sass.embedded.SassCompilerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Compile la feuille de style du site au build, lancé par Maven à {@code generate-resources}.
 *
 * <p>Ni Node ni téléchargement : le compilateur Dart Sass est livré dans une dépendance de
 * Maven Central, pour Windows comme pour Linux, et les sources de Bootstrap viennent de son
 * webjar. Le programme n'est pas dans {@code src/main} : il ne fait pas partie de
 * l'application, et ses dépendances restent en portée {@code test}, hors de l'archive
 * livrée.</p>
 *
 * <p>Arguments : la source Sass, la feuille produite, la version de Bootstrap, le dossier
 * où extraire ses sources, le fichier où copier son script.</p>
 *
 * <p>Les avertissements de dépréciation des sources de Bootstrap 5.3 sont tus pour elles
 * seules ({@code quietDeps}). Bootstrap 5.3 ne se charge que par {@code @import}, dont la
 * dépréciation est tue aussi : une autre alerte de nos sources reste affichée.</p>
 */
public class CompilationFeuilleDeStyle {

    public static void main(String[] args) throws Exception {
        Path source = Path.of(args[0]);
        Path feuille = Path.of(args[1]);
        String version = args[2];
        Path sourcesBootstrap = Path.of(args[3]);
        Path script = Path.of(args[4]);

        String racine = "META-INF/resources/webjars/bootstrap/" + version + "/";
        extraire(racine + "scss/", sourcesBootstrap);
        copier(racine + "dist/js/bootstrap.bundle.min.js", script);

        try (SassCompiler compilateur = SassCompilerFactory.bundled()) {
            compilateur.setLoadPaths(List.of(sourcesBootstrap.toFile(), source.getParent().toFile()));
            compilateur.setQuietDeps(true);
            compilateur.addSilenceDeprecation("import");
            compilateur.setOutputStyle(OutputStyle.COMPRESSED);
            CompileSuccess resultat = compilateur.compileFile(source.toFile());
            Files.createDirectories(feuille.getParent());
            Files.writeString(feuille, resultat.getCss(), StandardCharsets.UTF_8);
        }
    }

    /** Extrait le dossier de sources du webjar, trouvé sur le classpath du programme. */
    private static void extraire(String dossier, Path cible) throws IOException {
        URL url = CompilationFeuilleDeStyle.class.getClassLoader().getResource(dossier + "bootstrap.scss");
        if (url == null) {
            throw new IllegalStateException("Sources de Bootstrap introuvables : " + dossier);
        }
        JarURLConnection connexion = (JarURLConnection) url.openConnection();
        try (JarFile archive = connexion.getJarFile()) {
            for (JarEntry entree : archive.stream().toList()) {
                if (entree.isDirectory() || !entree.getName().startsWith(dossier)) {
                    continue;
                }
                Path destination = cible.resolve(entree.getName().substring(dossier.length())).normalize();
                if (!destination.startsWith(cible)) {
                    throw new IllegalStateException("Entrée hors du dossier : " + entree.getName());
                }
                Files.createDirectories(destination.getParent());
                try (InputStream flux = archive.getInputStream(entree)) {
                    Files.copy(flux, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void copier(String ressource, Path cible) throws IOException {
        try (InputStream flux = CompilationFeuilleDeStyle.class.getClassLoader().getResourceAsStream(ressource)) {
            if (flux == null) {
                throw new IllegalStateException("Script de Bootstrap introuvable : " + ressource);
            }
            Files.createDirectories(cible.getParent());
            Files.copy(flux, cible, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
