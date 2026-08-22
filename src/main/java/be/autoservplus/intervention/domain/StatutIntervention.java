package be.autoservplus.intervention.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

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
     * se terminer, ou etre annulee. SUSPENDUE reprend en EN_COURS, bascule en
     * ANNULEE, ou passe en ATTENTE_VALIDATION_MEMBRE si le garage chiffre un
     * depassement pendant la suspension (cas courant : le devis explose une fois
     * la piece diagnostiquee, travail a l arret). ATTENTE_VALIDATION_MEMBRE
     * reprend en EN_COURS ou bascule en ANNULEE.
     * TERMINEE et ANNULEE sont terminaux : aucune sortie, meme pour
     * correction (une correction post-facturation passera par un avoir).</p>
     *
     * <p>Note d ecart : le CdC (table 3.8) ne liste pas explicitement
     * SUSPENDUE -&gt; ATTENTE_VALIDATION_MEMBRE. Elle est ajoutee ici parce que
     * {@link #estEditable()} autorise le garage a chiffrer une ligne en
     * suspension : sans cette transition, un depassement constate a l arret
     * echapperait a RM-15. PLANIFIEE reste volontairement hors du dispositif :
     * la regle garde la « poursuite » des travaux, or rien n a commence.</p>
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
            case SUSPENDUE                 -> cible == EN_COURS
                                              || cible == ATTENTE_VALIDATION_MEMBRE
                                              || cible == ANNULEE;
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

    /**
     * Projection RM-16 : le membre ne voit pas la mecanique interne du garage.
     * Les trois statuts nominaux (En attente, En cours, Terminee) resument le
     * cycle percu par le client ; ANNULEE s ajoute comme cas terminal explicite
     * pour ne pas laisser un membre devant une intervention qui « disparait ».
     *
     * <p>SUSPENDUE et ATTENTE_VALIDATION_MEMBRE relevent de la mecanique
     * interne du garage : le membre les voit toutes deux comme « En cours »
     * (le travail continue de son cote, meme s il est momentanement suspendu
     * cote atelier). L accord/refus sur un depassement de devis (RM-15)
     * lui sera demande via un canal dedie, pas via la lecture du statut.</p>
     *
     * <p>Le mapping est porte par un {@link EnumMap} immuable unique
     * (une seule source de verite pour le lien statut technique -&gt; percu),
     * verifie exhaustif au chargement de la classe : ajouter un statut sans
     * l inclure ici fait echouer l initialisation.</p>
     */
    public String percuLabel() {
        return LABELS_PERCU.get(this);
    }

    private static final Map<StatutIntervention, String> LABELS_PERCU;
    static {
        EnumMap<StatutIntervention, String> m = new EnumMap<>(StatutIntervention.class);
        m.put(PLANIFIEE,                 "En attente");
        m.put(EN_COURS,                  "En cours");
        m.put(SUSPENDUE,                 "En cours");
        m.put(ATTENTE_VALIDATION_MEMBRE, "En cours");
        m.put(TERMINEE,                  "Terminée");
        m.put(ANNULEE,                   "Annulée");
        for (StatutIntervention s : values()) {
            if (!m.containsKey(s)) {
                throw new IllegalStateException(
                        "StatutIntervention." + s + " n a pas de percuLabel — completer LABELS_PERCU.");
            }
        }
        LABELS_PERCU = Collections.unmodifiableMap(m);
    }
}
