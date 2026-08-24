package be.autoservplus.vente.service;

/**
 * Le prestataire de paiement n a pas pu etre joint, ou a repondu autre chose que
 * ce que le contrat prevoit.
 *
 * <p><b>Distincte de {@link PaiementImpossibleException}</b>, et la distinction
 * porte : celle-ci dit que la commande n admet pas de paiement (deja payee,
 * annulee), fait metier que le membre doit comprendre et qui ne changera pas s il
 * reessaie. Celle-la dit que la demande etait legitime mais que le tiers n a pas
 * repondu — reessayer a donc du sens. Les confondre conduirait a decourager un
 * membre dont la commande est parfaitement payable.</p>
 *
 * <p>Elle porte une cause quand il y en a une, mais son message n est JAMAIS
 * destine a l ecran : la reponse d un prestataire peut contenir des identifiants
 * de requete et des details d infrastructure. Le libelle vu par le membre vient de
 * l i18n, cote web.</p>
 */
public class PrestataireIndisponibleException extends RuntimeException {

    public PrestataireIndisponibleException(String message) {
        super(message);
    }

    public PrestataireIndisponibleException(String message, Throwable cause) {
        super(message, cause);
    }
}
