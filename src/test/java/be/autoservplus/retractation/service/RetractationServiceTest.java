package be.autoservplus.retractation.service;

import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.retractation.domain.DemandeAnnulation;
import be.autoservplus.retractation.domain.StatutDemandeAnnulation;
import be.autoservplus.retractation.repository.DemandeAnnulationRepository;
import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.domain.MotifAnnulationCommande;
import be.autoservplus.vente.repository.CommandeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Controle d eligibilite de la retractation (F30, RM-23) : ce que le systeme sait
 * trancher seul.
 *
 * <p>La fenetre de quatorze jours est exercee a <b>horloge gelee</b> : c est tout
 * l interet de l horloge injectee. Avec {@code Instant.now()}, ces tests seraient
 * soit indeterministes, soit obliges d attendre deux semaines.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RetractationService")
class RetractationServiceTest {

    /** Vendredi 4 septembre 2026, 10 h. Toutes les commandes sont datees par rapport a lui. */
    private static final Instant MAINTENANT = Instant.parse("2026-09-04T10:00:00Z");
    private static final ZoneId BRUXELLES = ZoneId.of("Europe/Brussels");

    @Mock private CommandeRepository commandes;
    @Mock private DemandeAnnulationRepository demandes;

    private RetractationService service;

    private final Utilisateur marie = new Utilisateur(
            "marie@exemple.be", "$2a$12$h", "Dupont", "Marie", TypeUtilisateur.MEMBRE);

    @BeforeEach
    void setUp() {
        service = new RetractationService(commandes, demandes,
                Clock.fixed(MAINTENANT, BRUXELLES));
    }

    /** Commande payee, passee il y a {@code joursEcoules} jours. */
    private Commande commandePayeeIlYA(long joursEcoules) {
        Commande commande = new Commande("CMD-2026-0001", marie,
                new BigDecimal("39.98"), new BigDecimal("8.40"), new BigDecimal("48.38"),
                MAINTENANT.minus(Duration.ofDays(joursEcoules)));
        commande.confirmerPaiement(MAINTENANT.minus(Duration.ofDays(joursEcoules)));
        return commande;
    }

    private void commandeExiste(Commande commande) {
        when(commandes.findByReference(commande.getReference())).thenReturn(Optional.of(commande));
    }

    @Nested
    @DisplayName("fenetre legale de 14 jours")
    class FenetreLegale {

