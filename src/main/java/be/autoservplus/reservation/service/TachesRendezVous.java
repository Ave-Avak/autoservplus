package be.autoservplus.reservation.service;

import be.autoservplus.reservation.domain.ParametreAtelier;
import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.repository.ParametreAtelierRepository;
import be.autoservplus.reservation.repository.RdvRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;

/**
 * Tâche planifiée des rendez-vous, une fois par minute comme
 * {@code ExpirationCommandesJob} : le rappel avant un rendez-vous confirmé.
 *
 * <p>Une transaction par rendez-vous, pour qu'un échec n'emporte pas les autres et que
 * chaque courriel parte après la validation de sa propre réservation. Le rappel est
 * réservé par une mise à jour conditionnelle avant l'envoi : une seconde instance, ou
 * un passage repris après une panne, lit la réservation et passe son tour. La date
 * vient de l'horloge injectée, la méthode se teste en l'appelant directement.</p>
 */
@Component
public class TachesRendezVous {

    private static final Logger log = LoggerFactory.getLogger(TachesRendezVous.class);

    private final RdvRepository rdvs;
    private final ParametreAtelierRepository parametres;
    private final AvisRendezVous avis;
    private final TransactionTemplate transaction;
    private final Clock horloge;

    public TachesRendezVous(RdvRepository rdvs, ParametreAtelierRepository parametres,
                            AvisRendezVous avis, PlatformTransactionManager transactions,
                            Clock horloge) {
        this.rdvs = rdvs;
        this.parametres = parametres;
        this.avis = avis;
        this.transaction = new TransactionTemplate(transactions);
        this.horloge = horloge;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void executer() {
        envoyerLesRappels();
    }

    /** Rend le nombre de rappels envoyés par ce passage. */
    public int envoyerLesRappels() {
        ParametreAtelier p = parametres.courants();
        if (!p.rappelActif()) {
            return 0;
        }
        Instant maintenant = horloge.instant();
        int envoyes = 0;
        for (Rdv candidat : rdvs.rappelsDus(maintenant, maintenant.plus(p.delaiRappel()))) {
            try {
                Boolean envoye = transaction.execute(statut -> {
                    if (rdvs.reserverRappel(candidat.getId(), maintenant) == 0) {
                        return false;
                    }
                    avis.rappel(candidat);
                    return true;
                });
                if (Boolean.TRUE.equals(envoye)) {
                    envoyes++;
                }
            } catch (RuntimeException e) {
                log.warn("Rappel du rendez-vous {} non traité, repris au passage suivant",
                        candidat.getNumero(), e);
            }
        }
        return envoyes;
    }
}
