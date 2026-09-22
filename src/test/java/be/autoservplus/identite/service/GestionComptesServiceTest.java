package be.autoservplus.identite.service;

import be.autoservplus.common.exception.RegleMetierException;
import be.autoservplus.identite.domain.HistoriqueStatutUtilisateur;
import be.autoservplus.identite.domain.StatutUtilisateur;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.HistoriqueStatutUtilisateurRepository;
import be.autoservplus.identite.repository.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Les quatre gardes de la gestion des comptes (CdC 5.2.3).
 *
 * <p>Chaque garde est verifiee <b>par son effet</b> — statut inchange, journal non
 * ecrit — et non par le seul fait qu une exception parte : un refus qui laisserait
 * une ligne de journal derriere lui serait pire qu un refus silencieux, puisqu il
 * documenterait un evenement qui n a pas eu lieu.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GestionComptesService — gardes")
class GestionComptesServiceTest {

    private static final Instant MAINTENANT = Instant.parse("2026-09-22T09:00:00Z");
    private static final String MOTIF = "Impayes repetes.";

    @Mock private UtilisateurRepository utilisateurs;
    @Mock private HistoriqueStatutUtilisateurRepository historique;
    @Mock private AuteurCourant auteurCourant;
    @Mock private RevocationSessions revocation;

    private GestionComptesService service;
    private Utilisateur membre;
    private Utilisateur admin;
    private Utilisateur chef;

    @BeforeEach
    void setUp() {
        service = new GestionComptesService(utilisateurs, historique, auteurCourant,
                revocation, Clock.fixed(MAINTENANT, ZoneOffset.UTC));
        membre = compte(1L, "marie@exemple.be", TypeUtilisateur.MEMBRE);
        admin = compte(2L, "admin@exemple.be", TypeUtilisateur.ADMINISTRATEUR);
        chef = compte(3L, "chef@exemple.be", TypeUtilisateur.SUPER_ADMINISTRATEUR);
    }

    /** L identifiant et la reference sont poses par la base ; le test les impose. */
    private Utilisateur compte(long id, String email, TypeUtilisateur type) {
        Utilisateur u = new Utilisateur(email, "$2a$12$empreinte", "Nom", "Prenom", type);
        u.confirmerAdresseEmail();
        ecrire(u, "id", id);
        lenient().when(utilisateurs.findByReference(u.getReference())).thenReturn(Optional.of(u));
        return u;
    }

