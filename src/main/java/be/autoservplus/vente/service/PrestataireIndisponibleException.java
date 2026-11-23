package be.autoservplus.vente.service;

/**
 * Le prestataire de paiement n'a pas pu être joint, ou a répondu autre chose que
 * ce que le contrat prévoit.
 *
 * <p><b>Distincte de {@link PaiementImpossibleException}</b>, et la distinction
 * porte : celle-ci dit que la commande n'admet pas de paiement (déjà payée,
 * annulée), fait métier que le membre doit comprendre et qui ne changera pas s'il
 * réessaie. Celle-là dit que la demande était légitime mais que le tiers n'a pas
 * répondu : réessayer a donc du sens. Les confondre conduirait à décourager un
 * membre dont la commande est parfaitement payable.</p>
 *
 * <p>Elle porte une cause quand il y en a une, mais son message n'est JAMAIS
 * destiné à l'écran : la réponse d'un prestataire peut contenir des identifiants
 * de requête et des détails d'infrastructure. Le libellé vu par le membre vient de
 * l'i18n, côté web.</p>
 */
public class PrestataireIndisponibleException extends RuntimeException {

    public PrestataireIndisponibleException(String message) {
        super(message);
    }

    public PrestataireIndisponibleException(String message, Throwable cause) {
        super(message, cause);
    }
}
