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

    /**
     * Transitions autorisees. Toute autre est refusee par le domaine.
     *
     * <p>PLANIFIEE -> TERMINEE est un raccourci metier assume pour les prestations
     * express (montage pneu, controle rapide) que le mecanicien execute sans passer
     * par un EN_COURS explicite. Ecart vs le chemin nominal de l analyse UML V3,
     * documente en dette.</p>
     *
     * <p>TERMINEE -> EN_COURS reouvre une intervention cloturee pour correction :
     * FACTUREE est le seul etat de verrouillage definitif. Une fois facturee,
     * l intervention releve du droit comptable et ne peut plus etre modifiee ;
     * une correction passera par un avoir.</p>
     *
     * <p>Les self-loops (EN_PAUSE -> EN_PAUSE, EN_COURS -> EN_COURS, etc.) restent
     * refuses : aucune branche ne retourne {@code cible == this}.</p>
     */
    public boolean peutPasserA(StatutIntervention cible) {
        return switch (this) {
            case PLANIFIEE -> cible == EN_COURS || cible == TERMINEE;
            case EN_COURS  -> cible == EN_PAUSE || cible == TERMINEE;
            case EN_PAUSE  -> cible == EN_COURS || cible == TERMINEE;
            case TERMINEE  -> cible == EN_COURS || cible == FACTUREE;
            case FACTUREE  -> false;
        };
    }

    /**
     * L intervention est modifiable (lignes, commentaire admin). Le seul etat
     * verrouille est FACTUREE : une intervention TERMINEE reste editable pour
     * corriger une erreur avant facturation, la reouverture n est necessaire que
     * pour signaler au client (via le statut) que le travail continue.
     */
    public boolean estEditable() {
        return this != FACTUREE;
    }
}