    private static void ecrire(Object cible, String champ, Object valeur) {
        try {
            Field f = cible.getClass().getDeclaredField(champ);
            f.setAccessible(true);
            f.set(cible, valeur);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private void agit(Utilisateur auteur) {
        when(auteurCourant.resoudre()).thenReturn(auteur);
    }

    private UUID ref(Utilisateur u) {
        return u.getReference();
    }

    @Nested
    @DisplayName("suspension nominale")
    class Nominale {

        @Test
        @DisplayName("l'administrateur suspend un membre et le journal porte l'auteur et le motif")
        void suspensionJournalisee() {
            agit(admin);

            service.suspendre(ref(membre), MOTIF);

            assertThat(membre.getStatut()).isEqualTo(StatutUtilisateur.SUSPENDU);

            ArgumentCaptor<HistoriqueStatutUtilisateur> ligne =
                    ArgumentCaptor.forClass(HistoriqueStatutUtilisateur.class);
            verify(historique).save(ligne.capture());
            assertThat(ligne.getValue().getStatutAvant()).isEqualTo(StatutUtilisateur.ACTIF);
            assertThat(ligne.getValue().getStatutApres()).isEqualTo(StatutUtilisateur.SUSPENDU);
            assertThat(ligne.getValue().getAuteur()).isSameAs(admin);
            assertThat(ligne.getValue().getMotif()).isEqualTo(MOTIF);
            assertThat(ligne.getValue().getHorodatage()).isEqualTo(MAINTENANT);
        }

        /**
         * La revocation accompagne la SUSPENSION et elle seule : la declencher a la
         * reactivation deconnecterait le membre au moment precis ou on lui rend
         * l acces.
         */
        @Test
        @DisplayName("la suspension ferme les sessions ouvertes, la réactivation non")
        void revocationSurSuspensionSeulement() {
            agit(admin);

            service.suspendre(ref(membre), MOTIF);
            verify(revocation).revoquer(membre.getEmail());

            service.reactiver(ref(membre));
            verify(revocation, org.mockito.Mockito.times(1)).revoquer(membre.getEmail());
        }

        @Test
        @DisplayName("la réactivation repasse à ACTIF, sans motif")
        void reactivation() {
            membre.setStatut(StatutUtilisateur.SUSPENDU);
            agit(admin);

            service.reactiver(ref(membre));

            assertThat(membre.getStatut()).isEqualTo(StatutUtilisateur.ACTIF);
            ArgumentCaptor<HistoriqueStatutUtilisateur> ligne =
                    ArgumentCaptor.forClass(HistoriqueStatutUtilisateur.class);
            verify(historique).save(ligne.capture());
            assertThat(ligne.getValue().getMotif()).isNull();
        }
    }

    @Nested
    @DisplayName("garde 1 — personne ne se suspend soi-même")
    class PasSoiMeme {

        @Test
        @DisplayName("un administrateur ne se suspend pas")
        void administrateurRefuse() {
            agit(chef);

            assertThatThrownBy(() -> service.suspendre(ref(chef), MOTIF))
                    .isInstanceOf(RegleMetierException.class)
                    .hasMessageContaining("propre compte");

            assertThat(chef.getStatut()).isEqualTo(StatutUtilisateur.ACTIF);
            verify(historique, never()).save(any());
        }
    }

    @Nested
    @DisplayName("garde 2 — il doit rester un super-administrateur actif")
    class DernierSuperAdmin {

        /**
         * Le decompte EXCLUT la cible : il rend ce qu il resterait APRES le geste. Un
         * decompte qui l inclurait vaudrait un de trop et laisserait suspendre le
         * dernier.
         */
        @Test
        @DisplayName("suspendre le dernier super-administrateur actif est refusé")
        void dernierRefuse() {
            agit(chef);
            Utilisateur autreChef = compte(4L, "chef2@exemple.be",
                    TypeUtilisateur.SUPER_ADMINISTRATEUR);
            when(utilisateurs.compterSuperAdministrateursActifsSauf(autreChef.getId()))
                    .thenReturn(0L);

            assertThatThrownBy(() -> service.suspendre(ref(autreChef), MOTIF))
                    .isInstanceOf(RegleMetierException.class)
                    .hasMessageContaining("dernier super-administrateur");

            assertThat(autreChef.getStatut()).isEqualTo(StatutUtilisateur.ACTIF);
            verify(historique, never()).save(any());
        }

        @Test
        @DisplayName("s'il en reste un autre, la suspension passe")
        void avantDernierPermis() {
            agit(chef);
            Utilisateur autreChef = compte(4L, "chef2@exemple.be",
                    TypeUtilisateur.SUPER_ADMINISTRATEUR);
            when(utilisateurs.compterSuperAdministrateursActifsSauf(autreChef.getId()))
                    .thenReturn(1L);

            service.suspendre(ref(autreChef), MOTIF);

            assertThat(autreChef.getStatut()).isEqualTo(StatutUtilisateur.SUSPENDU);
        }

        /**
         * La garde ne porte QUE sur les super-administrateurs : sans ce cas, une garde
         * trop large interdirait de suspendre un administrateur ordinaire et personne
         * ne s en apercevrait avant la production.
         */
        @Test
        @DisplayName("elle ne s'applique pas à un administrateur ordinaire")
        void administrateurOrdinaireNonConcerne() {
            agit(chef);

            service.suspendre(ref(admin), MOTIF);

            assertThat(admin.getStatut()).isEqualTo(StatutUtilisateur.SUSPENDU);
            verify(utilisateurs, never()).compterSuperAdministrateursActifsSauf(anyLong());
        }
    }

    @Nested
    @DisplayName("garde 3 — pouvoirs exclusifs sur les comptes du back-office")
    class PouvoirsExclusifs {

        @Test
        @DisplayName("un administrateur ne suspend pas un autre administrateur")
        void administrateurSurAdministrateur() {
            agit(admin);
            Utilisateur autreAdmin = compte(5L, "admin2@exemple.be",
                    TypeUtilisateur.ADMINISTRATEUR);

            assertThatThrownBy(() -> service.suspendre(ref(autreAdmin), MOTIF))
                    .isInstanceOf(RegleMetierException.class)
                    .hasMessageContaining("super-administrateur");

            assertThat(autreAdmin.getStatut()).isEqualTo(StatutUtilisateur.ACTIF);
            verify(historique, never()).save(any());
        }

        @Test
        @DisplayName("un administrateur ne suspend pas davantage un super-administrateur")
        void administrateurSurSuperAdministrateur() {
            agit(admin);

            assertThatThrownBy(() -> service.suspendre(ref(chef), MOTIF))
                    .isInstanceOf(RegleMetierException.class)
                    .hasMessageContaining("super-administrateur");
        }

        @Test
        @DisplayName("mais il suspend un membre sans difficulté")
        void administrateurSurMembre() {
            agit(admin);

            assertThatCode(() -> service.suspendre(ref(membre), MOTIF))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("la réactivation obéit à la même exclusivité")
        void reactivationMemeExclusivite() {
            agit(admin);
            Utilisateur autreAdmin = compte(5L, "admin2@exemple.be",
                    TypeUtilisateur.ADMINISTRATEUR);
            autreAdmin.setStatut(StatutUtilisateur.SUSPENDU);

            assertThatThrownBy(() -> service.reactiver(ref(autreAdmin)))
                    .isInstanceOf(RegleMetierException.class)
                    .hasMessageContaining("super-administrateur");

            assertThat(autreAdmin.getStatut()).isEqualTo(StatutUtilisateur.SUSPENDU);
        }
    }

    @Nested
    @DisplayName("garde 4 — motif obligatoire à la suspension")
    class MotifObligatoire {

        @Test
        @DisplayName("un motif vide ou fait d'espaces est refusé")
        void motifVideRefuse() {
            agit(admin);

            assertThatThrownBy(() -> service.suspendre(ref(membre), "   "))
                    .isInstanceOf(RegleMetierException.class)
                    .hasMessageContaining("motif");

            assertThat(membre.getStatut()).isEqualTo(StatutUtilisateur.ACTIF);
            verify(historique, never()).save(any());
        }
    }

    @Nested
    @DisplayName("transitions impossibles")
    class Transitions {

        @Test
        @DisplayName("un compte déjà suspendu ne se resuspend pas")
        void dejaSuspendu() {
            membre.setStatut(StatutUtilisateur.SUSPENDU);
            agit(admin);

            assertThatThrownBy(() -> service.suspendre(ref(membre), MOTIF))
                    .isInstanceOf(RegleMetierException.class)
                    .hasMessageContaining("deja suspendu");
        }

        /**
         * Un compte supprime au sens F23 est anonymise : le reactiver rendrait
         * joignable une ligne dont l adresse est un jeton non routable.
         */
        @Test
        @DisplayName("un compte supprimé ne se suspend ni ne se réactive")
        void supprimeIntouchable() {
            membre.setStatut(StatutUtilisateur.SUPPRIME);
            agit(admin);

            assertThatThrownBy(() -> service.suspendre(ref(membre), MOTIF))
                    .isInstanceOf(RegleMetierException.class)
                    .hasMessageContaining("supprime");
            assertThatThrownBy(() -> service.reactiver(ref(membre)))
                    .isInstanceOf(RegleMetierException.class)
                    .hasMessageContaining("suspendu");
        }
    }
}
