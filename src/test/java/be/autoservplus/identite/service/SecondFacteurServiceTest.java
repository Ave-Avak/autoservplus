package be.autoservplus.identite.service;

import be.autoservplus.common.exception.RefusTraduit;
import be.autoservplus.communication.service.ServiceCourriel;
import be.autoservplus.identite.domain.CodeSecours;
import be.autoservplus.identite.domain.TypeEvenementSecurite;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.mfa.Base32;
import be.autoservplus.identite.mfa.ChiffrementSecretMfa;
import be.autoservplus.identite.mfa.Totp;
import be.autoservplus.identite.repository.CodeSecoursRepository;
import be.autoservplus.identite.repository.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Service du second facteur")
class SecondFacteurServiceTest {

    private static final Instant MAINTENANT = Instant.parse("2026-10-08T08:00:00Z");
    private static final String EMAIL = "titulaire@exemple.be";
    private static final String MOT_DE_PASSE = "phrase de passe du titulaire";

    @Mock private UtilisateurRepository utilisateurs;
    @Mock private CodeSecoursRepository codes;
    @Mock private JournalSecuriteCompte journal;
    @Mock private RevocationSessions revocation;
    @Mock private ServiceCourriel courriel;

    private final PasswordEncoder encodeur = new BCryptPasswordEncoder(4);
    private final SecretKey cle1 = new SecretKeySpec(new byte[32], "AES");
    private final List<CodeSecours> codesEnregistres = new ArrayList<>();

    private Utilisateur titulaire;

    private SecondFacteurService service(ChiffrementSecretMfa chiffrement) {
        return new SecondFacteurService(utilisateurs, codes, chiffrement, encodeur, journal,
                revocation, courriel, Clock.fixed(MAINTENANT, ZoneOffset.UTC));
    }

    private SecondFacteurService service;

    @BeforeEach
    void setUp() {
        titulaire = new Utilisateur(EMAIL, encodeur.encode(MOT_DE_PASSE), "Nom", "Prenom",
                TypeUtilisateur.MEMBRE);
        ReflectionTestUtils.setField(titulaire, "id", 42L);
        lenient().when(utilisateurs.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(titulaire));
        lenient().when(codes.save(any(CodeSecours.class))).thenAnswer(invocation -> {
            codesEnregistres.add(invocation.getArgument(0));
            return invocation.getArgument(0);
        });
        lenient().when(codes.utilisablesDe(42L)).thenAnswer(invocation ->
                codesEnregistres.stream().filter(CodeSecours::estUtilisable).toList());
        service = service(new ChiffrementSecretMfa(Map.of((short) 1, cle1), (short) 1));
    }

    /** Enrole le titulaire et rend le secret, comme l application d authentification le lirait. */
    private byte[] enroler(SecondFacteurService avec) {
        String cle = avec.preparerEnrolement(EMAIL).cleBase32();
        byte[] secret = Base32.decoder(cle);
        // Le pas precedent : celui de MAINTENANT reste ainsi disponible pour la connexion.
        avec.confirmerEnrolement(EMAIL, Totp.code(secret, Totp.pasDe(MAINTENANT) - 1), "session-1");
        return secret;
    }

    private String codeCourant(byte[] secret) {
        return Totp.code(secret, Totp.pasDe(MAINTENANT));
    }

    @Test
    @DisplayName("l'enrôlement propose 160 bits, et un rafraîchissement reprend le même secret")
    void enrolementRepris() {
        SecondFacteurService.Enrolement premier = service.preparerEnrolement(EMAIL);
        SecondFacteurService.Enrolement second = service.preparerEnrolement(EMAIL);

        assertThat(Base32.decoder(premier.cleBase32())).hasSize(20);
        assertThat(second.cleBase32()).isEqualTo(premier.cleBase32());
        assertThat(premier.uriOtpauth()).startsWith("otpauth://totp/AutoServ%2B:")
                .contains("secret=" + premier.cleBase32(), "issuer=AutoServ%2B", "digits=6", "period=30");
        assertThat(titulaire.secondFacteurActif()).isFalse();
    }

