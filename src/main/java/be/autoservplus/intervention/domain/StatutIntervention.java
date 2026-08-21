package be.autoservplus.intervention.domain;

/**
 * Cycle de vie d une intervention en atelier.
 *
 * <p>PLANIFIEE : creee lorsqu un rendez-vous est marque honore, en attente de
 * demarrage effectif au garage. EN_COURS : le mecanicien travaille, l heure de
 * debut est enregistree. EN_PAUSE : suspension temporaire (attente de piece,
 * autre urgence) sans cloture. TERMINEE : les travaux sont acheves, l heure de
 * fin est figee. FACTUREE : hook du module facturation, pas active en V1.</p>
 */
public enum StatutIntervention {
    PLANIFIEE,
    EN_COURS,
    EN_PAUSE,
    TERMINEE,
    FACTUREE;

    /** Transitions autorisees. Toute autre est refusee par le domaine. */
    public boolean peutPasserA(StatutIntervention cible) {
        return switch (this) {
            case PLANIFIEE -> cible == EN_COURS;
            case EN_COURS  -> cible == EN_PAUSE || cible == TERMINEE;
            case EN_PAUSE  -> cible == EN_COURS || cible == TERMINEE;
            case TERMINEE  -> cible == FACTUREE;
            case FACTUREE  -> false;
        };
    }

    /** L intervention est ouverte : ses lignes et son commentaire peuvent etre modifies. */
    public boolean estEditable() {
        return this == PLANIFIEE || this == EN_COURS || this == EN_PAUSE;
    }
}
