package be.autoservplus.catalogue.service;

import be.autoservplus.common.exception.RegleMetierException;

/**
 * Unicite du catalogue violee : nom de service deja pris (A1), code de prestation
 * ou reference fabricant deja enregistres.
 *
 * <p>Meme patron que les exceptions dediees du module vente : une cause de refus
 * identifiable porte sa propre classe pour que la couche web la traduise en message
 * i18n cible. {@code champ} nomme le champ de formulaire fautif — le controleur
 * peut ainsi accrocher l erreur au champ plutot que d afficher un message global.</p>
 *
 * <p>Pas de code RM : l unicite est un invariant technique du dictionnaire de
 * donnees, aucune regle numerotee du CdC ne la couvre.</p>
 */
public class DoublonCatalogueException extends RegleMetierException {

    private final String champ;
    private final String valeur;

    public DoublonCatalogueException(String champ, String valeur, String message) {
        super(message);
        this.champ = champ;
        this.valeur = valeur;
    }

    public String getChamp() {
        return champ;
    }

    public String getValeur() {
        return valeur;
    }
}
