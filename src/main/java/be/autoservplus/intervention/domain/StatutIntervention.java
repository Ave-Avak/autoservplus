package be.autoservplus.intervention.domain;

/**
 * Cycle de vie d une intervention en atelier, aligne sur le CdC
 * (dictionnaire de donnees, table 3.8).
 *
 * <p>PLANIFIEE : creee au marquage HONORE du RDV, en attente de demarrage.
 * EN_COURS : le mecanicien travaille, {@code debutReel} enregistre.
 * SUSPENDUE : suspension temporaire (attente de piece, autre urgence) sans
 * cloture. ATTENTE_VALIDATION_MEMBRE : depassement de devis &gt; 10 % (RM-15),
 * la poursuite exige l accord expres du membre. TERMINEE : travaux acheves,
 * {@code finReelle} figee ; c est au passage vers cet etat que le module
 * facturation (post-V1) declenchera la generation de la facture (RM-17).
 * ANNULEE : arret definitif de l intervention sans passage a TERMINEE.</p>
 *
 * <p>La facturation n est PAS un statut du CdC : elle est modelisee comme une
 * action deroulee au passage a TERMINEE. Aucun statut FACTUREE ici.</p>
 */
public enum StatutIntervention {
    PLANIFIEE,
    EN_COURS,
    SUSPENDUE,
    ATTENTE_VALIDATION_MEMBRE,
    TERMINEE,
    ANNULEE;

    /**
     * Transitions autorisees par le CdC. Toute autre est refusee par le domaine.
     *
     * <p>PLANIFIEE peut demarrer ou etre annulee (avant tout travail).
     * EN_COURS peut se suspendre, passer en attente de validation membre,
     * se terminer, ou etre annulee. SUSPENDUE et ATTENTE_VALIDATION_MEMBRE
     * sont symetriques : elles reprennent en EN_COURS ou basculent en ANNULEE.
     * TERMINEE et ANNULEE sont terminaux : aucune sortie, meme pour
     * correction (une correction post-facturation passera par un avoir).</p>
     *
     * <p>Self-loops toujours refuses : aucune branche ne retourne
     * {@code cible == this}.</p>
     */
    public boolean peutPasserA(StatutIntervention cible) {
        return switch (this) {
            case PLANIFIEE                 -> cible == EN_COURS || cible == ANNULEE;
            case EN_COURS                  -> cible == SUSPENDUE
                                              || cible == ATTENTE_VALIDATION_MEMBRE
                                              || cible == TERMINEE
                                              || cible == ANNULEE;
            case SUSPENDUE                 -> cible == EN_COURS || cible == ANNULEE;
            case ATTENTE_VALIDATION_MEMBRE -> cible == EN_COURS || cible == ANNULEE;
            case TERMINEE, ANNULEE         -> false;
        };
    }

    /**
     * L intervention est modifiable (lignes, commentaire admin) tant qu elle
     * n est pas terminale. Une fois TERMINEE (facturation branchee) ou
     * ANNULEE, plus aucune ecriture n est autorisee.
     */
    public boolean estEditable() {
        return this != TERMINEE && this != ANNULEE;
    }
}
