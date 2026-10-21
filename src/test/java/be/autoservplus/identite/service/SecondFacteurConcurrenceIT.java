package be.autoservplus.identite.service;

import be.autoservplus.identite.domain.CodeSecours;
import be.autoservplus.identite.domain.StatutUtilisateur;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.mfa.ChiffrementSecretMfa;
import be.autoservplus.identite.mfa.Totp;
import be.autoservplus.identite.repository.CodeSecoursRepository;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.identite.service.SecondFacteurService.Resultat;
import be.autoservplus.support.SocleIntegration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deux verifications SIMULTANEES du meme code n en acceptent qu une.
 *
 * <p>Seul un test d integration le montre : la garde tient a la base (verrou de ligne
 * sur le compte, UPDATE conditionnels), que les bouchons du test unitaire remplacent.
 * Chaque fil appelle le service dans sa propre transaction.</p>
 */
@SpringBootTest
@DisplayName("Second facteur : vérifications simultanées (intégration)")
class SecondFacteurConcurrenceIT extends SocleIntegration {

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);
    private static final byte[] SECRET = "secret de vingt octs".getBytes();
    private static final String CODE_SECOURS = "ABCD-2345";

    @Autowired private SecondFacteurService service;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private CodeSecoursRepository codes;
    @Autowired private PasswordEncoder encodeur;
    @Autowired private ChiffrementSecretMfa chiffrement;
    @Autowired private Clock horloge;

    private Utilisateur enrole() {
        Utilisateur u = new Utilisateur("concurrence-" + COMPTEUR.getAndIncrement() + "@exemple.be",
                encodeur.encode("phrase de passe du test de concurrence"), "Nom", "Prenom",
                TypeUtilisateur.MEMBRE);
        u.confirmerAdresseEmail();
        u.setStatut(StatutUtilisateur.ACTIF);
        u = utilisateurs.saveAndFlush(u);
        ChiffrementSecretMfa.SecretChiffre chiffre = chiffrement.chiffrer(SECRET, u.getId());
        u.commencerEnrolement(chiffre.donnees(), chiffre.version());
        u.confirmerEnrolement(horloge.instant(), Totp.pasDe(horloge.instant()) - 10);
        return utilisateurs.saveAndFlush(u);
    }

    /** Un code refuse sur toute la fenetre de tolerance, pas seulement au pas courant. */
    private String codeFaux() {
        for (int candidat = 0; ; candidat++) {
            String code = "%06d".formatted(candidat);
            if (Totp.verifier(SECRET, code, horloge.instant(), null).isEmpty()) {
                return code;
            }
        }
    }

    /** Lance les deux verifications au meme signal et rend leurs verdicts. */
    private List<Resultat> deuxFoisEnMemeTemps(String email, String code) throws Exception {
        ExecutorService fils = Executors.newFixedThreadPool(2);
        CountDownLatch depart = new CountDownLatch(1);
        try {
            List<Future<Resultat>> verdicts = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                verdicts.add(fils.submit(() -> {
                    depart.await();
                    return service.verifier(email, code);
                }));
            }
            depart.countDown();
            List<Resultat> resultats = new ArrayList<>();
            for (Future<Resultat> verdict : verdicts) {
                resultats.add(verdict.get(30, TimeUnit.SECONDS));
            }
            return resultats;
        } finally {
            fils.shutdownNow();
        }
    }

    @Test
    @DisplayName("le même code TOTP soumis deux fois en même temps n'est accepté qu'une fois")
    void totpSimultane() throws Exception {
        Utilisateur membre = enrole();
        long pas = Totp.pasDe(horloge.instant());

        List<Resultat> resultats = deuxFoisEnMemeTemps(membre.getEmail(), Totp.code(SECRET, pas));

        assertThat(resultats).containsExactlyInAnyOrder(Resultat.ACCEPTE, Resultat.REFUSE);
        // La transaction perdante n a pas reecrit un pas perime en validant.
        assertThat(utilisateurs.findById(membre.getId()).orElseThrow().getMfaDernierPas())
                .isGreaterThanOrEqualTo(pas);
    }

    @Test
    @DisplayName("le même code de secours soumis deux fois en même temps n'est consommé qu'une fois")
    void codeDeSecoursSimultane() throws Exception {
        Utilisateur membre = enrole();
        codes.saveAndFlush(new CodeSecours(membre,
                new BCryptPasswordEncoder(10).encode(CODE_SECOURS.replace("-", ""))));

        List<Resultat> resultats = deuxFoisEnMemeTemps(membre.getEmail(), CODE_SECOURS);

        assertThat(resultats).containsExactlyInAnyOrder(Resultat.ACCEPTE, Resultat.REFUSE);
        assertThat(codes.utilisablesDe(membre.getId())).isEmpty();
    }

    @Test
    @DisplayName("deux codes faux soumis en même temps comptent deux échecs, pas un")
    void deuxEchecsSimultanes() throws Exception {
        Utilisateur membre = enrole();

        List<Resultat> resultats = deuxFoisEnMemeTemps(membre.getEmail(), codeFaux());

        assertThat(resultats).containsExactly(Resultat.REFUSE, Resultat.REFUSE);
        // Sans le verrou de ligne, les deux transactions liraient 0 et ecriraient 1.
        assertThat(utilisateurs.findById(membre.getId()).orElseThrow().getMfaEchecs())
                .isEqualTo((short) 2);
    }
}
