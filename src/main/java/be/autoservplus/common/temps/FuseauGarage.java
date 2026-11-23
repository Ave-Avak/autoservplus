package be.autoservplus.common.temps;

import be.autoservplus.reservation.repository.ParametreAtelierRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Fuseau horaire du garage, seul point d'accès pour tout ce qui date un fait à l'heure
 * du garage : une date affichée, le jour d'une échéance, l'année d'un numéro.
 *
 * <p>Les instants restent stockés en UTC et l'horloge de l'application est en UTC : seul
 * le formatage passe par ici. Lire le fuseau de l'horloge datait de la veille une
 * commande passée entre minuit et deux heures, et rangeait dans l'exercice précédent une
 * facture émise le 1er janvier à 0 h 30.</p>
 *
 * <p>Le fuseau appartient au garage, pas aux réservations. Il est encore lu dans les
 * paramètres de l'atelier, où il a été créé avec les rendez-vous : le jour où il passe à
 * la configuration du garage, cette classe est la seule à changer.</p>
 */
@Component
public class FuseauGarage {

    private final ParametreAtelierRepository parametres;

    public FuseauGarage(ParametreAtelierRepository parametres) {
        this.parametres = parametres;
    }

    public ZoneId zone() {
        return parametres.courants().zone();
    }

    /** Date et heure au garage, ou {@code null} pour un instant absent. */
    public LocalDateTime heureLocale(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, zone());
    }

    /** Jour au garage, ou {@code null} pour un instant absent. */
    public LocalDate jour(Instant instant) {
        return instant == null ? null : LocalDate.ofInstant(instant, zone());
    }

    /** Année au garage : celle que porte un numéro de commande, de facture ou d'avoir. */
    public int annee(Instant instant) {
        return instant.atZone(zone()).getYear();
    }
}
