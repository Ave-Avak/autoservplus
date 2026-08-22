package be.autoservplus.vente.service;

import be.autoservplus.common.exception.RegleMetierException;

/**
 * L ajout ou la modification demandee depasse le stock physique de la piece (F13).
 *
 * <p>Porte la quantite encore disponible pour que la couche web puisse la restituer
 * au membre — « stock insuffisant » seul n est pas actionnable, « il en reste 2 »
 * l est. Pas de code RM : le refus sur stock est une exigence de F13, pas une regle
 * numerotee du CdC (le constructeur sans code existe pour ce cas).</p>
 *
 * <p>Le controle porte sur le stock <b>physique</b> de la piece : la reservation
 * virtuelle de 30 minutes (RM-21) est documentee comme evolution V2 et rien n est
 * decremente a l ajout au panier.</p>
 */
public class StockInsuffisantException extends RegleMetierException {

    private final String libelle;
    private final int quantiteDisponible;

    public StockInsuffisantException(String libelle, int quantiteDemandee, int quantiteDisponible) {
        super("Stock insuffisant pour « %s » : %d demandee(s), %d disponible(s)."
                .formatted(libelle, quantiteDemandee, quantiteDisponible));
        this.libelle = libelle;
        this.quantiteDisponible = quantiteDisponible;
    }

    public String getLibelle() {
        return libelle;
    }

    public int getQuantiteDisponible() {
        return quantiteDisponible;
    }
}
