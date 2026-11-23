package be.autoservplus.retractation.service;

import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.reservation.repository.ParametreAtelierRepository;
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
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

    @Mock private ParametreAtelierRepository parametres;
    @Mock private CommandeRepository commandes;
    @Mock private DemandeAnnulationRepository demandes;
    @Mock private be.autoservplus.intervention.repository.InterventionRepository interventionsRepo;
    @Mock private be.autoservplus.communication.service.ServiceCourriel courriel;

    private RetractationService service;

    private final Utilisateur marie = new Utilisateur(
            "marie@exemple.be", "$2a$12$h", "Dupont", "Marie", TypeUtilisateur.MEMBRE);

    @BeforeEach
    void setUp() {
        service = new RetractationService(commandes, demandes, interventionsRepo, courriel,
                parametres, Clock.fixed(MAINTENANT, BRUXELLES));
    }

    @Nested
    @DisplayName("fonction de rétractation en ligne (VI.61/2)")
    class FonctionEnLigne {

        @Test
        @DisplayName("la déclaration montre le nom, la commande et l'adresse de l'accusé")
        void declaration() {
            Commande commande = commandePayeeIlYA(3);
            commandeExiste(commande);
            when(demandes.existsByCommandeAndStatut(commande, StatutDemandeAnnulation.EN_ATTENTE))
                    .thenReturn(false);

            var declaration = service.declaration("marie@exemple.be", commande.getReference());

            assertThat(declaration.nomMembre()).isEqualTo("Marie Dupont");
            assertThat(declaration.numeroCommande()).isEqualTo("CMD-2026-0001");
            assertThat(declaration.adresseConfirmation()).isEqualTo("marie@exemple.be");
            assertThat(declaration.totalTvac()).contains("48,38");
        }

        @Test
        @DisplayName("la déclaration est refusée d'emblée par les mêmes gardes que la demande")
        void declarationRefusee() {
            Commande commande = commandePayeeIlYA(15);
            commandeExiste(commande);
            commandeDePrestations(commande);

            assertThatThrownBy(() -> service.declaration("marie@exemple.be", commande.getReference()))
                    .isInstanceOf(RetractationImpossibleException.class);
        }

        @Test
        @DisplayName("la confirmation fait partir l'accusé avec le contenu et l'instant de la soumission")
        void accuseALaSoumission() {
            Commande commande = commandePayeeIlYA(3);
            commandeExiste(commande);
            when(demandes.existsByCommandeAndStatut(commande, StatutDemandeAnnulation.EN_ATTENTE))
                    .thenReturn(false);
            when(demandes.save(any(DemandeAnnulation.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            service.demander("marie@exemple.be", commande.getReference(), "trop cher");

            var accuse = org.mockito.ArgumentCaptor.forClass(
                    be.autoservplus.communication.service.DetailsAccuseRetractationCourriel.class);
            verify(courriel).envoyerAccuseRetractation(accuse.capture());
            assertThat(accuse.getValue().adresseEmail()).isEqualTo("marie@exemple.be");
            assertThat(accuse.getValue().nomMembre()).isEqualTo("Marie Dupont");
            assertThat(accuse.getValue().numeroCommande()).isEqualTo("CMD-2026-0001");
            assertThat(accuse.getValue().motif()).isEqualTo("trop cher");
            assertThat(accuse.getValue().soumiseLe()).isEqualTo(MAINTENANT);
        }

        @Test
        @DisplayName("une demande refusée par les gardes n'envoie aucun accusé")
        void aucunAccuseSurRefus() {
            Commande commande = commandePayeeIlYA(15);
            commandeExiste(commande);
            commandeDePrestations(commande);

            assertThatThrownBy(() -> service.demander("marie@exemple.be", commande.getReference(), null))
                    .isInstanceOf(RetractationImpossibleException.class);
            verifyNoInteractions(courriel);
        }
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

    /** La commande porte une prestation : son délai se calcule depuis la conclusion. */
    private void commandeDePrestations(Commande commande) {
        when(commandes.lignesServiceDe(commande)).thenReturn(List.of(
                org.mockito.Mockito.mock(be.autoservplus.vente.domain.LignePanier.class)));
        when(parametres.courants()).thenReturn(new be.autoservplus.reservation.domain.ParametreAtelier());
    }

    /** Prestation commandée à l'instant donné, payée dans la foulée. */
    private Commande prestationCommandeeLe(String instant) {
        Commande commande = new Commande("CMD-2026-0001", marie,
                new BigDecimal("39.98"), new BigDecimal("8.40"), new BigDecimal("48.38"),
                Instant.parse(instant));
        commande.confirmerPaiement(Instant.parse(instant));
        commandeExiste(commande);
        commandeDePrestations(commande);
        return commande;
    }

    /** Vrai si la demande est enregistrée à l'instant donné, faux si le délai la refuse. */
    private boolean demandeAcceptee(Commande commande, String instant) {
        RetractationService aLInstant = new RetractationService(commandes, demandes, interventionsRepo,
                courriel, parametres, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
        org.mockito.Mockito.lenient()
                .when(demandes.existsByCommandeAndStatut(commande, StatutDemandeAnnulation.EN_ATTENTE))
                .thenReturn(false);
        org.mockito.Mockito.lenient().when(demandes.save(any(DemandeAnnulation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        try {
            aLInstant.demander("marie@exemple.be", commande.getReference(), null);
            return true;
        } catch (RetractationImpossibleException refus) {
            assertThat(refus.getMotif()).isEqualTo(MotifRefusRetractation.DELAI_EXPIRE);
            return false;
        }
    }

    @Nested
    @DisplayName("prestation : délai en jours du garage (règlement 1182/71, art. 3)")
    class DelaiDUnePrestation {

        @Test
        @DisplayName("commandée un lundi à 9 h : encore rétractable le quatorzième jour à 23 h 59")
        void derniereHeureDuQuatorziemeJour() {
            // Lundi 7 septembre 2026, 9 h à Bruxelles. Quatorzième jour : lundi 21.
            Commande commande = prestationCommandeeLe("2026-09-07T07:00:00Z");

            assertThat(demandeAcceptee(commande, "2026-09-21T07:01:00Z")).isTrue();
            assertThat(demandeAcceptee(commande, "2026-09-21T21:59:00Z")).isTrue();
            assertThat(demandeAcceptee(commande, "2026-09-21T22:01:00Z")).isFalse();
        }

        @Test
        @DisplayName("quatorzième jour un samedi : le délai est reporté à la fin du lundi")
        void quatorziemeJourUnSamedi() {
            // Samedi 5 septembre 2026. Quatorzième jour : samedi 19, reporté au lundi 21.
            Commande commande = prestationCommandeeLe("2026-09-05T10:00:00Z");

            assertThat(demandeAcceptee(commande, "2026-09-20T12:00:00Z")).isTrue();
            assertThat(demandeAcceptee(commande, "2026-09-21T21:59:00Z")).isTrue();
            assertThat(demandeAcceptee(commande, "2026-09-21T22:01:00Z")).isFalse();
        }

        @Test
        @DisplayName("quatorzième jour férié (11 novembre) : le délai est reporté à la fin du lendemain")
        void quatorziemeJourFerie() {
            // Mercredi 28 octobre 2026. Quatorzième jour : mercredi 11 novembre, férié.
            Commande commande = prestationCommandeeLe("2026-10-28T10:00:00Z");

            assertThat(demandeAcceptee(commande, "2026-11-12T22:59:00Z")).isTrue();
            assertThat(demandeAcceptee(commande, "2026-11-12T23:01:00Z")).isFalse();
        }

        @Test
        @DisplayName("le jour se lit au garage : une commande de 0 h 30 compte depuis ce jour-là, pas depuis la veille")
        void jourDuGarage() {
            // Mardi 8 septembre 2026 à 0 h 30 à Bruxelles, soit le lundi 7 à 22 h 30 en UTC.
            Commande commande = prestationCommandeeLe("2026-09-07T22:30:00Z");

            assertThat(demandeAcceptee(commande, "2026-09-22T21:59:00Z")).isTrue();
            assertThat(demandeAcceptee(commande, "2026-09-22T22:01:00Z")).isFalse();
        }
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
        @DisplayName("prestations à J-15 : le délai court depuis la conclusion, il est expiré, rien n'est écrit")
        void quinzeJours() {
            Commande commande = commandePayeeIlYA(15);
            commandeExiste(commande);
            commandeDePrestations(commande);

            assertThatThrownBy(() -> service.demander(
                    "marie@exemple.be", commande.getReference(), null))
                    .isInstanceOf(RetractationImpossibleException.class)
                    .extracting(e -> ((RetractationImpossibleException) e).getMotif())
                    .isEqualTo(MotifRefusRetractation.DELAI_EXPIRE);
            verify(demandes, never()).save(any());
        }

        @Test
        @DisplayName("prestations à J-14 pile : la fenêtre reste ouverte jusqu'à la fin du jour")
        void quatorzeJoursPile() {
            // 14 fois 24 heures exactement : le quatorzième jour n'est pas fini.
            Commande commande = commandePayeeIlYA(14);
            commandeExiste(commande);
            commandeDePrestations(commande);
            when(demandes.existsByCommandeAndStatut(commande, StatutDemandeAnnulation.EN_ATTENTE))
                    .thenReturn(false);
            when(demandes.save(any(DemandeAnnulation.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            assertThat(service.demander("marie@exemple.be", commande.getReference(), null).getStatut())
                    .isEqualTo(StatutDemandeAnnulation.EN_ATTENTE);
        }

        @Test
        @DisplayName("pièces à J-30 : aucune date de remise, la demande est enregistrée, le garage tranche")
        void piecesSansRefusAutomatique() {
            // Pour un bien, le délai court de la prise de possession (VI.47 §2, 2°),
            // que la base ne connaît pas : refuser depuis la conclusion priverait le
            // consommateur d'une partie de son délai.
            Commande commande = commandePayeeIlYA(30);
            commandeExiste(commande);
            when(commandes.lignesServiceDe(commande)).thenReturn(List.of());
            when(demandes.existsByCommandeAndStatut(commande, StatutDemandeAnnulation.EN_ATTENTE))
                    .thenReturn(false);
            when(demandes.save(any(DemandeAnnulation.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            assertThat(service.demander("marie@exemple.be", commande.getReference(), null).getStatut())
                    .isEqualTo(StatutDemandeAnnulation.EN_ATTENTE);
        }

        @Test
        @DisplayName("commande mixte à J-30 : la règle des pièces s'applique, la demande est enregistrée")
        void commandeMixteSuitLaRegleDesPieces() {
            // Le panier refuse ce mélange, la base non : la partie pièces garde son délai
            // depuis la prise de possession, le refus automatique ne la touche pas.
            Commande commande = commandePayeeIlYA(30);
            commandeExiste(commande);
            // Les lignes de prestation ne sont plus lues : la présence de pièces décide.
            org.mockito.Mockito.lenient().when(commandes.lignesServiceDe(commande)).thenReturn(List.of(
                    org.mockito.Mockito.mock(be.autoservplus.vente.domain.LignePanier.class)));
            when(commandes.lignesDe(commande)).thenReturn(List.of(
                    org.mockito.Mockito.mock(be.autoservplus.vente.domain.LignePanier.class)));
            when(demandes.existsByCommandeAndStatut(commande, StatutDemandeAnnulation.EN_ATTENTE))
                    .thenReturn(false);
            when(demandes.save(any(DemandeAnnulation.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            assertThat(service.demander("marie@exemple.be", commande.getReference(), null).getStatut())
                    .isEqualTo(StatutDemandeAnnulation.EN_ATTENTE);
        }

        @Test
        @DisplayName("le delai court depuis la conclusion de la commande, pas depuis le paiement")
        void departDuDelai() {
            // Ici la commande date de J-13 mais n a ete payee qu hier : c est bien J-13
            // qui compte pour une prestation.
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
         * partagent {@code refusEventuel} et la construction de la vue. Ce test le
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
        @DisplayName("pièces à J-30 : le lien « Renoncer au contrat ici » reste proposé")
        void piecesToujoursDemandables() {
            // VI.61/2 §1 : la fonction reste disponible pendant tout le délai, qui court
            // de la prise de possession, inconnue en base.
            Commande commande = commandePayeeIlYA(30);
            commandeExiste(commande);
            when(demandes.historiqueDe(commande)).thenReturn(List.of());
            when(demandes.existsByCommandeAndStatut(commande, StatutDemandeAnnulation.EN_ATTENTE))
                    .thenReturn(false);

            assertThat(service.etatDeLaCommande("marie@exemple.be", commande.getReference())
                    .demandable()).isTrue();
        }

        @Test
        @DisplayName("prestations à J-15 : le délai depuis la conclusion est écoulé, plus de lien")
        void prestationsPlusDemandables() {
            Commande commande = commandePayeeIlYA(15);
            commandeExiste(commande);
            commandeDePrestations(commande);
            when(demandes.historiqueDe(commande)).thenReturn(List.of());

            assertThat(service.etatDeLaCommande("marie@exemple.be", commande.getReference())
                    .demandable()).isFalse();
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
