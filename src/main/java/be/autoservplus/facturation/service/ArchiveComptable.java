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
 * Archivage des documents comptables PDF sur le systeme de fichiers : factures et
 * notes de credit.
 *
 * <p>La loi belge impose la conservation des factures <b>dix ans</b> (Code TVA art. 60,
 * tel que modifie par la loi du 20 novembre 2022), et la note de credit qui en corrige
 * une est soumise a la meme obligation — une facture conservee sans son avoir donnerait
 * un montant faux au controle. Un document regenere a chaque demande ne serait pas une
 * archive : il refleterait le code du jour, pas celui de l emission. Le PDF est donc
 * ecrit une fois puis relu tel quel, et c est ce meme fichier qui sert de piece
 * justificative.</p>
 *
 * <p><b>Dix et non sept</b> : le delai etait de sept ans jusqu a la loi du 20 novembre
 * 2022, qui l a porte a dix pour les taxes devenues exigibles a partir du 1er janvier
 * 2023. Il court a compter du 1er janvier de l annee qui suit celle de la facture, et
 * non de sa date d emission — un mois de decembre et le janvier suivant ne sortent donc
 * pas de l archive la meme annee.</p>
 *
 * <p>Racine configurable ({@code autoservplus.facturation.archive}), jamais en dur :
 * en production elle designe un volume sauvegarde, distinct du repertoire de
 * deploiement — une archive de dix ans ne peut pas disparaitre avec un
 * redeploiement.</p>
 *
 * <p>Le chemin stocke en base est <b>relatif</b> a cette racine : deplacer l archive
 * (nouveau serveur, nouveau volume) ne rend pas invalides des milliers de lignes
 * {@code facture.chemin_pdf}. Arborescence par exercice, comme un classeur comptable.</p>
 */
@Component
public class ArchiveComptable {

    private static final Logger log = LoggerFactory.getLogger(ArchiveComptable.class);

    /**
     * Un numero de document comptable, et rien d autre, peut composer un nom de
     * fichier : {@code 2026-0001} pour une facture, {@code AV-2026-0001} pour un
     * avoir. Le numero est produit par l application et non par l utilisateur, mais un
     * nom de fichier construit sans controle reste une porte ouverte a la traversee de
     * repertoire : la garde coute une ligne.
     */
    private static final Pattern NUMERO_VALIDE = Pattern.compile("(AV-)?\\d{4}-\\d{4,}");

    private final Path racine;

    public ArchiveComptable(
            @Value("${autoservplus.facturation.archive:./data/factures}") String racine) {
        this.racine = Path.of(racine).toAbsolutePath().normalize();
    }

    /**
     * Ecrit le PDF dans l archive et retourne son chemin relatif, a stocker en base.
     *
     * <p>Ecriture en deux temps (fichier temporaire puis deplacement atomique) :
     * une interruption en cours d ecriture ne doit jamais laisser un PDF tronque a
     * l emplacement definitif, ou il serait ensuite servi comme s il etait complet.</p>
     */
    public String archiver(short exercice, String numero, byte[] pdf) {
        return ecrire("%d/%s.pdf".formatted(exercice, numero), numero, pdf);
    }

    /**
     * Ecrit la note de credit dans l archive et retourne son chemin relatif.
     *
     * <p>Sous-repertoire {@code avoirs} de l exercice plutot que melange aux factures :
     * les deux suites de numeros repartent a 1 chaque annee, et un classeur comptable
     * separe les pieces rectificatives des factures qu elles corrigent. Le prefixe
     * {@code AV-} suffirait a eviter la collision de noms ; le sous-repertoire ajoute
     * qu on retrouve d un coup d oeil tous les avoirs d un exercice.</p>
     */
    public String archiverAvoir(short exercice, String numero, byte[] pdf) {
        return ecrire("%d/avoirs/%s.pdf".formatted(exercice, numero), numero, pdf);
    }

    /**
     * Ecriture proprement dite, chemin relatif deja compose par l appelant.
     *
     * <p>Extraite d {@link #archiver} : la composition du chemin est le seul point
     * qui variera d un type de document a l autre, l ecriture atomique est commune.
     * Elle est isolee ici pour que l ajout d un second document n ait pas a dupliquer
     * le fichier temporaire, le deplacement atomique et leur rattrapage d erreur.</p>
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
     * Relit un document archive.
     *
     * @return le PDF, ou {@link Optional#empty()} si le fichier a disparu de
     *         l archive — le service regenere alors plutot que d echouer : mieux
     *         vaut un document reconstruit qu un client sans sa facture
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
            // Certains systemes de fichiers reseau ne savent pas deplacer
            // atomiquement : le deplacement simple reste preferable a une ecriture
            // directe, la fenetre d incoherence se reduit a la duree du move.
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
