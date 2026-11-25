package be.autoservplus.facturation.domain;

import be.autoservplus.common.exception.RefusTraduit;

/** Refus d'un encaissement ou de son annulation, traduit à l'écran (Q-H-43 à Q-H-46). */
public class EncaissementRefuseException extends RefusTraduit {

    private EncaissementRefuseException(String cle, Object[] arguments, String message) {
        super(null, cle, arguments, message);
    }

    private static EncaissementRefuseException de(String cle, String message) {
        return new EncaissementRefuseException(cle, new Object[0], message);
    }

    static EncaissementRefuseException montantDifferent() {
        return de("encaissement.erreur.montant", "Le montant encaissé doit être le total TVAC de la facture.");
    }

    static EncaissementRefuseException rienARegler() {
        return de("encaissement.erreur.rien-a-regler", "Une facture à 0 € n'a rien à régler.");
    }

    static EncaissementRefuseException dateFuture() {
        return de("encaissement.erreur.date-future", "La date d'encaissement ne peut pas être future.");
    }

    static EncaissementRefuseException dateAvantEmission() {
        return de("encaissement.erreur.date-avant-emission",
                "La date d'encaissement ne peut pas précéder l'émission de la facture.");
    }

    /** Le plafond voyage en nombre : le message le formate dans la langue de l'écran. */
    static EncaissementRefuseException especesAuDelaDuPlafond(java.math.BigDecimal plafond) {
        return new EncaissementRefuseException("encaissement.erreur.plafond-especes", new Object[]{plafond},
                "Au-delà de " + plafond + " EUR TVAC, une facture ne se règle pas en espèces.");
    }

    static EncaissementRefuseException motifObligatoire() {
        return de("encaissement.erreur.motif-obligatoire", "Le motif de l'annulation est obligatoire.");
    }

    static EncaissementRefuseException motifTropLong() {
        return de("encaissement.erreur.motif-longueur", "Le motif de l'annulation dépasse 500 caractères.");
    }

    static EncaissementRefuseException dejaAnnule() {
        return de("encaissement.erreur.deja-annule", "Cet encaissement est déjà annulé.");
    }

    public static EncaissementRefuseException pasDeFactureDAtelier() {
        return de("encaissement.erreur.facture-atelier", "Seule une facture d'atelier se règle au comptoir.");
    }

    public static EncaissementRefuseException factureAnnulee() {
        return de("encaissement.erreur.facture-annulee", "Une facture annulée par une note de crédit ne s'encaisse plus.");
    }

    public static EncaissementRefuseException dejaReglee() {
        return de("encaissement.erreur.deja-reglee", "Cette facture est déjà réglée.");
    }

    public static EncaissementRefuseException aucunEncaissement() {
        return de("encaissement.erreur.aucun", "Cette facture n'a aucun encaissement à annuler.");
    }
}
