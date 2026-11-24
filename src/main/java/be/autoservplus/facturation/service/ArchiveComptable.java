package be.autoservplus.facturation.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Archivage des documents comptables PDF sur le système de fichiers : factures et
 * notes de crédit.
 *
 * <p>La loi belge impose la conservation des factures <b>sept ans</b> (Code TVA art. 60,
 * tel que modifié par la loi du 18 décembre 2025), et la note de crédit qui en corrige
 * une est soumise à la même obligation : une facture conservée sans son avoir donnerait
 * un montant faux au contrôle. Un document régénéré à chaque demande ne serait pas une
 * archive : il refléterait le code du jour, pas celui de l'émission. Le PDF est donc
 * écrit une fois puis relu tel quel, et c'est ce même fichier qui sert de pièce
 * justificative.</p>
 *
 * <p><b>Sept ans, à compter du 1er janvier qui suit</b> : la loi du 20 novembre 2022 avait
 * porté le délai à dix ans, la loi du 18 décembre 2025 l'a ramené à sept. Il court à
 * compter du 1er janvier de l'année qui suit celle de la facture, et non de sa date
 * d'émission. Un mois de décembre et le janvier suivant ne sortent donc pas de l'archive
 * la même année.</p>
 *
 * <p>Racine configurable ({@code autoservplus.facturation.archive}), jamais en dur :
 * en production elle désigne un volume sauvegardé, distinct du répertoire de
 * déploiement. Une archive de sept ans ne peut pas disparaître avec un
 * redéploiement.</p>
 *
 * <p>Le chemin stocké en base est <b>relatif</b> à cette racine : déplacer l'archive
 * (nouveau serveur, nouveau volume) ne rend pas invalides des milliers de lignes
 * {@code facture.chemin_pdf}. Arborescence par exercice, comme un classeur comptable.</p>
 */
@Component
public class ArchiveComptable {

    private static final Logger log = LoggerFactory.getLogger(ArchiveComptable.class);

    /**
     * Un numéro de document comptable, et rien d'autre, peut composer un nom de
     * fichier : {@code 2026-0001} pour une facture, {@code AV-2026-0001} pour un
     * avoir. Le numéro est produit par l'application et non par l'utilisateur, mais un
     * nom de fichier construit sans contrôle reste une porte ouverte à la traversée de
     * répertoire : la garde coûte une ligne.
     */
    private static final Pattern NUMERO_VALIDE = Pattern.compile("(AV-)?\\d{4}-\\d{4,}");

    private final Path racine;

    public ArchiveComptable(
            @Value("${autoservplus.facturation.archive:./data/factures}") String racine) {
        this.racine = Path.of(racine).toAbsolutePath().normalize();
    }

    /**
     * Écrit le PDF dans l'archive et retourne son chemin relatif, à stocker en base.
     *
     * <p>Écriture en deux temps (fichier temporaire puis déplacement atomique) :
     * une interruption en cours d'écriture ne doit jamais laisser un PDF tronqué à
     * l'emplacement définitif, où il serait ensuite servi comme s'il était complet.</p>
     */
    public String archiver(short exercice, String numero, byte[] pdf) {
        return ecrire("%d/%s.pdf".formatted(exercice, numero), numero, pdf);
    }

    /**
     * Écrit la note de crédit dans l'archive et retourne son chemin relatif.
     *
     * <p>Sous-répertoire {@code avoirs} de l'exercice plutôt que mélangé aux factures :
     * les deux suites de numéros repartent à 1 chaque année, et un classeur comptable
     * sépare les pièces rectificatives des factures qu'elles corrigent. Le préfixe
     * {@code AV-} suffirait à éviter la collision de noms. Le sous-répertoire ajoute
     * qu'on retrouve d'un coup d'oeil tous les avoirs d'un exercice.</p>
     */
    public String archiverAvoir(short exercice, String numero, byte[] pdf) {
        return ecrire("%d/avoirs/%s.pdf".formatted(exercice, numero), numero, pdf);
    }

    /**
     * Écriture proprement dite, chemin relatif déjà composé par l'appelant.
     *
     * <p>Extraite d'{@link #archiver} : la composition du chemin est le seul point
     * qui variera d'un type de document à l'autre, l'écriture atomique est commune.
     * Elle est isolée ici pour que l'ajout d'un second document n'ait pas à dupliquer
     * le fichier temporaire, le déplacement atomique et leur rattrapage d'erreur.</p>
     */
    private String ecrire(String cheminRelatif, String numero, byte[] pdf) {
        exigerNumeroValide(numero);
        Path destination = racine.resolve(cheminRelatif);
        try {
            Files.createDirectories(destination.getParent());
            Path temporaire = Files.createTempFile(destination.getParent(), numero, ".part");
            Files.write(temporaire, pdf);
            deplacer(temporaire, destination);
            log.info("Document {} archive ({} octets).", numero, pdf.length);
            return cheminRelatif;
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Archivage du document %s impossible.".formatted(numero), e);
        }
    }

    /**
     * Relit un document archivé.
     *
     * @return le PDF, ou {@link Optional#empty()} si le fichier a disparu de
     *         l'archive. Le service régénère alors plutôt que d'échouer : mieux
     *         vaut un document reconstruit qu'un client sans sa facture
     */
    public Optional<byte[]> lire(String cheminRelatif) {
        Path fichier = racine.resolve(cheminRelatif).normalize();
        if (!fichier.startsWith(racine) || !Files.isRegularFile(fichier)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(fichier));
        } catch (IOException e) {
            log.warn("Document archive illisible ({}) : il sera regenere. {}",
                    cheminRelatif, e.getMessage());
            return Optional.empty();
        }
    }

    private void deplacer(Path temporaire, Path destination) throws IOException {
        try {
            Files.move(temporaire, destination,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            // Certains systèmes de fichiers réseau ne savent pas déplacer
            // atomiquement : le déplacement simple reste préférable à une écriture
            // directe, la fenêtre d'incohérence se réduit à la durée du move.
            Files.move(temporaire, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void exigerNumeroValide(String numero) {
        if (numero == null || !NUMERO_VALIDE.matcher(numero).matches()) {
            throw new IllegalArgumentException(
                    "Numero de document comptable invalide pour un nom de fichier : " + numero);
        }
    }
}
