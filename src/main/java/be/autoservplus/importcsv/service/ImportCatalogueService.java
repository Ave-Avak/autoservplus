package be.autoservplus.importcsv.service;

import be.autoservplus.catalogue.repository.PieceRepository;
import be.autoservplus.catalogue.repository.PrestationRepository;
import be.autoservplus.catalogue.service.AdminCatalogueService;
import be.autoservplus.catalogue.service.dto.DonneesPiece;
import be.autoservplus.catalogue.service.dto.DonneesPrestation;
import be.autoservplus.importcsv.service.dto.RapportImport;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Import du catalogue depuis un fichier CSV (BL-2).
 *
 * <p><b>Tout ou rien.</b> La methode est transactionnelle et une ligne invalide fait
 * <b>echouer l import entier</b> : un catalogue a moitie importe est pire qu un import
 * refuse, parce que le garage ne sait plus ou il en est et qu un second essai creerait
 * des doublons sur les lignes deja passees. Le rapport enumere <b>toutes</b> les
 * erreurs avant de renvoyer, pour que le garage corrige son fichier en une fois plutot
 * que ligne apres ligne.</p>
 *
 * <p><b>Trouve-ou-cree sur le code.</b> Une ligne dont le code existe deja met a jour
 * l article ; sinon elle le cree. C est ce qui rend l import rejouable : le garage
 * corrige son tableur et le renvoie sans avoir a purger quoi que ce soit.</p>
 *
 * <p><b>En-tete obligatoire.</b> Sans elle, l ordre des colonnes serait implicite et
 * une inversion prix / taux passerait inapercue jusqu a la premiere facture. Les
 * colonnes sont donc reconnues par leur nom, et leur ordre est libre.</p>
 *
 * <p>{@code @PreAuthorize} de classe : l import ecrit dans le catalogue, donc dans les
 * prix. La protection d URL {@code /admin/**} filtre deja le role, le service refuse
 * en second.</p>
 */
@Service
@PreAuthorize("hasRole('ADMINISTRATEUR')")
public class ImportCatalogueService {

    /** Colonnes attendues pour les prestations ; l ordre dans le fichier est libre. */
    private static final List<String> COLONNES_PRESTATION =
            List.of("categorie", "code", "libelle", "description", "prix_htva", "taux_tva",
                    "duree_minutes");

    private static final List<String> COLONNES_PIECE =
            List.of("categorie", "reference_fabricant", "libelle", "marque", "description",
                    "prix_htva", "taux_tva", "stock", "seuil_alerte");

    private final AdminCatalogueService catalogue;
    private final PrestationRepository prestations;
    private final PieceRepository pieces;

    public ImportCatalogueService(AdminCatalogueService catalogue,
                                  PrestationRepository prestations,
                                  PieceRepository pieces) {
        this.catalogue = catalogue;
        this.prestations = prestations;
        this.pieces = pieces;
    }

    /** En-tete a fournir pour un import de prestations, dans l ordre recommande. */
    public static String enTetePrestations() {
        return String.join(";", COLONNES_PRESTATION);
    }

    public static String enTetePieces() {
        return String.join(";", COLONNES_PIECE);
    }

    @Transactional
    public RapportImport importerPrestations(byte[] contenu) {
        return importer(contenu, COLONNES_PRESTATION, this::traiterPrestation);
    }

    @Transactional
    public RapportImport importerPieces(byte[] contenu) {
        return importer(contenu, COLONNES_PIECE, this::traiterPiece);
    }

    // --- moteur commun -------------------------------------------------------------------

    private RapportImport importer(byte[] contenu, List<String> colonnesAttendues,
                                   TraitementLigne traitement) {
        List<List<String>> lignes = LecteurCsv.lire(new String(contenu, StandardCharsets.UTF_8));
        List<RapportImport.LigneEnErreur> erreurs = new ArrayList<>();

        if (lignes.isEmpty()) {
            erreurs.add(new RapportImport.LigneEnErreur(0, "Le fichier est vide."));
            return new RapportImport(0, 0, erreurs);
        }

        List<String> entete = lignes.getFirst().stream().map(String::toLowerCase).toList();
        List<String> manquantes = colonnesAttendues.stream()
                .filter(colonne -> !entete.contains(colonne))
                .toList();
        if (!manquantes.isEmpty()) {
            erreurs.add(new RapportImport.LigneEnErreur(1,
                    "Colonnes manquantes dans l'en-tête : " + String.join(", ", manquantes)));
            return new RapportImport(0, 0, erreurs);
        }

        int crees = 0;
        int misAJour = 0;
        for (int i = 1; i < lignes.size(); i++) {
            List<String> cellules = lignes.get(i);
            // +1 : l en-tete est la ligne 1 du fichier, la premiere donnee la ligne 2.
            int numeroDansLeFichier = i + 1;
            try {
                if (traitement.traiter(new Ligne(entete, cellules))) {
                    crees++;
                } else {
                    misAJour++;
                }
            } catch (RuntimeException e) {
                erreurs.add(new RapportImport.LigneEnErreur(numeroDansLeFichier, e.getMessage()));
            }
        }

        RapportImport rapport = new RapportImport(crees, misAJour, erreurs);
        if (!rapport.sansErreur()) {
            // Tout ou rien : la transaction est annulee, les compteurs du rapport ne
            // decrivent alors que ce qui AURAIT ete fait. Le rapport reste utile — il
            // dit exactement quelles lignes corriger.
            throw new ImportRefuseException(rapport);
        }
        return rapport;
    }

    /** @return {@code true} si l article a ete cree, {@code false} s il a ete mis a jour */
    private boolean traiterPrestation(Ligne ligne) {
        DonneesPrestation donnees = new DonneesPrestation(
                ligne.texteObligatoire("categorie"),
                ligne.texteObligatoire("code"),
                ligne.texteObligatoire("libelle"),
                ligne.texte("description"),
                ligne.montant("prix_htva"),
                ligne.tauxTva("taux_tva"),
                ligne.entier("duree_minutes"),
                true);

        var existante = prestations.findByCode(donnees.code());
        if (existante.isPresent()) {
            catalogue.modifierPrestation(existante.get().getReference(), donnees);
            return false;
        }
        catalogue.creerPrestation(donnees);
        return true;
    }

    private boolean traiterPiece(Ligne ligne) {
        DonneesPiece donnees = new DonneesPiece(
                ligne.texteObligatoire("categorie"),
                ligne.texteObligatoire("reference_fabricant"),
                ligne.texteObligatoire("libelle"),
                ligne.texte("marque"),
                ligne.texte("description"),
                ligne.montant("prix_htva"),
                ligne.tauxTva("taux_tva"),
                ligne.entier("stock"),
                ligne.entier("seuil_alerte"),
                true);

        var existante = pieces.findByReferenceFabricant(donnees.referenceFabricant());
        if (existante.isPresent()) {
            catalogue.modifierPiece(existante.get().getReference(), donnees);
            return false;
        }
        catalogue.creerPiece(donnees);
        return true;
    }

    @FunctionalInterface
    private interface TraitementLigne {
        boolean traiter(Ligne ligne);
    }

    /**
     * Une ligne de donnees, lue <b>par nom de colonne</b>.
     *
     * <p>Les conversions levent un message en francais destine au garage : il lira ce
     * rapport dans son navigateur, pas une trace technique.</p>
     */
    private record Ligne(List<String> entete, List<String> cellules) {

        String texte(String colonne) {
            int index = entete.indexOf(colonne);
            if (index < 0 || index >= cellules.size()) {
                return null;
            }
            String valeur = cellules.get(index);
            return valeur.isEmpty() ? null : valeur;
        }

        String texteObligatoire(String colonne) {
            String valeur = texte(colonne);
            if (valeur == null) {
                throw new IllegalArgumentException("Colonne « " + colonne + " » vide.");
            }
            return valeur;
        }

        /**
         * Montant accepte a la virgule comme au point : le fichier vient d un tableur
         * belge, ou la virgule est le separateur decimal. Refuser la virgule ferait
         * echouer tout export local reimporte tel quel.
         */
        BigDecimal montant(String colonne) {
            String brut = texteObligatoire(colonne).replace(',', '.').replace(" ", "");
            try {
                BigDecimal valeur = new BigDecimal(brut);
                if (valeur.signum() < 0) {
                    throw new IllegalArgumentException(
                            "Colonne « " + colonne + " » : un montant négatif n'est pas admis.");
                }
                return valeur;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Colonne « " + colonne + " » : « " + brut + " » n'est pas un montant.");
            }
        }

        /**
         * Taux de TVA controle contre les taux belges admis. Le domaine le reverifiera,
         * mais l erreur serait alors technique : ici elle nomme la colonne et la ligne.
         */
        BigDecimal tauxTva(String colonne) {
            BigDecimal taux = montant(colonne);
            if (!be.autoservplus.catalogue.domain.TauxTvaBelge.estAdmis(taux)) {
                throw new IllegalArgumentException(
                        "Colonne « " + colonne + " » : taux « " + taux.toPlainString()
                                + " » non admis en Belgique (0, 6, 12 ou 21).");
            }
            return taux;
        }

        int entier(String colonne) {
            String brut = texte(colonne);
            if (brut == null) {
                return 0;
            }
            try {
                int valeur = Integer.parseInt(brut);
                if (valeur < 0) {
                    throw new IllegalArgumentException(
                            "Colonne « " + colonne + " » : une valeur négative n'est pas admise.");
                }
                return valeur;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Colonne « " + colonne + " » : « " + brut + " » n'est pas un nombre entier.");
            }
        }
    }
}
