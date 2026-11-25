package be.autoservplus.galerie.service;

import be.autoservplus.stockage.service.StockageMedia;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * Dépose les photos de démonstration sous la racine des médias, au démarrage sous le
 * profil {@code demo}.
 *
 * <p>La graine ({@code db/demo/V911}) ne pose que des lignes {@code photo} : une
 * migration n'écrit pas de fichier. Les fichiers voyagent donc avec l'application, sous
 * {@code demonstration/photos} du classpath, et sont copiés ici vers le dossier
 * {@code demonstration} de la racine, là où les lignes les cherchent. Une base de
 * démonstration restaurée sur un volume neuf retrouve ainsi ses images au premier
 * démarrage.</p>
 *
 * <p>Un fichier déjà présent n'est jamais remplacé : un redémarrage ne réécrit rien.
 * Hors du profil {@code demo}, ce composant n'existe pas, et aucune ligne ne pointe ce
 * dossier.</p>
 */
@Component
@Profile("demo")
public class PhotosDemonstration implements ApplicationRunner {

    /** Dossier des photos de démonstration, sous la racine des médias comme dans le classpath. */
    static final String DOSSIER = "demonstration";

    private static final Logger JOURNAL = LoggerFactory.getLogger(PhotosDemonstration.class);

    private final StockageMedia stockage;

    public PhotosDemonstration(StockageMedia stockage) {
        this.stockage = stockage;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        Resource[] fichiers;
        try {
            fichiers = new PathMatchingResourcePatternResolver()
                    .getResources("classpath:" + DOSSIER + "/photos/*.webp");
        } catch (IOException e) {
            throw new UncheckedIOException("Lecture des photos de démonstration impossible", e);
        }
        int deposees = 0;
        for (Resource fichier : fichiers) {
            try (InputStream contenu = fichier.getInputStream()) {
                if (stockage.deposerSiAbsent(DOSSIER + "/" + fichier.getFilename(), contenu)) {
                    deposees++;
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Copie de " + fichier.getFilename() + " impossible", e);
            }
        }
        JOURNAL.info("Photos de démonstration : {} déposées sur {}.", deposees, fichiers.length);
    }
}
