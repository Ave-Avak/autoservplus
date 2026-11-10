package be.autoservplus.communication.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Repli des profils {@code dev}, {@code test} et {@code demo} sans relais SMTP : le
 * courriel entier est écrit au journal, lien compris, pour qu'un parcours reste
 * praticable sans boîte de réception.
 *
 * <p>Jamais sous {@code prod} : le démarrage y est refusé sans relais
 * ({@link be.autoservplus.config.ProprietesCourriel#verifierPourProduction()}). Un
 * journal de production ne porte donc aucun lien à jeton.</p>
 *
 * <p>La pièce jointe n'est pas écrite : son nom, son type et sa taille le sont. C'est
 * ce qui la rend observable, un fichier vide passerait sinon inaperçu.</p>
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
                Pièce   : {}

                {}
                ----------------------------------------------
                """, courriel.type(), courriel.nomDestinataire(), courriel.adresse(),
                courriel.sujet(), description(courriel.pieceJointe()), courriel.corps());
    }

    private static String description(PieceJointeCourriel piece) {
        if (piece == null) {
            return "aucune";
        }
        return "%s (%s, %d octets)".formatted(piece.nomFichier(), piece.typeMime(),
                piece.tailleOctets());
    }
}
