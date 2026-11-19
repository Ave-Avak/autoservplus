package be.autoservplus.identite.service;

import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Objects;

/**
 * Encodeur des mots de passe qui normalise en NFKC avant le hachage et la vérification.
 *
 * <p>Sans normalisation, un « é » précomposé et un « e » suivi de l'accent combinant,
 * identiques à l'écran, sont deux mots de passe : selon le clavier ou le système, le
 * titulaire ne se reconnecte plus (NIST SP 800-63B-4, §3.1.1.2). NFKC plutôt que NFC :
 * il ramène aussi les formes de compatibilité (ligature « ﬁ », chiffres et lettres pleine
 * chasse) à leur forme ordinaire, que l'utilisateur croit avoir tapée.</p>
 *
 * <p>Porté par le bean lui-même, il s'applique à toute vérification et à tout hachage du
 * projet, connexion, réauthentification et changement compris, sans qu'un appelant puisse
 * l'oublier.</p>
 *
 * <p><b>Migration à la connexion.</b> Les empreintes écrites avant la normalisation sont
 * celles de la forme brute. La vérification essaie d'abord la forme normalisée, puis la
 * brute si elle en diffère. Une connexion réussie par la forme brute fait réécrire
 * l'empreinte de la forme normalisée ({@link #exigeMigration}). Le second essai ne coûte
 * qu'à un mot de passe saisi hors de sa forme normalisée, compte existant ou non : il ne
 * dit rien de l'existence d'un compte.</p>
 *
 * <p>NFKC peut allonger une saisie (« ½ », deux octets, devient « 1⁄2 », cinq octets), et
 * {@code BCryptPasswordEncoder} refuse de hacher plus de 72 octets depuis le correctif de
 * CVE-2025-22228. Une empreinte brute dont la forme normalisée dépasse cette limite ne
 * migre donc pas : la réécrire ferait échouer la connexion, et la tronquer hacherait un
 * autre mot de passe que celui que vérifie la politique. Elle reste valable pour la
 * saisie brute.</p>
 */
public class EncodeurNormalise implements PasswordEncoder {

    private final PasswordEncoder hachage;

    public EncodeurNormalise(PasswordEncoder hachage) {
        this.hachage = Objects.requireNonNull(hachage, "hachage");
    }

    /** Forme NFKC d'un mot de passe, celle que le projet hache et dont il mesure la taille. */
    public static String normaliser(CharSequence motDePasse) {
        return Normalizer.normalize(motDePasse, Normalizer.Form.NFKC);
    }

    @Override
    public String encode(CharSequence motDePasse) {
        return hachage.encode(normaliser(motDePasse));
    }

    @Override
    public boolean matches(CharSequence motDePasse, String empreinte) {
        String normalise = normaliser(motDePasse);
        if (hachage.matches(normalise, empreinte)) {
            return true;
        }
        return !normalise.contentEquals(motDePasse) && hachage.matches(motDePasse, empreinte);
    }

    /**
     * Vrai si l'empreinte est celle de la forme brute d'un mot de passe dont la forme
     * normalisée diffère et tient dans les 72 octets que BCrypt accepte de hacher : à
     * réécrire après une vérification réussie.
     */
    public boolean exigeMigration(CharSequence motDePasse, String empreinte) {
        String normalise = normaliser(motDePasse);
        return !normalise.contentEquals(motDePasse)
                && normalise.getBytes(StandardCharsets.UTF_8).length <= PolitiqueMotDePasse.MAXIMUM_OCTETS
                && !hachage.matches(normalise, empreinte);
    }

    @Override
    public boolean upgradeEncoding(String empreinte) {
        return hachage.upgradeEncoding(empreinte);
    }
}
