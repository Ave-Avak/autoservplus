package be.autoservplus.rgpd.service;

import be.autoservplus.common.exception.RegleMetierException;

import java.time.Duration;

/**
 * Un export a deja ete produit pour ce membre dans les vingt-quatre heures (F22).
 *
 * <p>L article 12.5 du RGPD autorise le responsable a refuser une demande
 * <i>manifestement infondee ou excessive, notamment en raison de son caractere
 * repetitif</i>. La limite protege aussi la plateforme : chaque export lit
 * l integralite du dossier d un membre, un rafraichissement en boucle serait un
 * levier d epuisement de ressources bon marche.
 *
 * <p>Porte le temps restant a attendre pour que la couche web puisse le restituer :
 * un refus sans echeance n est pas actionnable — meme raisonnement que la quantite
 * disponible de {@code StockInsuffisantException}.
 *
 * <p>Pas de code RM : la limite vient de F22, pas d une regle numerotee du CdC.
 */
public class ExportTropRecentException extends RegleMetierException {

    private final Duration attenteRestante;

    public ExportTropRecentException(Duration attenteRestante) {
        super("Un export a deja ete produit dans les 24 dernieres heures : encore %d minute(s) a attendre."
                .formatted(attenteRestante.toMinutes()));
        this.attenteRestante = attenteRestante;
    }

    public Duration getAttenteRestante() {
        return attenteRestante;
    }
}
