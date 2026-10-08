package be.autoservplus.identite.service;

import be.autoservplus.common.exception.RefusTraduit;
import be.autoservplus.communication.service.ServiceCourriel;
import be.autoservplus.identite.domain.StatutUtilisateur;
import be.autoservplus.identite.domain.TypeEvenementSecurite;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Comptes du back-office : création, lien, réinitialisation")
class ComptesAdministrateursServiceTest {

    private static final Instant MAINTENANT = Instant.parse("2026-10-08T08:00:00Z");

    @Mock private UtilisateurRepository utilisateurs;
    @Mock private AuteurCourant auteurCourant;
    @Mock private JournalSecuriteCompte journal;
    @Mock private SecondFacteurService secondFacteur;
    @Mock private RevocationSessions revocation;
    @Mock private ServiceCourriel courriel;

    private ComptesAdministrateursService service;
    private Utilisateur chef;

    @BeforeEach
    void setUp() {
        service = new ComptesAdministrateursService(utilisateurs, new BCryptPasswordEncoder(4),
                auteurCourant, journal, secondFacteur, revocation, courriel,
                Clock.fixed(MAINTENANT, ZoneOffset.UTC));
        chef = new Utilisateur("chef@exemple.be", "x", "Garage", "Chef",
                TypeUtilisateur.SUPER_ADMINISTRATEUR);
        ReflectionTestUtils.setField(chef, "id", 1L);
        when(auteurCourant.resoudre()).thenReturn(chef);
    }

    @Test
    @DisplayName("l'administrateur naît sans mot de passe utilisable, et seul le lien porte le jeton")
    void creation() {
        when(utilisateurs.saveAndFlush(any(Utilisateur.class))).thenAnswer(i -> i.getArgument(0));

        Utilisateur cree = service.creerAdministrateur(" Nouvel@Exemple.be ", "Ana", "Lopez", "Atelier");

        ArgumentCaptor<String> lien = ArgumentCaptor.forClass(String.class);
        verify(courriel).envoyerLienDefinitionMotDePasse(eq(cree), lien.capture());
        String jeton = lien.getValue().substring("/compte/definition?jeton=".length());

        assertThat(cree.getEmail()).isEqualTo("nouvel@exemple.be");
        assertThat(cree.getTypeUtilisateur()).isEqualTo(TypeUtilisateur.ADMINISTRATEUR);
        assertThat(cree.getStatut()).isEqualTo(StatutUtilisateur.EN_ATTENTE_VALIDATION);
        assertThat(cree.doitChangerMotDePasse()).isTrue();
        assertThat(cree.getMotDePasseHache()).startsWith("$2a$");
        assertThat(jeton).hasSize(64);
        assertThat(cree.getJetonDefinitionHache()).isEqualTo(JetonDefinition.empreinte(jeton))
                .isNotEqualTo(jeton);
        assertThat(cree.getJetonDefinitionExpiration()).isEqualTo(MAINTENANT.plus(
                PremierMotDePasseService.VALIDITE_LIEN));
        verify(journal).consigner(cree, TypeEvenementSecurite.ADMINISTRATEUR_CREE, chef);
    }

    @Test
    @DisplayName("une adresse déjà prise est refusée")
    void adressePrise() {
        when(utilisateurs.existsByEmailIgnoreCase("pris@exemple.be")).thenReturn(true);

        assertThatThrownBy(() -> service.creerAdministrateur("pris@exemple.be", "A", "B", null))
                .isInstanceOf(RefusTraduit.class);
        verify(courriel, never()).envoyerLienDefinitionMotDePasse(any(), any());
    }

    @Test
    @DisplayName("renvoyer le lien remplace le jeton : l'ancien cesse de valoir")
    void renvoi() {
        Utilisateur admin = new Utilisateur("a@exemple.be", "x", "N", "P", TypeUtilisateur.ADMINISTRATEUR);
        admin.preparerDefinitionMotDePasse("ancienne-empreinte", MAINTENANT);
        when(utilisateurs.findByReference(admin.getReference())).thenReturn(Optional.of(admin));

        service.renvoyerLienDefinition(admin.getReference());

        assertThat(admin.getJetonDefinitionHache()).isNotEqualTo("ancienne-empreinte");
        verify(journal).consigner(admin, TypeEvenementSecurite.LIEN_DEFINITION_ENVOYE, chef);
    }

    @Test
    @DisplayName("on ne réinitialise pas son propre second facteur depuis le back-office")
    void reinitialisationDeSoiRefusee() {
        when(utilisateurs.findByReference(chef.getReference())).thenReturn(Optional.of(chef));

        assertThatThrownBy(() -> service.reinitialiserSecondFacteur(chef.getReference()))
                .isInstanceOf(RefusTraduit.class);
        verify(secondFacteur, never()).retirer(any());
    }

    @Test
    @DisplayName("la réinitialisation retire, journalise, coupe toutes les sessions et avertit")
    void reinitialisation() {
        Utilisateur admin = new Utilisateur("a@exemple.be", "x", "N", "P", TypeUtilisateur.ADMINISTRATEUR);
        ReflectionTestUtils.setField(admin, "id", 2L);
        admin.commencerEnrolement(new byte[]{1}, (short) 1);
        admin.confirmerEnrolement(MAINTENANT, 1L);
        when(utilisateurs.findByReference(admin.getReference())).thenReturn(Optional.of(admin));

        service.reinitialiserSecondFacteur(admin.getReference());

        verify(secondFacteur).retirer(admin);
        verify(journal).consigner(admin, TypeEvenementSecurite.MFA_REINITIALISE, chef);
        verify(revocation).revoquer("a@exemple.be");
        verify(courriel).envoyerAvisReinitialisationSecondFacteur(admin);
    }
}
