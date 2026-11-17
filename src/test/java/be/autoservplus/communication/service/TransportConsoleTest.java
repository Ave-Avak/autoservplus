package be.autoservplus.communication.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Courriel et repli console : pièces jointes")
class TransportConsoleTest {

    private static final PieceJointeCourriel AGENDA =
            new PieceJointeCourriel("rdv.ics", "text/calendar", new byte[12]);
    private static final PieceJointeCourriel FORMULAIRE =
            new PieceJointeCourriel("formulaire.pdf", "application/pdf", new byte[300]);

    @Test
    @DisplayName("le journal décrit chaque pièce jointe, nom, type et taille, dans l'ordre")
    void descriptionDesPieces() {
        assertThat(TransportConsole.description(List.of(AGENDA, FORMULAIRE)))
                .isEqualTo("rdv.ics (text/calendar, 12 octets), formulaire.pdf (application/pdf, 300 octets)");
        assertThat(TransportConsole.description(List.of())).isEqualTo("aucune");
    }

    @Test
    @DisplayName("un courriel sans pièce porte une liste vide, jamais nulle, et refuse une pièce nulle")
    void listeDesPieces() {
        assertThat(new Courriel("t", "a@b.be", "A", "s", "c", null).piecesJointes()).isEmpty();
        java.util.List<PieceJointeCourriel> avecTrou = new java.util.ArrayList<>();
        avecTrou.add(null);
        assertThatThrownBy(() -> new Courriel("t", "a@b.be", "A", "s", "c", avecTrou))
                .isInstanceOf(NullPointerException.class);
    }
}
