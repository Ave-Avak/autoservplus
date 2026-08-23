package be.autoservplus.stockage.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Stockage des fichiers deposes par le back-office (fondation upload, prerequis BL-9
 * et BL-2).
 *
 * <p><b>Hors du webroot.</b> La racine par defaut est {@code ./data/uploads}, un
 * repertoire que rien ne sert statiquement : aucun fichier depose n est atteignable par
 * son chemin. Les images passent par un controleur qui verifie ce qu il sert — c est
 * ce qui permet a la CSP de rester {@code img-src 'self'} sans rien relacher, puisque
 * les images sont servies par l application elle-meme.</p>
 *
 * <p><b>Le nom d origine n est jamais reutilise.</b> Ni comme nom de fichier, ni pour
 * en deduire un type. Un nom fourni par le client peut contenir {@code ../}, un octet
 * nul, un nom reserve Windows ({@code CON}, {@code PRN}) ou 4 000 caracteres. Le
 * fichier est enregistre sous un UUID, avec l extension du type <b>reconnu aux octets
 * d en-tete</b> : il n existe donc aucun chemin par lequel une valeur du client
 * atteigne le systeme de fichiers.</p>
 *
 * <p><b>Trois controles, dans cet ordre</b> : fichier non vide, taille sous le plafond,
 * type reconnu. Le plafond est verifie avant la lecture du contenu pour ne pas charger
 * en memoire ce qu on va refuser.</p>
 */
@Service
public class StockageMedia {

    private static final Logger log = LoggerFactory.getLogger(StockageMedia.class);

    private final Path racine;
    private final long tailleMaximale;

    public StockageMedia(@Value("${autoservplus.stockage.racine:./data/uploads}") String racine,
                         @Value("${autoservplus.stockage.taille-max-octets:5242880}") long tailleMaximale) {
        this.racine = Path.of(racine).toAbsolutePath().normalize();
        this.tailleMaximale = tailleMaximale;
    }

    public long tailleMaximale() {
        return tailleMaximale;
    }

    /**
     * Enregistre un fichier et rend son chemin <b>relatif</b> a la racine.
     *
     * <p>Relatif et non absolu, comme pour l archive des factures : un chemin absolu
     * stocke en base casserait au premier changement de machine ou de volume.</p>
     *
     * @param sousDossier rangement logique ({@code prestations}, {@code interventions})
     * @return chemin relatif du type {@code prestations/3f2a….jpg}
     * @throws TypeFichierRefuseException      contenu non reconnu comme image admise
     * @throws FichierTropVolumineuxException  au-dela du plafond applicatif
     */
    public String enregistrer(MultipartFile fichier, String sousDossier) {
        if (fichier == null || fichier.isEmpty()) {
            throw new TypeFichierRefuseException(TypeMedia.libelleDesTypesAdmis());
        }
        if (fichier.getSize() > tailleMaximale) {
            throw new FichierTropVolumineuxException(tailleMaximale);
        }

        TypeMedia type = reconnaitre(fichier);
        // Nom entierement fabrique : rien de ce que le client a envoye ne s y retrouve.
        String nom = UUID.randomUUID() + type.extension();
        Path dossier = resoudre(sousDossier);

        try {
            Files.createDirectories(dossier);
            try (InputStream flux = fichier.getInputStream()) {
                Files.copy(flux, dossier.resolve(nom), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Enregistrement du media impossible", e);
        }
        return sousDossier + "/" + nom;
    }

    /** Contenu d un fichier deja enregistre, adresse par son chemin relatif. */
    public byte[] lire(String cheminRelatif) {
        try {
            return Files.readAllBytes(cheminSur(cheminRelatif));
        } catch (IOException e) {
            throw new UncheckedIOException("Lecture du media impossible", e);
        }
    }

    public boolean existe(String cheminRelatif) {
        return Files.isRegularFile(cheminSur(cheminRelatif));
    }

    /**
     * Supprime un fichier. L absence n est pas une erreur : la ligne de base a pu etre
     * effacee avant le fichier, ou le fichier avoir deja disparu d un volume restaure.
     */
    public void supprimer(String cheminRelatif) {
        try {
            Files.deleteIfExists(cheminSur(cheminRelatif));
        } catch (IOException e) {
            log.warn("Suppression du media {} impossible : {}", cheminRelatif, e.getMessage());
        }
    }

    /** Type MIME a servir, deduit de l extension que nous avons nous-memes posee. */
    public String typeMimeDe(String cheminRelatif) {
        for (TypeMedia type : TypeMedia.values()) {
            if (cheminRelatif.endsWith(type.extension())) {
                return type.typeMime();
            }
        }
        return "application/octet-stream";
    }

    private TypeMedia reconnaitre(MultipartFile fichier) {
        byte[] entete = new byte[TypeMedia.octetsNecessaires()];
        try (InputStream flux = fichier.getInputStream()) {
            int lus = flux.readNBytes(entete, 0, entete.length);
            if (lus < entete.length) {
                throw new TypeFichierRefuseException(TypeMedia.libelleDesTypesAdmis());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Lecture du fichier depose impossible", e);
        }
        return TypeMedia.reconnaitre(entete)
                .orElseThrow(() -> new TypeFichierRefuseException(
                        TypeMedia.libelleDesTypesAdmis()));
    }

    /**
     * Resout un chemin sous la racine en <b>refusant toute evasion</b>.
     *
     * <p>Ceinture et bretelles : les noms de fichiers sont deja fabriques par nous, mais
     * le sous-dossier vient du code appelant et une faute de frappe future ne doit pas
     * pouvoir ecrire ailleurs. La normalisation puis la verification d appartenance
     * arretent {@code ../} comme les chemins absolus.</p>
     */
    private Path resoudre(String relatif) {
        Path cible = racine.resolve(relatif).normalize();
        if (!cible.startsWith(racine)) {
            throw new IllegalArgumentException("Chemin de stockage hors de la racine.");
        }
        return cible;
    }

    private Path cheminSur(String cheminRelatif) {
        return resoudre(cheminRelatif);
    }
}
