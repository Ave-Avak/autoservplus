package be.autoservplus.identite.service;

import be.autoservplus.common.exception.RefusTraduit;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.communication.service.ServiceCourriel;
import be.autoservplus.identite.domain.CodeSecours;
import be.autoservplus.identite.domain.TypeEvenementSecurite;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.mfa.Base32;
import be.autoservplus.identite.mfa.ChiffrementSecretMfa;
import be.autoservplus.identite.mfa.Totp;
import be.autoservplus.identite.repository.CodeSecoursRepository;
import be.autoservplus.identite.repository.UtilisateurRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Second facteur TOTP : enrôlement, vérification, codes de secours, désactivation.
 *
 * <p><b>Le secret ne sort de ce service qu'une fois</b>, à l'enrôlement, pour être
 * affiché au titulaire. Ensuite, aucune méthode ne le rend : la vérification se fait
 * ici, et le reste de l'application n'apprend que le verdict.</p>
 *
 * <p><b>Les échecs se comptent même quand le geste est refusé.</b> Les méthodes qui
 * lèvent un refus après avoir vérifié un code portent {@code noRollbackFor} : sans
 * cela, le refus annulerait la transaction et l'échec qu'il signale ne serait jamais
 * compte, essayer des codes depuis l'écran de désactivation deviendrait gratuit.</p>
 */
@Service
@Transactional(readOnly = true)
public class SecondFacteurService {

    private static final Logger JOURNAL = LoggerFactory.getLogger(SecondFacteurService.class);

    public static final int SEUIL_ECHECS = 5;
    public static final Duration VERROUILLAGE_INITIAL = Duration.ofMinutes(15);
    public static final Duration VERROUILLAGE_PLAFOND = Duration.ofHours(24);
    public static final int NOMBRE_CODES_SECOURS = 8;

    /** 160 bits : la taille recommandée par la RFC 4226 §4, celle d'une sortie HMAC-SHA1. */
    private static final int OCTETS_SECRET = 20;
    private static final String EMETTEUR = "AutoServ+";

    /**
     * Trente et un caractères, sans les confusions de lecture (0/O, 1/I/L) : un code de
     * secours se recopie souvent depuis une feuille imprimée. Huit tirages donnent
     * 31^8, soit près de 40 bits par code.
     */
    private static final String ALPHABET_CODES = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final int LONGUEUR_CODE = 8;
    private static final int COUT_BCRYPT_CODES = 10;

    private final UtilisateurRepository utilisateurs;
    private final CodeSecoursRepository codes;
    private final ChiffrementSecretMfa chiffrement;
    private final PasswordEncoder encodeurMotsDePasse;
    private final JournalSecuriteCompte journal;
    private final RevocationSessions revocation;
    private final ServiceCourriel courriel;
    private final Clock horloge;
    private final PasswordEncoder encodeurCodes = new BCryptPasswordEncoder(COUT_BCRYPT_CODES);
    private final SecureRandom aleatoire = new SecureRandom();

    public SecondFacteurService(UtilisateurRepository utilisateurs, CodeSecoursRepository codes,
                                ChiffrementSecretMfa chiffrement, PasswordEncoder encodeurMotsDePasse,
                                JournalSecuriteCompte journal, RevocationSessions revocation,
                                ServiceCourriel courriel, Clock horloge) {
        this.utilisateurs = utilisateurs;
        this.codes = codes;
        this.chiffrement = chiffrement;
        this.encodeurMotsDePasse = encodeurMotsDePasse;
        this.journal = journal;
        this.revocation = revocation;
        this.courriel = courriel;
        this.horloge = horloge;
    }

    /** Ce que l'écran d'enrôlement affiche : la clé en clair et l'URI du QR code. */
    public record Enrolement(String cleBase32, String uriOtpauth) {

        /** Par groupes de quatre : une clé de trente-deux caractères se recopie mal d'un bloc. */
        public String cleLisible() {
            return cleBase32.replaceAll("(.{4})(?!$)", "$1 ");
        }
    }

    /**
     * {@code INDISPONIBLE} : le secret ne se déchiffre plus (clé maître perdue ou
     * altérée). Ce n'est pas un échec du titulaire : rien n'est compté, et seule une
     * réinitialisation par un super-administrateur le résout.
     */
    public enum Resultat { ACCEPTE, REFUSE, VERROUILLE, INDISPONIBLE }

    public record Etat(boolean actif, Instant activeLe, boolean obligatoire, int codesRestants) {
    }

