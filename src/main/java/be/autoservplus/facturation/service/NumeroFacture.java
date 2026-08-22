package be.autoservplus.facturation.service;

/**
 * Numero de facture attribue : sa forme lisible et ses deux composantes, que la
 * table {@code facture} stocke separement ({@code exercice}, {@code sequence_annuelle},
 * contrainte {@code uq_facture_sequence}).
 *
 * <p>Format {@code ANNEE-NNNN} (2026-0001), sans prefixe alphabetique contrairement
 * aux numeros de commande (CMD-) et d intervention (INT-) : l annee ouvre le numero
 * de facture, comme l usage comptable belge le veut, et la suite repart a 1 chaque
 * exercice. Quatre chiffres suffisent a un garage independant ; au-dela de 9999
 * factures dans l annee le format s allonge naturellement sans casser le tri, la
 * colonne acceptant 20 caracteres.</p>
 */
public record NumeroFacture(short exercice, int sequenceAnnuelle, String valeur) {

    static NumeroFacture de(short exercice, int sequenceAnnuelle) {
        return new NumeroFacture(exercice, sequenceAnnuelle,
                "%d-%04d".formatted(exercice, sequenceAnnuelle));
    }
}
