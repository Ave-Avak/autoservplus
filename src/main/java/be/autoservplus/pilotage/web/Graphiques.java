package be.autoservplus.pilotage.web;

import be.autoservplus.pilotage.service.dto.JourChiffre;
import be.autoservplus.pilotage.service.dto.MontantCategorie;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Géométrie des deux graphiques du tableau de bord (A25), rendus en SVG par le gabarit.
 *
 * <p><b>Aucune bibliothèque, aucun script.</b> La politique de sécurité du contenu
 * n'autorise que les scripts du site et interdit les styles en ligne : une bibliothèque
 * de graphiques demanderait l'un ou l'autre, ou un domaine tiers. Le serveur calcule
 * donc les coordonnées et le gabarit écrit les balises, colorées par la feuille de style.
 * Le graphique existe sans JavaScript, et chaque figure est doublée d'un tableau des
 * mêmes valeurs.</p>
 *
 * <p>Ici, rien que des nombres : les libellés et le format des montants restent au
 * gabarit, qui connaît la langue de l'écran.</p>
 */
public final class Graphiques {

    /** Repère de la courbe, en unités du {@code viewBox}. */
    static final int LARGEUR = 640;
    static final int HAUTEUR = 240;
    private static final int MARGE_GAUCHE = 64;
    private static final int MARGE_DROITE = 16;
    private static final int MARGE_HAUTE = 12;
    private static final int MARGE_BASSE = 28;

    /** Une étiquette de date tous les sept jours : trente dates se chevaucheraient. */
    private static final int PAS_DES_DATES = 7;

    /** Barres horizontales : le libellé d'une catégorie tient sur sa ligne, quelle que soit sa longueur. */
    private static final int HAUTEUR_D_UNE_BARRE = 44;
    private static final int EPAISSEUR = 14;

    private Graphiques() {
    }

    /**
     * Courbe d'évolution.
     *
     * @param points    sommets de la ligne, au format de l'attribut {@code points}
     * @param ordonnees graduations de l'axe vertical, de zéro au sommet de l'échelle
     * @param abscisses dates écrites sous l'axe horizontal
     * @param vide      aucune valeur positive : le gabarit le dit au lieu de tracer un trait plat
     */
    public record Courbe(int largeur, int hauteur, String points,
                         List<Graduation> ordonnees, List<Repere> abscisses, boolean vide) {

        public int gauche() {
            return MARGE_GAUCHE;
        }

        public int droite() {
            return LARGEUR - MARGE_DROITE;
        }

        public int bas() {
            return HAUTEUR - MARGE_BASSE;
        }
    }

    public record Graduation(double y, BigDecimal valeur) {
    }

    public record Repere(double x, LocalDate jour) {
    }

    /** Histogramme. */
    public record Barres(int largeur, int hauteur, List<Barre> barres) {

        public boolean vide() {
            return barres.isEmpty();
        }
    }

    /**
     * @param yLibelle ligne de base du libellé, au-dessus de la barre
     * @param yBarre   bord supérieur de la barre
     */
    public record Barre(String libelle, BigDecimal valeur, double yLibelle, double yBarre, double largeur) {
    }

    public static Courbe courbe(List<JourChiffre> serie) {
        BigDecimal sommet = echelle(serie.stream().map(JourChiffre::htva)
                .reduce(BigDecimal.ZERO, BigDecimal::max));
        boolean vide = serie.stream().noneMatch(jour -> jour.htva().signum() > 0);

        double largeurUtile = LARGEUR - MARGE_GAUCHE - MARGE_DROITE;
        double hauteurUtile = HAUTEUR - MARGE_HAUTE - MARGE_BASSE;
        double pas = serie.size() > 1 ? largeurUtile / (serie.size() - 1) : 0;

        StringBuilder points = new StringBuilder();
        List<Repere> abscisses = new ArrayList<>();
        for (int i = 0; i < serie.size(); i++) {
            double x = MARGE_GAUCHE + i * pas;
            double part = serie.get(i).htva().max(BigDecimal.ZERO)
                    .divide(sommet, 6, RoundingMode.HALF_UP).doubleValue();
            double y = MARGE_HAUTE + hauteurUtile * (1 - part);
            if (i > 0) {
                points.append(' ');
            }
            points.append(nombre(x)).append(',').append(nombre(y));
            // Compté depuis la fin : la dernière étiquette est toujours aujourd'hui.
            if ((serie.size() - 1 - i) % PAS_DES_DATES == 0) {
                abscisses.add(new Repere(arrondi(x), serie.get(i).jour()));
            }
        }

        BigDecimal moitie = sommet.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        List<Graduation> ordonnees = List.of(
                new Graduation(MARGE_HAUTE + hauteurUtile, BigDecimal.ZERO),
                new Graduation(MARGE_HAUTE + hauteurUtile / 2, moitie),
                new Graduation(MARGE_HAUTE, sommet));
        return new Courbe(LARGEUR, HAUTEUR, points.toString(), ordonnees, abscisses, vide);
    }

    public static Barres barres(List<MontantCategorie> categories) {
        BigDecimal maximum = categories.stream().map(MontantCategorie::htva)
                .reduce(BigDecimal.ZERO, BigDecimal::max);
        List<Barre> barres = new ArrayList<>();
        for (int i = 0; i < categories.size(); i++) {
            MontantCategorie categorie = categories.get(i);
            double part = maximum.signum() > 0
                    ? categorie.htva().max(BigDecimal.ZERO).divide(maximum, 6, RoundingMode.HALF_UP).doubleValue()
                    : 0;
            double haut = i * HAUTEUR_D_UNE_BARRE;
            barres.add(new Barre(categorie.categorie(), categorie.htva(),
                    haut + 16, haut + 22, arrondi(LARGEUR * part)));
        }
        return new Barres(LARGEUR, Math.max(categories.size(), 1) * HAUTEUR_D_UNE_BARRE, barres);
    }

    public static int epaisseur() {
        return EPAISSEUR;
    }

    /**
     * Sommet de l'échelle : le premier nombre rond au-dessus du maximum, pris parmi
     * 1, 2 et 5 fois une puissance de dix. L'axe se lit ainsi « 0, 250, 500 » plutôt
     * que « 0, 231,57, 463,14 ». Sans valeur positive, l'échelle vaut 1 pour éviter une
     * division par zéro.
     */
    static BigDecimal echelle(BigDecimal maximum) {
        if (maximum.signum() <= 0) {
            return BigDecimal.ONE;
        }
        BigDecimal puissance = BigDecimal.ONE.movePointRight(maximum.precision() - maximum.scale() - 1);
        for (int facteur : new int[]{1, 2, 5, 10}) {
            BigDecimal candidat = puissance.multiply(BigDecimal.valueOf(facteur));
            if (candidat.compareTo(maximum) >= 0) {
                return candidat;
            }
        }
        return puissance.multiply(BigDecimal.TEN);
    }

    /** Point décimal quelle que soit la langue de la machine : une virgule casserait l'attribut. */
    private static String nombre(double valeur) {
        return String.format(Locale.ROOT, "%.1f", valeur);
    }

    private static double arrondi(double valeur) {
        return Math.round(valeur * 10) / 10.0;
    }
}