        @Test
        @DisplayName("J-13 : la demande passe")
        void treizeJours() {
            Commande commande = commandePayeeIlYA(13);
            commandeExiste(commande);
            when(demandes.existsByCommandeAndStatut(commande, StatutDemandeAnnulation.EN_ATTENTE))
                    .thenReturn(false);
            when(demandes.save(any(DemandeAnnulation.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            DemandeAnnulation demande = service.demander(
                    "marie@exemple.be", commande.getReference(), "trop cher");

            assertThat(demande.getStatut()).isEqualTo(StatutDemandeAnnulation.EN_ATTENTE);
            assertThat(demande.getMotifMembre()).isEqualTo("trop cher");
            assertThat(demande.getDateDemande()).isEqualTo(MAINTENANT);
        }

        @Test
        @DisplayName("J-15 : le delai est expire, rien n'est ecrit")
        void quinzeJours() {
            Commande commande = commandePayeeIlYA(15);
            commandeExiste(commande);

            assertThatThrownBy(() -> service.demander(
                    "marie@exemple.be", commande.getReference(), null))
                    .isInstanceOf(RetractationImpossibleException.class)
                    .extracting(e -> ((RetractationImpossibleException) e).getMotif())
                    .isEqualTo(MotifRefusRetractation.DELAI_EXPIRE);
            verify(demandes, never()).save(any());
        }

        @Test
        @DisplayName("J-14 pile : la fenetre est fermee, la borne est exclusive")
        void quatorzeJoursPile() {
            // 14 x 24 h exactement : le delai est ecoule. Legerement plus strict que
            // le calcul calendaire legal, et l ecart joue contre le garage, jamais
            // contre le consommateur — seul sens dans lequel l approximation tient.
            Commande commande = commandePayeeIlYA(14);
            commandeExiste(commande);

            assertThatThrownBy(() -> service.demander(
                    "marie@exemple.be", commande.getReference(), null))
                    .isInstanceOf(RetractationImpossibleException.class);
        }

        @Test
        @DisplayName("le delai court depuis la conclusion de la commande, pas depuis le paiement")
        void departDuDelai() {
            // La V1 ne suit pas la livraison : le depart est la conclusion, donc la
            // fenetre du membre est plus courte que la fenetre legale, jamais plus
            // longue. Ici la commande date de J-13 mais n a ete payee qu hier : c est
            // bien J-13 qui compte.
            Commande commande = new Commande("CMD-2026-0001", marie,
                    new BigDecimal("39.98"), new BigDecimal("8.40"), new BigDecimal("48.38"),
                    MAINTENANT.minus(Duration.ofDays(13)));
            commande.confirmerPaiement(MAINTENANT.minus(Duration.ofDays(1)));
            commandeExiste(commande);
            when(demandes.existsByCommandeAndStatut(commande, StatutDemandeAnnulation.EN_ATTENTE))
                    .thenReturn(false);
            when(demandes.save(any(DemandeAnnulation.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            assertThat(service.demander("marie@exemple.be", commande.getReference(), null))
                    .isNotNull();
        }
    }

    @Nested
    @DisplayName("autres gardes")
    class AutresGardes {

        @Test
        @DisplayName("une commande non payee n'a rien a rembourser")
        void commandeNonPayee() {
            Commande commande = new Commande("CMD-2026-0002", marie,
                    new BigDecimal("10.00"), new BigDecimal("2.10"), new BigDecimal("12.10"),
                    MAINTENANT.minus(Duration.ofDays(1)));
            commandeExiste(commande);

            assertThatThrownBy(() -> service.demander(
                    "marie@exemple.be", commande.getReference(), null))
                    .isInstanceOf(RetractationImpossibleException.class)
                    .extracting(e -> ((RetractationImpossibleException) e).getMotif())
                    .isEqualTo(MotifRefusRetractation.COMMANDE_NON_PAYEE);
        }

        @Test
        @DisplayName("une demande deja en attente bloque la suivante (idempotence)")
        void demandeDejaEnCours() {
            Commande commande = commandePayeeIlYA(2);
            commandeExiste(commande);
            when(demandes.existsByCommandeAndStatut(commande, StatutDemandeAnnulation.EN_ATTENTE))
                    .thenReturn(true);

            assertThatThrownBy(() -> service.demander(
                    "marie@exemple.be", commande.getReference(), null))
                    .isInstanceOf(RetractationImpossibleException.class)
                    .extracting(e -> ((RetractationImpossibleException) e).getMotif())
                    .isEqualTo(MotifRefusRetractation.DEMANDE_DEJA_EN_COURS);
            verify(demandes, never()).save(any());
        }

        @Test
        @DisplayName("une commande deja cloturee ne se retracte plus")
        void commandeCloturee() {
            Commande remboursee = commandePayeeIlYA(2);
            remboursee.rembourser(MAINTENANT.minus(Duration.ofDays(1)));
            commandeExiste(remboursee);

            assertThatThrownBy(() -> service.demander(
                    "marie@exemple.be", remboursee.getReference(), null))
                    .isInstanceOf(RetractationImpossibleException.class)
                    .extracting(e -> ((RetractationImpossibleException) e).getMotif())
                    .isEqualTo(MotifRefusRetractation.COMMANDE_CLOTUREE);
        }

        @Test
        @DisplayName("une commande annulee faute de paiement repond « cloturee », pas « non payee »")
        void commandeAnnulee() {
            // L ordre des gardes n est pas neutre : l etat terminal prime sur le
            // statut de paiement, sinon une annulation RM-21 se lirait comme un
            // paiement en attente.
            Commande annulee = new Commande("CMD-2026-0003", marie,
                    new BigDecimal("10.00"), new BigDecimal("2.10"), new BigDecimal("12.10"),
                    MAINTENANT.minus(Duration.ofDays(1)));
            annulee.annuler(MotifAnnulationCommande.TIMEOUT_PAIEMENT, MAINTENANT);
            commandeExiste(annulee);

            assertThatThrownBy(() -> service.demander(
                    "marie@exemple.be", annulee.getReference(), null))
                    .isInstanceOf(RetractationImpossibleException.class)
                    .extracting(e -> ((RetractationImpossibleException) e).getMotif())
                    .isEqualTo(MotifRefusRetractation.COMMANDE_CLOTUREE);
        }
    }

    @Nested
    @DisplayName("appartenance")
    class Appartenance {

        @Test
        @DisplayName("la commande d'autrui remonte comme introuvable : 404, jamais 403")
        void commandeDAutrui() {
            Commande commande = commandePayeeIlYA(1);
            commandeExiste(commande);

            // Un 403 confirmerait a un tiers que cette commande existe.
            assertThatThrownBy(() -> service.demander(
                    "jean@exemple.be", commande.getReference(), null))
                    .isInstanceOf(RessourceIntrouvableException.class);
            verify(demandes, never()).save(any());
        }

        @Test
        @DisplayName("une reference inconnue remonte comme introuvable")
        void referenceInconnue() {
            UUID inconnue = UUID.randomUUID();
            when(commandes.findByReference(inconnue)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.demander("marie@exemple.be", inconnue, null))
                    .isInstanceOf(RessourceIntrouvableException.class);
        }
    }

    @Nested
    @DisplayName("etats de l'ecran membre")
    class EtatsDuMembre {

        @Test
        @DisplayName("une commande eligible est demandable et sans demande")
        void commandeEligible() {
            Commande commande = commandePayeeIlYA(3);
            when(commandes.historiqueDuMembre("marie@exemple.be")).thenReturn(List.of(commande));
            when(demandes.demandesDuMembre("marie@exemple.be")).thenReturn(List.of());
            when(demandes.existsByCommandeAndStatut(commande, StatutDemandeAnnulation.EN_ATTENTE))
                    .thenReturn(false);

            var etats = service.etatsDuMembre("marie@exemple.be");

            assertThat(etats).containsOnlyKeys(commande.getReference());
            var vue = etats.get(commande.getReference());
            assertThat(vue.demandable()).isTrue();
            assertThat(vue.statutDemande()).isNull();
            assertThat(vue.aUnAvoir()).isFalse();
        }

        @Test
        @DisplayName("une demande pendante rend la commande non demandable et signale l'attente")
        void demandePendante() {
            Commande commande = commandePayeeIlYA(3);
            DemandeAnnulation demande = new DemandeAnnulation(commande, null, MAINTENANT);
            when(commandes.historiqueDuMembre("marie@exemple.be")).thenReturn(List.of(commande));
            when(demandes.demandesDuMembre("marie@exemple.be")).thenReturn(List.of(demande));
            lenient().when(demandes.existsByCommandeAndStatut(
                    commande, StatutDemandeAnnulation.EN_ATTENTE)).thenReturn(true);

            var vue = service.etatsDuMembre("marie@exemple.be").get(commande.getReference());

            assertThat(vue.estEnAttente()).isTrue();
            assertThat(vue.demandable()).isFalse();
        }
    }

    @Nested
    @DisplayName("etat d'une seule commande (F32)")
    class EtatDeLaCommande {

        /**
         * Le detail d une commande et la liste doivent proposer la retractation aux
         * memes conditions : un bouton visible d un cote et absent de l autre, pour la
         * meme commande, serait incomprehensible pour le membre. Les deux chemins
         * partagent {@code refusEventuel} et la construction de la vue ; ce test le
         * verifie sur le resultat plutot que sur la structure du code, de sorte qu il
         * tombe si quelqu un reecrit l un des deux calculs.
         */
        @Test
        @DisplayName("rend exactement la meme vue que la liste, commande eligible")
        void concordeAvecLaListe() {
            Commande commande = commandePayeeIlYA(3);
            commandeExiste(commande);
            when(commandes.historiqueDuMembre("marie@exemple.be")).thenReturn(List.of(commande));
            when(demandes.demandesDuMembre("marie@exemple.be")).thenReturn(List.of());
            when(demandes.historiqueDe(commande)).thenReturn(List.of());
            when(demandes.existsByCommandeAndStatut(commande, StatutDemandeAnnulation.EN_ATTENTE))
                    .thenReturn(false);

            var depuisLaListe = service.etatsDuMembre("marie@exemple.be")
                    .get(commande.getReference());
            var depuisLeDetail = service.etatDeLaCommande("marie@exemple.be",
                    commande.getReference());

            assertThat(depuisLeDetail).isEqualTo(depuisLaListe);
            assertThat(depuisLeDetail.demandable()).isTrue();
        }

        @Test
        @DisplayName("rend la meme vue que la liste quand une demande est pendante")
        void concordeAvecLaListeAvecDemande() {
            Commande commande = commandePayeeIlYA(3);
            DemandeAnnulation demande = new DemandeAnnulation(commande, null, MAINTENANT);
            commandeExiste(commande);
            when(commandes.historiqueDuMembre("marie@exemple.be")).thenReturn(List.of(commande));
            when(demandes.demandesDuMembre("marie@exemple.be")).thenReturn(List.of(demande));
            when(demandes.historiqueDe(commande)).thenReturn(List.of(demande));
            when(demandes.existsByCommandeAndStatut(commande, StatutDemandeAnnulation.EN_ATTENTE))
                    .thenReturn(true);

            var depuisLaListe = service.etatsDuMembre("marie@exemple.be")
                    .get(commande.getReference());
            var depuisLeDetail = service.etatDeLaCommande("marie@exemple.be",
                    commande.getReference());

            assertThat(depuisLeDetail).isEqualTo(depuisLaListe);
            assertThat(depuisLeDetail.estEnAttente()).isTrue();
        }

        /** Meme garde que partout : la commande d autrui est introuvable, pas interdite. */
        @Test
        @DisplayName("la commande d'un autre membre remonte comme introuvable")
        void commandeDAutrui() {
            Commande commande = commandePayeeIlYA(3);
            commandeExiste(commande);

            assertThatThrownBy(() -> service.etatDeLaCommande("jean@exemple.be",
                    commande.getReference()))
                    .isInstanceOf(RessourceIntrouvableException.class);
        }
    }
}
