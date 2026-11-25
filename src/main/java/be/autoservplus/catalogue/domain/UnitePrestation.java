package be.autoservplus.catalogue.domain;

/**
 * Unité dans laquelle une prestation se compte et se facture (V72).
 *
 * <p>Une prestation à l'heure est la main-d'œuvre au taux du garage : l'atelier l'ajoute
 * en cours d'intervention, en heures. Elle ne se réserve pas et ne s'achète pas en ligne,
 * le client ne sachant pas combien d'heures il faut.</p>
 */
public enum UnitePrestation {

    FORFAIT,

    HEURE
}
