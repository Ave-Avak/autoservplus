package be.autoservplus.identite.service;

import be.autoservplus.common.exception.RegleMetierException;
import be.autoservplus.facturation.repository.FactureRepository;
import be.autoservplus.identite.domain.Langue;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Regle d adresse liee aux factures.
 *
 * <p>Le point defendu ici n est pas evident : l instantane client d une facture
 * <b>n est pas fige a l emission</b>. {@code PdfFactureService} lit l entite vive au
 * moment de produire le document, si bien qu une adresse effacee aujourd hui sortirait
 * sur une facture emise il y a des mois — privee de sa mention obligatoire
 * (AR n°1, art. 5). Les PDF deja archives, eux, sont immuables et ne bougent pas.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProfilService — adresse et factures")
class ProfilServiceTest {

    private static final String EMAIL = "marie@exemple.be";

    @Mock private UtilisateurRepository utilisateurs;
    @Mock private FactureRepository factures;

    private ProfilService service;
    private Utilisateur membre;

    @BeforeEach
    void setUp() {
        service = new ProfilService(utilisateurs, factures);
        membre = new Utilisateur(EMAIL, "$2a$12$h", "Dupont", "Marie", TypeUtilisateur.MEMBRE);
        lenient().when(utilisateurs.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(membre));
    }

    private void avecAdresse() {
        membre.modifierProfil("Marie", "Dupont", null, "Rue des Ateliers", "18",
                "1000", "Bruxelles", "Belgique", Langue.fr);
    }

    private void viderAdresse() {
        service.enregistrer(EMAIL, "Marie", "Dupont", null, "", "", "", "", "Belgique", Langue.fr);
    }

    @Test
    @DisplayName("sans facture, le membre peut effacer son adresse")
    void sansFactureEffacementPermis() {
        avecAdresse();
        when(factures.countByMembreEmailIgnoreCase(EMAIL)).thenReturn(0L);

        viderAdresse();

        assertThat(membre.getRue()).isNull();
        assertThat(membre.adressePostaleComplete()).isFalse();
    }

    @Test
    @DisplayName("avec une facture, l'effacement est refusé et l'adresse reste intacte")
    void avecFactureEffacementRefuse() {
        avecAdresse();
        when(factures.countByMembreEmailIgnoreCase(EMAIL)).thenReturn(1L);

        assertThatThrownBy(this::viderAdresse)
                .isInstanceOf(RegleMetierException.class);

        // Le refus doit etre TOTAL : rien ne doit avoir ete applique avant la levee.
        assertThat(membre.getRue()).isEqualTo("Rue des Ateliers");
        assertThat(membre.getLocalite()).isEqualTo("Bruxelles");
    }

    /**
     * Le controle ne se declenche que sur un <b>effacement</b>. Un membre qui n avait
     * aucune adresse et n en saisit toujours pas ne retire rien : ses factures
     * anciennes ne doivent pas le bloquer, et le depot n a meme pas a etre interroge.
     */
    @Test
    @DisplayName("sans adresse au départ : aucun effacement, et les factures ne sont pas consultées")
    void absenceInitialeNonBloquante() {
        viderAdresse();

        assertThat(membre.getRue()).isNull();
        verifyNoInteractions(factures);
    }

    @Test
    @DisplayName("modifier une adresse complète en une autre reste permis")
    void modificationPermiseAvecFacture() {
        avecAdresse();

        service.enregistrer(EMAIL, "Marie", "Dupont", null, "Chaussee de Mons", "5",
                "7090", "Braine-le-Comte", "Belgique", Langue.nl);

        assertThat(membre.getLocalite()).isEqualTo("Braine-le-Comte");
        assertThat(membre.getLangue()).isEqualTo(Langue.nl);
    }
}
