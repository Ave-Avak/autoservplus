package be.autoservplus.vente.domain;

/**
 * Cycle de vie d'un paiement, aligné sur le CHECK {@code ck_paiement_statut} (V4)
 * et sur le dictionnaire : projection des statuts Mollie, open (INITIE),
 * pending (EN_COURS), paid (REUSSI), failed/canceled (ECHOUE), expired (EXPIRE),
 * refunded (REMBOURSE, exercé par la rétractation F30).
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
     * pending ait été vu). EN_COURS se résout en REUSSI, ECHOUE ou EXPIRE.
     * ECHOUE et EXPIRE sont terminaux : un re-essai est un NOUVEAU paiement, la
     * commande restant EN_ATTENTE_PAIEMENT pendant son délai.
     *
     * <p>REUSSI n'admet qu'une suite, REMBOURSE (F30). Ce n'est pas un retour en
     * arrière : l'encaissement a bien eu lieu et reste au dossier, la facture qui
     * l'atteste est immuable, et c'est un second mouvement (un Refund chez le
     * prestataire, une note de crédit dans les livres) qui le contre-passe. Le
     * statut suit ce second mouvement plutôt que d'effacer le premier, et REMBOURSE
     * est à son tour terminal : on ne rembourse pas deux fois.</p>
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
