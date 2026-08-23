package be.autoservplus.facturation.service;

/**
 * Numero de note de credit attribue, format {@code AV-ANNEE-NNNN} (AV-2026-0001).
 *
 * <p><b>Prefixe AV, contrairement au numero de facture</b> qui ouvre directement sur
 * l annee (2026-0001). Ce n est pas une coquetterie : les deux suites repartent a 1
 * chaque exercice, et sans prefixe « 2026-0001 » designerait a la fois la premiere
 * facture et le premier avoir de l annee. Un client qui cite un numero au telephone,
 * un comptable qui classe une piece, doivent pouvoir dire de quel document il s agit
 * sans ouvrir le fichier. Le prefixe suit d ailleurs l usage des autres numeros du
 * projet (CMD-, INT-, RDV-) ; c est la facture qui fait exception, l usage comptable
 * belge voulant qu elle ouvre sur son exercice.</p>
 *
 * <p>Contrairement a la facture, l exercice et la sequence ne sont pas stockes en
 * colonnes separees : la table {@code avoir} du socle ne les prevoit pas et n en a
 * pas besoin — l unicite est portee par {@code uq_avoir_numero} seul, la ou la
 * facture doit en plus garantir {@code uq_facture_sequence}. L exercice se relit du
 * numero quand l archivage en a besoin.</p>
 */
public record NumeroAvoir(short exercice, int sequenceAnnuelle, String valeur) {

    static NumeroAvoir de(short exercice, int sequenceAnnuelle) {
        return new NumeroAvoir(exercice, sequenceAnnuelle,
                "AV-%d-%04d".formatted(exercice, sequenceAnnuelle));
    }
}
