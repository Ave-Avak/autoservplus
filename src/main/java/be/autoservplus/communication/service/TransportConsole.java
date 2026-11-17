package be.autoservplus.communication.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Repli des profils {@code dev}, {@code test} et {@code demo} sans relais SMTP : le
 * courriel entier est écrit au journal, lien compris, pour qu'un parcours reste
 * praticable sans boîte de réception.
 *
 * <p>Jamais sous {@code prod} : le démarrage y est refusé sans relais
 * ({@link be.autoservplus.config.ProprietesCourriel#verifierPourProduction()}). Un
 * journal de production ne porte donc aucun lien à jeton.</p>
 *
 * <p>Les pièces jointes ne sont pas écrites : le nom, le type et la taille de chacune
 * le sont. C'est ce qui les rend observables, un fichier vide passerait sinon
 * inaperçu.</p>
 */
public class TransportConsole implements TransportCourriel {

    private static final Logger JOURNAL = LoggerFactory.getLogger(TransportConsole.class);

    public TransportConsole() {
        JOURNAL.warn("Aucun relais SMTP configuré : les courriels sont JOURNALISÉS et jamais "
                + "expédiés. Les liens d'activation et de réinitialisation se lisent dans ce "
                + "journal.");
    }

    @Override
    public void transmettre(Courriel courriel) {
        JOURNAL.info("""

                ---------- COURRIEL NON EXPÉDIÉ ({}) ----------
                À       : {} <{}>
                Sujet   : {}
                Pièces  : {}

                {}
                ----------------------------------------------
                """, courriel.type(), courriel.nomDestinataire(), courriel.adresse(),
                courriel.sujet(), description(courriel.piecesJointes()), courriel.corps());
    }

    static String description(List<PieceJointeCourriel> pieces) {
        if (pieces.isEmpty()) {
            return "aucune";
        }
        return pieces.stream()
                .map(piece -> "%s (%s, %d octets)".formatted(piece.nomFichier(),
                        piece.typeMime(), piece.tailleOctets()))
                .collect(Collectors.joining(", "));
    }
}
