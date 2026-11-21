package be.autoservplus.reservation.service;

import be.autoservplus.common.exception.RefusTraduit;

/**
 * <b>RM-07</b> : plafond de demandes EN_ATTENTE simultanees par membre
 * (parametre d atelier {@code max_rdv_en_attente_par_membre}) : le membre attend
 * la reponse du garage avant d empiler de nouvelles demandes.
 */
public class LimiteDemandesEnAttenteException extends RefusTraduit {

    public LimiteDemandesEnAttenteException(long demandesEnAttente) {
        super(null, "rdv.limite-demandes", new Object[]{demandesEnAttente},
                "Vous avez déjà %d demandes en attente de confirmation.".formatted(demandesEnAttente));
    }
}
