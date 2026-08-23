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
 * Archivage des documents comptables PDF sur le systeme de fichiers.
 *
 * <p>La loi belge impose la conservation des factures <b>sept ans</b> (Code TVA
 * art. 60). Un document regenere a chaque demande ne serait pas une archive : il
 * refleterait le code du jour, pas celui de l emission. Le PDF est donc ecrit une
 * fois puis relu tel quel, et c est ce meme fichier qui sert de piece justificative.</p>
 *
 * <p>Le nom de la classe ne dit plus « factures » : la note de credit qui corrige une
 * facture releve de la meme obligation de conservation et sera rangee dans la meme
 * archive. La generalisation est faite <b>avant</b> son arrivee, pour que le bloc qui
 * l ajoutera n ait pas a renommer en meme temps qu il implemente.</p>
 *
 * <p>Racine configurable ({@code autoservplus.facturation.archive}), jamais en dur :
 * en production elle designe un volume sauvegarde, distinct du repertoire de
 * deploiement — une archive de sept ans ne peut pas disparaitre avec un
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
     * Un numero de facture, et rien d autre, peut composer un nom de fichier. Le
     * numero est produit par l application et non par l utilisateur, mais un nom de
     * fichier construit sans controle reste une porte ouverte a la traversee de
     * repertoire : la garde coute une ligne.
     */
    private static final Pattern NUMERO_VALIDE = Pattern.compile("\\d{4}-\\d{4,}");

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
            log.info("Facture {} archivee ({} octets).", numero, pdf.length);
            return cheminRelatif;
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Archivage de la facture %s impossible.".formatted(numero), e);
        }
    }

    /**
     * Relit une facture archivee.
     *
     * @return le PDF, ou {@link Optional#empty()} si le fichier a disparu de
     *         l archive — le service regenere alors plutot que d echouer : mieux
     *         vaut un document reconstruit qu un client sans facture
     */
    public Optional<byte[]> lire(String cheminRelatif) {
        Path fichier = racine.resolve(cheminRelatif).normalize();
        if (!fichier.startsWith(racine) || !Files.isRegularFile(fichier)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(fichier));
        } catch (IOException e) {
            log.warn("Facture archivee illisible ({}) : elle sera regeneree. {}",
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
                    "Numero de facture invalide pour un nom de fichier : " + numero);
        }
    }
}
