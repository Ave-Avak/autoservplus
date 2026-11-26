package be.autoservplus.common.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Demande de page des listes du back-office, à la taille commune de
 * {@code autoservplus.back-office.taille-page}.
 *
 * <p>La taille est un choix d'écran : le contrôleur la fixe et le service reçoit une
 * demande déjà bornée. Les listes paginées avant celles-ci (factures, commandes) lisent la
 * même propriété, pour que toutes les listes du back-office aient la même longueur.</p>
 */
@Component
public class PagesBackOffice {

    private final int taille;

    public PagesBackOffice(@Value("${autoservplus.back-office.taille-page:50}") int taille) {
        this.taille = taille;
    }

    /** @param pageSaisie numéro reçu dans l'adresse, à partir de 1 */
    public PageRequest demande(int pageSaisie) {
        return PageRequest.of(NumeroPage.borne(pageSaisie - 1, taille), taille);
    }
}
