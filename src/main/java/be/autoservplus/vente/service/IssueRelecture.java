package be.autoservplus.vente.service;

import be.autoservplus.vente.domain.StatutPaiement;

/**
 * Ce qu une relecture du statut chez le prestataire a constate, et ce qu elle a
 * change. Destine au JOURNAL, pas a un ecran : aucune de ces valeurs n est traduite,
 * aucune ne s affiche a un membre.
 *
 * <p><b>Pourquoi la methode qui relit rend cette information au lieu de la
 * journaliser elle-meme.</b> Le meme traitement idempotent est declenche par deux
 * evenements distincts — le retour du membre depuis la page du prestataire, et la
 * notification serveur a serveur — et la documentation de deploiement demande de les
 * voir arriver « tour a tour ». Une ligne ecrite au fond du service serait identique
 * dans les deux cas et ne permettrait pas de les distinguer ; ce sont donc les deux
 * appelants qui redigent la leur, avec le meme constat.</p>
 *
 * <p>Le statut seul ne suffirait pas a montrer l idempotence promise : deux « paye »
 * de suite ne disent pas si la seconde relecture a emis une seconde facture. C est
 * {@link Effet} qui repond, et c est la moitie interessante de la ligne.</p>
 *
 * @param statutRelu statut authentique rapporte par le prestataire, jamais deduit
 *                   d une requete entrante
 * @param effet      ce que ce constat a change dans la base, ou n a pas change
 */
public record IssueRelecture(StatutPaiement statutRelu, Effet effet) {

    /** Ce que la relecture a change. Les libelles sont ceux qui partent au journal. */
    public enum Effet {

        /** Transition reelle : la commande passe PAYEE, la facture est emise. */
        FACTURE_EMISE("commande passee PAYEE, facture emise"),

        /**
         * Constat deja fait. Couvre le rejeu d une notification sur une commande deja
         * payee, et la course perdue contre le job d expiration RM-21 — cette
         * derniere ecrit par ailleurs son propre avertissement, qui porte le detail.
         */
        DEJA_TRAITE("deja traite, aucune ecriture"),

        /** Echec ou expiration de CETTE tentative ; la commande reste payable. */
        TENTATIVE_CLOSE("tentative close, la commande reste payable"),

        /** Le paiement n a pas encore abouti, et peut encore aboutir. */
        EN_ATTENTE("paiement toujours en cours");

        private final String libelle;

        Effet(String libelle) {
            this.libelle = libelle;
        }

        public String libelle() {
            return libelle;
        }
    }
}
