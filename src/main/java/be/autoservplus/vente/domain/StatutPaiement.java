package be.autoservplus.vente.domain;

/**
 * Cycle de vie d un paiement, aligne sur le CHECK {@code ck_paiement_statut} (V4)
 * et sur le dictionnaire : projection des statuts Mollie — open (INITIE),
 * pending (EN_COURS), paid (REUSSI), failed/canceled (ECHOUE), expired (EXPIRE),
 * refunded (REMBOURSE, exerce par la retractation F30).
 */
public enum StatutPaiement {
    INITIE,
    EN_COURS,
    REUSSI,
    ECHOUE,
    EXPIRE,
    REMBOURSE;

    /**
     * INITIE peut passer partout (un webhook « paid » peut arriver sans que
     * pending ait ete vu) ; EN_COURS se resout en REUSSI, ECHOUE ou EXPIRE ;
     * ECHOUE et EXPIRE sont terminaux — un re-essai est un NOUVEAU paiement, la
     * commande restant EN_ATTENTE_PAIEMENT pendant son delai.
     *
     * <p>REUSSI n admet qu une suite, REMBOURSE (F30). Ce n est pas un retour en
     * arriere : l encaissement a bien eu lieu et reste au dossier, la facture qui
     * l atteste est immuable, et c est un second mouvement — un Refund chez le
     * prestataire, une note de credit dans les livres — qui le contre-passe. Le
     * statut suit ce second mouvement plutot que d effacer le premier, et REMBOURSE
     * est a son tour terminal : on ne rembourse pas deux fois.</p>
     */
    public boolean peutPasserA(StatutPaiement cible) {
        return switch (this) {
            case INITIE   -> cible == EN_COURS || cible == REUSSI
                             || cible == ECHOUE || cible == EXPIRE;
            case EN_COURS -> cible == REUSSI || cible == ECHOUE || cible == EXPIRE;
            case REUSSI   -> cible == REMBOURSE;
            case ECHOUE, EXPIRE, REMBOURSE -> false;
        };
    }
}