    public Etat etat(String email) {
        Utilisateur compte = charger(email);
        int restants = compte.secondFacteurActif() ? codes.utilisablesDe(compte.getId()).size() : 0;
        return new Etat(compte.secondFacteurActif(), compte.getMfaActiveLe(),
                compte.secondFacteurObligatoire(), restants);
    }

    /**
     * Commence l'enrôlement, ou reprend celui qui est en cours.
     *
     * <p><b>Reprendre plutôt que retirer un secret neuf</b> : un rafraîchissement de la page
     * après avoir scanné le QR code ne doit pas invalider ce que l'application vient
     * d'enregistrer.</p>
     */
    @Transactional
    public Enrolement preparerEnrolement(String email) {
        Utilisateur compte = charger(email);
        if (compte.secondFacteurActif()) {
            throw new RefusTraduit("mfa.erreur.deja-actif", "Le second facteur est deja actif.");
        }
        byte[] secret;
        if (compte.enrolementEnCours()) {
            secret = dechiffrer(compte);
        } else {
            secret = new byte[OCTETS_SECRET];
            aleatoire.nextBytes(secret);
            ChiffrementSecretMfa.SecretChiffre chiffre = chiffrement.chiffrer(secret, compte.getId());
            compte.commencerEnrolement(chiffre.donnees(), chiffre.version());
        }
        String cle = Base32.encoder(secret);
        return new Enrolement(cle, uriOtpauth(compte.getEmail(), cle));
    }

    /**
     * Confirme l'enrôlement par un premier code, émet les codes de secours et ferme les
     * autres sessions du compte.
     *
     * @return les codes de secours EN CLAIR : la seule fois où ils existent ainsi
     */
    @Transactional
    public List<String> confirmerEnrolement(String email, String code, String idSessionCourante) {
        Utilisateur compte = charger(email);
        if (!compte.enrolementEnCours()) {
            throw new RefusTraduit("mfa.erreur.aucun-enrolement", "Aucun enrolement en cours.");
        }
        Instant maintenant = horloge.instant();
        OptionalLong pas = Totp.verifier(dechiffrer(compte), code, maintenant, null);
        if (pas.isEmpty()) {
            throw new RefusTraduit("mfa.erreur.code", "Code incorrect.");
        }
        compte.confirmerEnrolement(maintenant, pas.getAsLong());
        journal.consigner(compte, TypeEvenementSecurite.MFA_ACTIVE, compte);
        List<String> clairs = emettreCodes(compte, maintenant);
        revocation.revoquerSauf(compte.getEmail(), idSessionCourante);
        JOURNAL.info("Second facteur active pour le compte {}", compte.getReference());
        return clairs;
    }

    /**
     * Vérifie le second facteur à la connexion : code TOTP, ou à défaut code de secours.
     *
     * <p>Ne lève rien : un échec doit être COMMITE pour être compté, et un verrouillage
     * écrit pour valoir à la connexion suivante.</p>
     */
    @Transactional
    public Resultat verifier(String email, String saisie) {
        return verifierSaisie(chargerPourVerification(email), saisie);
    }

    /**
     * Retire le second facteur d'un membre. Refusé au back-office, pour qui il est
     * obligatoire. Mot de passe ET code valide : une session volée seule ne suffit pas.
     */
    @Transactional(noRollbackFor = RefusTraduit.class)
    public void desactiver(String email, String motDePasse, String code, String idSessionCourante) {
        Utilisateur compte = chargerPourVerification(email);
        if (compte.secondFacteurObligatoire()) {
            throw new RefusTraduit("mfa.erreur.obligatoire",
                    "Le second facteur est obligatoire pour un compte du back-office.");
        }
        if (!compte.secondFacteurActif()) {
            throw new RefusTraduit("mfa.erreur.inactif", "Aucun second facteur actif.");
        }
        exigerMotDePasse(compte, motDePasse);
        exigerCodeValide(compte, code);

        compte.retirerSecondFacteur();
        codes.invaliderUtilisables(compte.getId(), horloge.instant());
        journal.consigner(compte, TypeEvenementSecurite.MFA_DESACTIVE, compte);
        revocation.revoquerSauf(compte.getEmail(), idSessionCourante);
        JOURNAL.info("Second facteur desactive par le titulaire du compte {}", compte.getReference());
    }

