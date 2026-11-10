package be.autoservplus.common.validation;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Numéro d'entreprise belge (Banque-Carrefour des Entreprises) et numéro de TVA qui en
 * dérive.
 *
 * <p>Le numéro compte dix chiffres. Le premier vaut 0 ou 1, et les deux derniers sont
 * une clé de contrôle : 97 moins le reste de la division des huit premiers par 97. Les
 * points et les espaces de la présentation usuelle (0123.456.789) sont ignorés. Le
 * numéro de TVA d'une entreprise belge est « BE » suivi de ces dix chiffres : il ne
 * porte aucune information propre, et un écart entre les deux numéros est donc toujours
 * une erreur de saisie.</p>
 */
public final class NumeroEntrepriseBelge {

    private static final Pattern DIX_CHIFFRES = Pattern.compile("[01]\\d{9}");

    private NumeroEntrepriseBelge() {
    }

    /** Retire points et espaces. Rend {@code null} pour {@code null}. */
    public static String normaliser(String numero) {
        return numero == null ? null : numero.replaceAll("[.\\s]", "");
    }

    /** Dix chiffres, premier chiffre 0 ou 1, clé modulo 97 juste. */
    public static boolean estValide(String numero) {
        String chiffres = normaliser(numero);
        if (chiffres == null || !DIX_CHIFFRES.matcher(chiffres).matches()) {
            return false;
        }
        long base = Long.parseLong(chiffres.substring(0, 8));
        int cle = Integer.parseInt(chiffres.substring(8));
        return cle == 97 - base % 97;
    }

    /**
     * Le numéro de TVA est-il « BE » suivi des dix chiffres du numéro d'entreprise ?
     * Points et espaces sont ignorés des deux côtés, et la casse du préfixe aussi
     * (« be » vaut « BE »). Ne juge pas la validité du numéro d'entreprise lui-même, que
     * {@link #estValide(String)} contrôle à part.
     */
    public static boolean tvaCorrespond(String numeroTva, String numeroEntreprise) {
        String tva = numeroTva == null ? null : normaliser(numeroTva).toUpperCase(Locale.ROOT);
        String bce = normaliser(numeroEntreprise);
        return tva != null && bce != null && bce.matches("\\d{10}") && tva.equals("BE" + bce);
    }
}
