package be.autoservplus.intervention.domain;

import be.autoservplus.common.exception.RefusTraduit;

/**
 * Changement des pièces incluses refusé : ligne qui n'est pas un forfait aux pièces du garage,
 * quantité inférieure à un, pièce absente de la ligne, ou intervention close.
 */
public class PieceIncluseRefuseeException extends RefusTraduit {

    private PieceIncluseRefuseeException(String cle, String message) {
        super(cle, message);
    }

    static PieceIncluseRefuseeException horsForfaitFourni() {
        return new PieceIncluseRefuseeException("admin.intervention.incluses.erreur.ligne",
                "Seul un forfait dont le garage fournit les pièces porte des pièces incluses.");
    }

    static PieceIncluseRefuseeException quantite() {
        return new PieceIncluseRefuseeException("admin.intervention.incluses.erreur.quantite",
                "La quantité d'une pièce incluse vaut au moins un.");
    }

    static PieceIncluseRefuseeException absente() {
        return new PieceIncluseRefuseeException("admin.intervention.incluses.erreur.absente",
                "Cette pièce n'est pas incluse dans la ligne.");
    }

    static PieceIncluseRefuseeException close() {
        return new PieceIncluseRefuseeException("admin.intervention.incluses.erreur.close",
                "Les pièces incluses ne changent plus après la clôture ou l'annulation.");
    }
}
