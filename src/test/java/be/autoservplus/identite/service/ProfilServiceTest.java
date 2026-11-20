package be.autoservplus.identite.service;

import be.autoservplus.common.exception.RegleMetierException;
import be.autoservplus.identite.domain.Langue;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.vente.domain.StatutCommande;
import be.autoservplus.vente.repository.CommandeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Règle d'adresse liée aux commandes en attente de paiement.
 *
 * <p>Depuis l'instantané client (V61), une facture émise garde l'adresse qu'elle portait.
 * Reste une commande validée mais pas encore payée : sa facture sera émise au paiement
 * avec l'adresse du moment, qui ne doit donc pas disparaître d'ici là.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProfilService : adresse et commandes en attente de paiement")
class ProfilServiceTest {

    private static final String EMAIL = "marie@exemple.be";

    @Mock private UtilisateurRepository utilisateurs;
    @Mock private CommandeRepository commandes;
    @Mock private HistoriqueModificationsCompte historique;

    private ProfilService service;
    private Utilisateur membre;

    @BeforeEach
    void setUp() {
        service = new ProfilService(utilisateurs, new GardeAdresseCommande(commandes), historique);
        membre = new Utilisateur(EMAIL, "$2a$12$h", "Dupont", "Marie", TypeUtilisateur.MEMBRE);
        lenient().when(utilisateurs.findByEmail(EMAIL)).thenReturn(Optional.of(membre));
    }

    private void avecAdresse() {
        membre.modifierProfil("Marie", "Dupont", null, "Rue des Ateliers", "18",
                "1000", "Bruxelles", "Belgique", Langue.fr);
    }

    private void viderAdresse() {
        service.enregistrer(EMAIL, "Marie", "Dupont", null, "", "", "", "", "Belgique", Langue.fr);
    }

    private void commandeEnAttente(boolean presente) {
        when(commandes.existsByMembreIdAndStatut(any(), eq(StatutCommande.EN_ATTENTE_PAIEMENT)))
                .thenReturn(presente);
    }

    @Test
    @DisplayName("sans commande en attente de paiement, le membre peut effacer son adresse")
    void sansCommandeEnAttenteEffacementPermis() {
        avecAdresse();
        commandeEnAttente(false);

        viderAdresse();

        assertThat(membre.getRue()).isNull();
        assertThat(membre.adressePostaleComplete()).isFalse();
    }

    @Test
    @DisplayName("une commande attend son paiement : l'effacement est refusé, l'adresse reste intacte")
    void commandeEnAttenteEffacementRefuse() {
        avecAdresse();
        commandeEnAttente(true);

        assertThatThrownBy(this::viderAdresse).isInstanceOf(RegleMetierException.class);

        // Le refus doit être total : rien n'est appliqué avant la levée.
        assertThat(membre.getRue()).isEqualTo("Rue des Ateliers");
        assertThat(membre.getLocalite()).isEqualTo("Bruxelles");
    }

    @Test
    @DisplayName("sans adresse au départ : aucun effacement, les commandes ne sont pas consultées")
    void absenceInitialeNonBloquante() {
        viderAdresse();

        assertThat(membre.getRue()).isNull();
        verifyNoInteractions(commandes);
    }

    @Test
    @DisplayName("remplacer une adresse complète par une autre reste permis")
    void modificationPermise() {
        avecAdresse();

        service.enregistrer(EMAIL, "Marie", "Dupont", null, "Chaussee de Mons", "5",
                "7090", "Braine-le-Comte", "Belgique", Langue.nl);

        assertThat(membre.getLocalite()).isEqualTo("Braine-le-Comte");
        assertThat(membre.getLangue()).isEqualTo(Langue.nl);
    }
}