    @Test
    @DisplayName("la confirmation active, émet huit codes et ferme les autres sessions")
    void confirmation() {
        String cle = service.preparerEnrolement(EMAIL).cleBase32();

        List<String> clairs = service.confirmerEnrolement(EMAIL,
                Totp.code(Base32.decoder(cle), Totp.pasDe(MAINTENANT)), "session-1");

        assertThat(titulaire.secondFacteurActif()).isTrue();
        assertThat(clairs).hasSize(8).allMatch(c -> c.matches("[2-9A-HJKMNP-Z]{4}-[2-9A-HJKMNP-Z]{4}"));
        assertThat(codesEnregistres).hasSize(8)
                .allMatch(c -> c.getEmpreinte().startsWith("$2a$10$"));
        verify(journal).consigner(titulaire, TypeEvenementSecurite.MFA_ACTIVE, titulaire);
        verify(revocation).revoquerSauf(EMAIL, "session-1");
    }

    @Test
    @DisplayName("un code faux à l'enrôlement est refusé et n'active rien")
    void confirmationRefusee() {
        service.preparerEnrolement(EMAIL);

        assertThatThrownBy(() -> service.confirmerEnrolement(EMAIL, "000000", "s"))
                .isInstanceOf(RefusTraduit.class);
        assertThat(titulaire.secondFacteurActif()).isFalse();
    }

    @Test
    @DisplayName("un code TOTP est accepté une fois, puis refusé au rejeu")
    void rejeu() {
        byte[] secret = enroler(service);

        assertThat(service.verifier(EMAIL, codeCourant(secret))).isEqualTo(SecondFacteurService.Resultat.ACCEPTE);
        assertThat(service.verifier(EMAIL, codeCourant(secret))).isEqualTo(SecondFacteurService.Resultat.REFUSE);
    }

    @Test
    @DisplayName("un code de secours sert une seule fois, et sa consommation est journalisée")
    void codeDeSecoursUnique() {
        String cle = service.preparerEnrolement(EMAIL).cleBase32();
        List<String> clairs = service.confirmerEnrolement(EMAIL,
                Totp.code(Base32.decoder(cle), Totp.pasDe(MAINTENANT)), "s");
        String code = clairs.get(3).toLowerCase().replace("-", " ");

        assertThat(service.verifier(EMAIL, code)).isEqualTo(SecondFacteurService.Resultat.ACCEPTE);
        assertThat(service.verifier(EMAIL, code)).isEqualTo(SecondFacteurService.Resultat.REFUSE);
        assertThat(codesEnregistres.stream().filter(c -> c.getConsommeLe() != null)).hasSize(1);
        verify(journal).consigner(titulaire, TypeEvenementSecurite.CODE_SECOURS_CONSOMME, titulaire);
    }

    @Test
    @DisplayName("cinq échecs verrouillent, notifient le titulaire, et le bon code ne passe plus")
    void verrouillage() {
        byte[] secret = enroler(service);
        for (int i = 0; i < 4; i++) {
            assertThat(service.verifier(EMAIL, "000000")).isEqualTo(SecondFacteurService.Resultat.REFUSE);
        }

        assertThat(service.verifier(EMAIL, "000000")).isEqualTo(SecondFacteurService.Resultat.VERROUILLE);

        verify(courriel).envoyerAvisVerrouillageSecondFacteur(titulaire,
                MAINTENANT.plus(SecondFacteurService.VERROUILLAGE_INITIAL));
        verify(journal).consigner(titulaire, TypeEvenementSecurite.SECOND_FACTEUR_VERROUILLAGE, null);
        assertThat(service.verifier(EMAIL, codeCourant(secret)))
                .isEqualTo(SecondFacteurService.Resultat.VERROUILLE);
        assertThat(titulaire.getMfaDernierPas()).isEqualTo(Totp.pasDe(MAINTENANT) - 1);
    }