    /** Remplace les codes de secours, après réauthentification par mot de passe et code. */
    @Transactional(noRollbackFor = RefusTraduit.class)
    public List<String> regenererCodes(String email, String motDePasse, String code) {
        Utilisateur compte = chargerPourVerification(email);
        if (!compte.secondFacteurActif()) {
            throw new RefusTraduit("mfa.erreur.inactif", "Aucun second facteur actif.");
        }
        exigerMotDePasse(compte, motDePasse);
        exigerCodeValide(compte, code);

        List<String> clairs = emettreCodes(compte, horloge.instant());
        journal.consigner(compte, TypeEvenementSecurite.CODES_SECOURS_REGENERES, compte);
        return clairs;
    }

    /**
     * Retire le second facteur sur décision d'un tiers : réinitialisation par le
     * super-administrateur. Ni journal ni notification ici : c'est l'appelant qui sait
     * qui agit et à qui l'annoncer.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void retirer(Utilisateur compte) {
        compte.retirerSecondFacteur();
        codes.invaliderUtilisables(compte.getId(), horloge.instant());
    }

    // --- vérification -----------------------------------------------------------------

    private Resultat verifierSaisie(Utilisateur compte, String saisie) {
        Instant maintenant = horloge.instant();
        // Verrouille : on ne regarde même pas le code. Le vérifier quand même laisserait
        // un attaquant continuer à tester pendant le verrouillage, et apprendre lequel
        // était juste à la première connexion qui suit.
        if (compte.estVerrouille(maintenant)) {
            return Resultat.VERROUILLE;
        }
        if (compte.secondFacteurActif()) {
            try {
                if (accepterTotp(compte, saisie, maintenant)
                        || consommerCodeSecours(compte, saisie, maintenant)) {
                    return Resultat.ACCEPTE;
                }
            } catch (ChiffrementSecretMfa.SecretIndechiffrableException indechiffrable) {
                // Ni échec compté ni code de secours essayé : le titulaire n'y est pour
                // rien, et des essais non comptés ouvriraient une recherche illimitée des
                // codes de secours.
                JOURNAL.error("Secret TOTP indechiffrable pour le compte {} (version de cle {}) : {}",
                        compte.getReference(), compte.getMfaCleVersion(), indechiffrable.getMessage());
                return Resultat.INDISPONIBLE;
            }
        }
        journal.consigner(compte, TypeEvenementSecurite.SECOND_FACTEUR_ECHEC, null);
        Optional<Instant> verrouillage = compte.enregistrerEchecSecondFacteur(
                SEUIL_ECHECS, VERROUILLAGE_INITIAL, VERROUILLAGE_PLAFOND, maintenant);
        if (verrouillage.isEmpty()) {
            return Resultat.REFUSE;
        }
        journal.consigner(compte, TypeEvenementSecurite.SECOND_FACTEUR_VERROUILLAGE, null);
        JOURNAL.warn("Compte {} verrouille jusqu au {} apres {} echecs du second facteur.",
                compte.getReference(), verrouillage.get(), SEUIL_ECHECS);
        notifierVerrouillage(compte, verrouillage.get());
        return Resultat.VERROUILLE;
    }

    private boolean accepterTotp(Utilisateur compte, String saisie, Instant maintenant) {
        byte[] secret = dechiffrer(compte);
        OptionalLong pas = Totp.verifier(secret, saisie, maintenant, compte.getMfaDernierPas());
        if (pas.isEmpty()) {
            return false;
        }
        // L'UPDATE conditionnel tranche entre deux vérifications simultanées du même code.
        // Le compte en mémoire est ensuite aligné, pour ne pas réécrire un pas périmé.
        if (utilisateurs.accepterPasSiNouveau(compte.getId(), pas.getAsLong()) == 0) {
            return false;
        }
        compte.accepterPas(pas.getAsLong());
        // Rotation de clé résorbée à l'usage : chaque connexion réussie remet le secret
        // sous la clé active, sans procédure de migration à part.
        if (compte.getMfaCleVersion() != chiffrement.versionActive()) {
            ChiffrementSecretMfa.SecretChiffre neuf = chiffrement.chiffrer(secret, compte.getId());
            compte.rechiffrerSecret(neuf.donnees(), neuf.version());
        }
        return true;
    }

    private boolean consommerCodeSecours(Utilisateur compte, String saisie, Instant maintenant) {
        String code = normaliserCode(saisie);
        if (code.length() != LONGUEUR_CODE
                || !code.chars().allMatch(c -> ALPHABET_CODES.indexOf(c) >= 0)) {
            return false;
        }
        for (CodeSecours candidat : codes.utilisablesDe(compte.getId())) {
            if (encodeurCodes.matches(code, candidat.getEmpreinte())) {
                if (codes.consommerSiUtilisable(candidat.getId(), maintenant) == 0) {
                    return false;
                }
                candidat.consommer(maintenant);
                journal.consigner(compte, TypeEvenementSecurite.CODE_SECOURS_CONSOMME, compte);
                return true;
            }
        }
        return false;
    }

    private void exigerMotDePasse(Utilisateur compte, String motDePasse) {
        if (motDePasse == null || motDePasse.isEmpty()
                || !encodeurMotsDePasse.matches(motDePasse, compte.getMotDePasseHache())) {
            throw new RefusTraduit("mfa.erreur.mot-de-passe", "Mot de passe incorrect.");
        }
    }

    private void exigerCodeValide(Utilisateur compte, String code) {
        Resultat resultat = verifierSaisie(compte, code);
        if (resultat == Resultat.VERROUILLE) {
            throw new RefusTraduit("mfa.erreur.verrouille",
                    "Trop de codes incorrects : le compte est temporairement verrouille.");
        }
        if (resultat == Resultat.REFUSE) {
            throw new RefusTraduit("mfa.erreur.code", "Code incorrect.");
        }
        if (resultat == Resultat.INDISPONIBLE) {
            throw new RefusTraduit("mfa.erreur.indisponible",
                    "Verification du second facteur impossible pour ce compte.");
        }
    }

    // --- codes de secours -------------------------------------------------------------

    private List<String> emettreCodes(Utilisateur compte, Instant maintenant) {
        codes.invaliderUtilisables(compte.getId(), maintenant);
        List<String> clairs = new ArrayList<>(NOMBRE_CODES_SECOURS);
        for (int i = 0; i < NOMBRE_CODES_SECOURS; i++) {
            String code = tirerCode();
            codes.save(new CodeSecours(compte, encodeurCodes.encode(code)));
            clairs.add(code.substring(0, 4) + "-" + code.substring(4));
        }
        return clairs;
    }

    private String tirerCode() {
        StringBuilder code = new StringBuilder(LONGUEUR_CODE);
        for (int i = 0; i < LONGUEUR_CODE; i++) {
            code.append(ALPHABET_CODES.charAt(aleatoire.nextInt(ALPHABET_CODES.length())));
        }
        return code.toString();
    }

    /** Tiret, espaces et casse indifférents : le code se recopie à la main. */
    static String normaliserCode(String saisie) {
        return saisie == null ? "" : saisie.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }

