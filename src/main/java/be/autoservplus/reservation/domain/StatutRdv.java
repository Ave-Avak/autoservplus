package be.autoservplus.reservation.domain;

/**
 * Cycle de vie d un rendez-vous.
 *
 * <p>EN_ATTENTE : demande par le membre, pas encore validee par le garage.
 * CONFIRME : accepte par le garage. REFUSE : decline avec motif.
 * ANNULE : retire par le membre, ou par le garage qui ne peut plus tenir le creneau
 * (depuis EN_ATTENTE comme depuis CONFIRME). HONORE : le membre s est presente.
 * ABSENT : le membre ne s est pas presente.</p>
 */
public enum StatutRdv {
    EN_ATTENTE,
    CONFIRME,
    REFUSE,
    ANNULE,
    HONORE,
    ABSENT;

    /** Un rendez-vous encore modifiable par le membre. */
    public boolean estEnCours() {
        return this == EN_ATTENTE || this == CONFIRME;
    }

    /** Un rendez-vous dont le creneau doit etre libere. */
    public boolean libereLeCreneau() {
        return this == REFUSE || this == ANNULE;
    }

    /** Transitions autorisees (RM-10, version implementee a six etats). */
    public boolean peutPasserA(StatutRdv cible) {
        return switch (this) {
            case EN_ATTENTE -> cible == CONFIRME || cible == REFUSE || cible == ANNULE;
            case CONFIRME   -> cible == HONORE || cible == ABSENT || cible == ANNULE;
            case REFUSE, ANNULE, HONORE, ABSENT -> false;
        };
    }
}