    @Test
    @DisplayName("une panne d'envoi n'annule pas le verrouillage")
    void verrouillageMalgrePanneDeCourriel() {
        enroler(service);
        org.mockito.Mockito.doThrow(new IllegalStateException("fournisseur absent"))
                .when(courriel).envoyerAvisVerrouillageSecondFacteur(eq(titulaire), any());
        for (int i = 0; i < 4; i++) {
            service.verifier(EMAIL, "000000");
        }

        assertThat(service.verifier(EMAIL, "000000")).isEqualTo(SecondFacteurService.Resultat.VERROUILLE);
        assertThat(titulaire.estVerrouille(MAINTENANT)).isTrue();
    }

    @Test
    @DisplayName("un compte du back-office ne retire pas son second facteur")
    void desactivationRefuseeAuBackOffice() {
        Utilisateur admin = new Utilisateur("admin@exemple.be", encodeur.encode(MOT_DE_PASSE),
                "Nom", "Prenom", TypeUtilisateur.ADMINISTRATEUR);
        when(utilisateurs.findByEmailIgnoreCase("admin@exemple.be")).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.desactiver("admin@exemple.be", MOT_DE_PASSE, "123456", "s"))
                .isInstanceOf(RefusTraduit.class)
                .extracting(e -> ((RefusTraduit) e).getCleMessage()).isEqualTo("mfa.erreur.obligatoire");
    }

    @Test
    @DisplayName("la désactivation exige le mot de passe ET un code, et compte l'échec du code")
    void desactivation() {
        byte[] secret = enroler(service);
        clearInvocations(codes);

        assertThatThrownBy(() -> service.desactiver(EMAIL, "faux", codeCourant(secret), "s"))
                .isInstanceOf(RefusTraduit.class);
        assertThatThrownBy(() -> service.desactiver(EMAIL, MOT_DE_PASSE, "000000", "s"))
                .isInstanceOf(RefusTraduit.class);
        assertThat(titulaire.getMfaEchecs()).isEqualTo((short) 1);
        assertThat(titulaire.secondFacteurActif()).isTrue();

        service.desactiver(EMAIL, MOT_DE_PASSE, codeCourant(secret), "session-2");

        assertThat(titulaire.secondFacteurActif()).isFalse();
        verify(codes).invaliderUtilisables(eq(42L), any());
        verify(journal).consigner(titulaire, TypeEvenementSecurite.MFA_DESACTIVE, titulaire);
        verify(revocation).revoquerSauf(EMAIL, "session-2");
    }

    @Test
    @DisplayName("la régénération remplace les codes après mot de passe et code")
    void regeneration() {
        byte[] secret = enroler(service);

        List<String> nouveaux = service.regenererCodes(EMAIL, MOT_DE_PASSE, codeCourant(secret));

        assertThat(nouveaux).hasSize(8);
        verify(journal).consigner(titulaire, TypeEvenementSecurite.CODES_SECOURS_REGENERES, titulaire);
    }

    @Test
    @DisplayName("un secret chiffré sous une ancienne clé est remis sous la clé active au succès")
    void rechiffrementALUsage() {
        byte[] secret = enroler(service);
        SecretKey cle2 = new SecretKeySpec(new byte[]{9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9,
                9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9}, "AES");
        SecondFacteurService apresRotation = service(
                new ChiffrementSecretMfa(Map.of((short) 1, cle1, (short) 2, cle2), (short) 2));

        assertThat(apresRotation.verifier(EMAIL, codeCourant(secret)))
                .isEqualTo(SecondFacteurService.Resultat.ACCEPTE);
        assertThat(titulaire.getMfaCleVersion()).isEqualTo((short) 2);
    }

    @Test
    @DisplayName("la reinitialisation par un tiers retire le secret sans journaliser à sa place")
    void retraitParUnTiers() {
        enroler(service);
        clearInvocations(codes);

        service.retirer(titulaire);

        assertThat(titulaire.secondFacteurActif()).isFalse();
        verify(codes).invaliderUtilisables(eq(42L), any());
        verify(journal, never()).consigner(eq(titulaire), eq(TypeEvenementSecurite.MFA_REINITIALISE), isNull());
    }
}
