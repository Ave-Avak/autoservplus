package be.autoservplus.retractation.domain;

/**
 * Cycle de vie d une demande de retractation (F30, RM-23), aligne sur le CHECK
 * {@code ck_demande_annulation_statut} (V27).
 *
 * <p>Deux transitions, et deux seulement : une demande en attente est validee ou
 * refusee par l administrateur. Aucun retour en arriere — une validation a
 * rembourse le client et emis une note de credit, un refus est une decision
 * motivee opposee au consommateur. Revenir sur l une ou l autre n est pas une
 * transition d etat mais un evenement nouveau, qui passera par une nouvelle
 * demande.</p>
 */
public enum StatutDemandeAnnulation {
    EN_ATTENTE,
    VALIDEE,
    REFUSEE;

    public boolean peutPasserA(StatutDemandeAnnulation cible) {
        return switch (this) {
            case EN_ATTENTE       -> cible == VALIDEE || cible == REFUSEE;
            case VALIDEE, REFUSEE -> false;
        };
    }

    public boolean estTranchee() {
        return this != EN_ATTENTE;
    }
}
