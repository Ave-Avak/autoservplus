package be.autoservplus.vente.domain;

/**
 * Cycle de vie d un paiement, aligne sur le CHECK {@code ck_paiement_statut} (V4)
 * et sur le dictionnaire : projection des statuts Mollie — open (INITIE),
 * pending (EN_COURS), paid (REUSSI), failed/canceled (ECHOUE), expired (EXPIRE).
 * REMBOURSE existe pour coller au CHECK ; le remboursement (Refund distinct) est
 * le bloc retractation, aucune transition ne l exerce ici.
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
     * REUSSI est irreversible — le remboursement sera un Refund distinct, pas un
     * retour d etat. ECHOUE et EXPIRE sont terminaux : un re-essai est un NOUVEAU
     * paiement, la commande restant EN_ATTENTE_PAIEMENT pendant son delai.
     */
    public boolean peutPasserA(StatutPaiement cible) {
        return switch (this) {
            case INITIE   -> cible == EN_COURS || cible == REUSSI
                             || cible == ECHOUE || cible == EXPIRE;
            case EN_COURS -> cible == REUSSI || cible == ECHOUE || cible == EXPIRE;
            case REUSSI, ECHOUE, EXPIRE, REMBOURSE -> false;
        };
    }
}
