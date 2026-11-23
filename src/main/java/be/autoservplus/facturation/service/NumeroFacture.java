package be.autoservplus.facturation.service;

/**
 * Numéro de facture attribué : sa forme lisible et ses deux composantes, que la
 * table {@code facture} stocke séparément ({@code exercice}, {@code sequence_annuelle},
 * contrainte {@code uq_facture_sequence}).
 *
 * <p>Format {@code ANNEE-NNNN} (2026-0001), sans préfixe alphabétique contrairement
 * aux numéros de commande (CMD-) et d'intervention (INT-) : l'année ouvre le numéro
 * de facture, comme l'usage comptable belge le veut, et la suite repart à 1 chaque
 * exercice. Quatre chiffres suffisent à un garage indépendant. Au-delà de 9999
 * factures dans l'année le format s'allonge naturellement sans casser le tri, la
 * colonne acceptant 20 caractères.</p>
 */
public record NumeroFacture(short exercice, int sequenceAnnuelle, String valeur) {

    static NumeroFacture de(short exercice, int sequenceAnnuelle) {
        return new NumeroFacture(exercice, sequenceAnnuelle,
                "%d-%04d".formatted(exercice, sequenceAnnuelle));
    }
}
