package be.autoservplus.reservation.service;

import be.autoservplus.reservation.domain.ParametreAtelier;
import be.autoservplus.reservation.repository.ParametreAtelierRepository;
import be.autoservplus.reservation.service.dto.ParametresReservation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Paramètres de réservation réglés par le garage depuis le back-office : mode de
 * confirmation, délai de réponse, délai minimal, délai d'annulation par le membre et
 * délai du rappel. Les contrôles vivent dans {@link ParametreAtelier}.
 *
 * <p>Changer de mode ne touche aucune demande déjà en attente : chacune garde
 * l'échéance calculée à sa création. Les autres paramètres de l'atelier (fuseau, pas,
 * tampon, horizon, plafond de demandes) restent réglés par migration.</p>
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasRole('ADMINISTRATEUR')")
public class ParametresReservationService {

    private final ParametreAtelierRepository parametres;

    public ParametresReservationService(ParametreAtelierRepository parametres) {
        this.parametres = parametres;
    }

    public ParametresReservation courants() {
        ParametreAtelier p = parametres.courants();
        return new ParametresReservation(p.isConfirmationAutomatique(), p.getDelaiReponseHeures(),
                p.getDelaiMinimalHeures(), p.getDelaiAnnulationHeures(), p.getDelaiRappelHeures());
    }

    /** Lève {@link IllegalArgumentException} sur une valeur hors bornes, sans rien écrire. */
    @Transactional
    public void modifier(ParametresReservation nouveaux) {
        ParametreAtelier p = parametres.courants();
        p.modifierReservation(nouveaux.confirmationAutomatique(), nouveaux.delaiReponseHeures(),
                nouveaux.delaiMinimalHeures(), nouveaux.delaiAnnulationHeures(), nouveaux.delaiRappelHeures());
        parametres.save(p);
    }
}