    // --- outils -----------------------------------------------------------------------

    private byte[] dechiffrer(Utilisateur compte) {
        return chiffrement.dechiffrer(compte.getMfaSecretChiffre(), compte.getMfaCleVersion(),
                compte.getId());
    }

    /**
     * Format de Google Authenticator, reconnu par toutes les applications : l'émetteur
     * figure dans le libellé ET en paramètre, pour les applications qui ne lisent que
     * l'un des deux.
     */
    private static String uriOtpauth(String email, String cleBase32) {
        String emetteur = URLEncoder.encode(EMETTEUR, StandardCharsets.UTF_8);
        return "otpauth://totp/" + emetteur + ":" + URLEncoder.encode(email, StandardCharsets.UTF_8)
                + "?secret=" + cleBase32 + "&issuer=" + emetteur
                + "&algorithm=SHA1&digits=" + Totp.CHIFFRES + "&period=" + Totp.PERIODE_SECONDES;
    }

    /**
     * Le verrouillage est déjà écrit : une panne du fournisseur de courriel ne doit pas
     * l'annuler. Même arbitrage que l'avis de rendez-vous d {@code AdminRdvService}.
     */
    private void notifierVerrouillage(Utilisateur compte, Instant jusqua) {
        try {
            courriel.envoyerAvisVerrouillageSecondFacteur(compte, jusqua);
        } catch (RuntimeException echec) {
            JOURNAL.warn("Avis de verrouillage non envoye pour le compte {} : {}",
                    compte.getReference(), echec.getMessage());
        }
    }

    private Utilisateur chargerPourVerification(String email) {
        return utilisateurs.findForUpdateByEmail(email)
                .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur", email));
    }

    private Utilisateur charger(String email) {
        return utilisateurs.findByEmail(email)
                .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur", email));
    }
}
