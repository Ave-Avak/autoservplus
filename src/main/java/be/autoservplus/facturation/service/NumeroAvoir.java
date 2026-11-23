package be.autoservplus.facturation.service;

/**
 * Numéro de note de crédit attribué, format {@code AV-ANNEE-NNNN} (AV-2026-0001).
 *
 * <p><b>Préfixe AV, contrairement au numéro de facture</b> qui ouvre directement sur
 * l'année (2026-0001). Ce n'est pas une coquetterie : les deux suites repartent à 1
 * chaque exercice, et sans préfixe « 2026-0001 » désignerait à la fois la première
 * facture et le premier avoir de l'année. Un client qui cite un numéro au téléphone,
 * un comptable qui classe une pièce, doivent pouvoir dire de quel document il s'agit
 * sans ouvrir le fichier. Le préfixe suit d'ailleurs l'usage des autres numéros du
 * projet (CMD-, INT-, RDV-). C'est la facture qui fait exception, l'usage comptable
 * belge voulant qu'elle ouvre sur son exercice.</p>
 *
 * <p>Contrairement à la facture, l'exercice et la séquence ne sont pas stockés en
 * colonnes séparées : la table {@code avoir} du socle ne les prévoit pas et n'en a
 * pas besoin. L'unicité est portée par {@code uq_avoir_numero} seul, là où la
 * facture doit en plus garantir {@code uq_facture_sequence}. L'exercice se relit du
 * numéro quand l'archivage en a besoin.</p>
 */
public record NumeroAvoir(short exercice, int sequenceAnnuelle, String valeur) {

    static NumeroAvoir de(short exercice, int sequenceAnnuelle) {
        return new NumeroAvoir(exercice, sequenceAnnuelle,
                "AV-%d-%04d".formatted(exercice, sequenceAnnuelle));
    }
}
