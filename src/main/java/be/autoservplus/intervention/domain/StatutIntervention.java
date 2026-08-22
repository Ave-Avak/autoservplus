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
     * se terminer, ou etre annulee. SUSPENDUE reprend en EN_COURS ou bascule en
     * ANNULEE. ATTENTE_VALIDATION_MEMBRE reprend en EN_COURS ou bascule en
     * ANNULEE. TERMINEE et ANNULEE sont terminaux : aucune sortie, meme pour
     * correction (une correction post-facturation passera par un avoir).</p>
     *
     * <p>Aucun ecart avec le CdC (table 3.8). ATTENTE_VALIDATION_MEMBRE n a
     * qu une seule entree, depuis EN_COURS, parce que c est le seul etat ou
     * {@link #accepteAjoutDeLigne()} laisse chiffrer une ligne : la seule cause
     * d un depassement est un ajout, donc la seule origine possible de la bascule
     * est l etat ou l ajout est permis.</p>
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
     * L intervention est modifiable (commentaire admin, retrait de ligne) tant
     * qu elle n est pas terminale. Une fois TERMINEE (facturation branchee) ou
     * ANNULEE, plus aucune ecriture n est autorisee.
     *
     * <p>Ne couvre <b>pas</b> l ajout de ligne, plus restrictif : voir
     * {@link #accepteAjoutDeLigne()}.</p>
     */
    public boolean estEditable() {
        return this != TERMINEE && this != ANNULEE;
    }

    /**
     * Le dossier accepte-t-il une nouvelle ligne ? <b>RM-14</b> : uniquement
     * pendant la realisation. Le CdC ouvre l ajout « en cours d intervention »
     * et nulle part ailleurs — ni avant le demarrage, ni a l arret, ni pendant
     * qu une question est posee au membre.
     *
     * <p>Source de verite unique de la regle : l entite la fait respecter, le DTO
     * admin s en sert pour n afficher le formulaire d ajout que la ou il aboutira.
     * Retirer une ligne ou commenter reste regi par {@link #estEditable()} : ces
     * deux actions ne peuvent pas gonfler le devis, elles n ont pas a etre gardees
     * aussi etroitement.</p>
     */
    public boolean accepteAjoutDeLigne() {
        return this == EN_COURS;
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
        return PERCUS.get(this).label;
    }

    /**
     * Cle i18n du statut percu (RM-16), pour les templates qui passent par le
     * {@code MessageSource} ({@code #{...}}) plutot que par le libelle francais
     * pre-formate de {@link #percuLabel()}. Meme projection, meme source de
     * verite : les deux lectures sortent du meme {@link Percu}.
     */
    public String clePercue() {
        return PERCUS.get(this).clef;
    }

    /**
     * Les quatre etats percus du CdC. Chaque valeur porte les deux representations
     * d un meme percu — le libelle francais historique et la cle i18n — pour que la
     * projection statut technique -&gt; percu reste definie a un seul endroit,
     * {@link #PERCUS}, quel que soit le canal d affichage.
     */
    private enum Percu {
        EN_ATTENTE("En attente", "statut.percu.attente"),
        EN_COURS("En cours", "statut.percu.en_cours"),
        TERMINEE("Terminée", "statut.percu.terminee"),
        ANNULEE("Annulée", "statut.percu.annulee");

        private final String label;
        private final String clef;

        Percu(String label, String clef) {
            this.label = label;
            this.clef = clef;
        }
    }

    private static final Map<StatutIntervention, Percu> PERCUS;
    static {
        EnumMap<StatutIntervention, Percu> m = new EnumMap<>(StatutIntervention.class);
        m.put(PLANIFIEE,                 Percu.EN_ATTENTE);
        m.put(EN_COURS,                  Percu.EN_COURS);
        m.put(SUSPENDUE,                 Percu.EN_COURS);
        m.put(ATTENTE_VALIDATION_MEMBRE, Percu.EN_COURS);
        m.put(TERMINEE,                  Percu.TERMINEE);
        m.put(ANNULEE,                   Percu.ANNULEE);
        for (StatutIntervention s : values()) {
            if (!m.containsKey(s)) {
                throw new IllegalStateException(
                        "StatutIntervention." + s + " n a pas de statut percu — completer PERCUS.");
            }
        }
        PERCUS = Collections.unmodifiableMap(m);
    }
}
