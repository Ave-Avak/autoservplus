package be.autoservplus.reservation.service;

import be.autoservplus.reservation.domain.ParametreAtelier;
import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.repository.ParametreAtelierRepository;
import be.autoservplus.reservation.repository.RdvRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;

/**
 * Tâche planifiée des rendez-vous, une fois par minute comme
 * {@code ExpirationCommandesJob} : expiration des demandes échues, relance au garage à
 * mi-délai, rappel avant un rendez-vous confirmé.
 *
 * <p>Une transaction par rendez-vous, pour qu'un échec n'emporte pas les autres et que
 * chaque courriel parte après la validation de sa propre écriture. Rappel et relance
 * sont réservés par une mise à jour conditionnelle avant l'envoi : une seconde
 * instance, ou un passage repris après une panne, lit la réservation et passe son tour.
 * L'expiration passe par l'entité et son verrou de version : une confirmation du garage
 * validée entre-temps l'emporte. Un rendez-vous confirmé n'expire jamais. Les dates
 * viennent de l'horloge injectée, chaque méthode se teste en l'appelant directement.</p>
 */
@Component
public class TachesRendezVous {

    private static final Logger log = LoggerFactory.getLogger(TachesRendezVous.class);

    private final RdvRepository rdvs;
    private final ParametreAtelierRepository parametres;
    private final AvisRendezVous avis;
    private final ApplicationEventPublisher evenements;
    private final TransactionTemplate transaction;
    private final Clock horloge;

    public TachesRendezVous(RdvRepository rdvs, ParametreAtelierRepository parametres,
                            AvisRendezVous avis, ApplicationEventPublisher evenements,
                            PlatformTransactionManager transactions, Clock horloge) {
        this.rdvs = rdvs;
        this.parametres = parametres;
        this.avis = avis;
        this.evenements = evenements;
        this.transaction = new TransactionTemplate(transactions);
        this.horloge = horloge;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void executer() {
        expirerLesDemandes();
        relancerLeGarage();
        envoyerLesRappels();
    }

    /** Rend le nombre de demandes expirées par ce passage. */
    public int expirerLesDemandes() {
        Instant maintenant = horloge.instant();
        int expirees = 0;
        for (Rdv candidat : rdvs.demandesEchues(maintenant)) {
            try {
                Boolean expiree = transaction.execute(statut -> {
                    Rdv rdv = rdvs.findById(candidat.getId()).orElse(null);
                    if (rdv == null || !rdv.peutExpirer(maintenant)) {
                        // Tranchée par le garage entre la recherche et la relecture.
                        return false;
                    }
                    rdv.expirer(maintenant);
                    Rdv enregistre = rdvs.saveAndFlush(rdv);
                    avis.expiration(enregistre);
                    evenements.publishEvent(new RdvStatutModifieEvent(enregistre.getReference()));
                    return true;
                });
                if (Boolean.TRUE.equals(expiree)) {
                    expirees++;
                    log.info("Demande {} expirée sans réponse du garage", candidat.getNumero());
                }
            } catch (OptimisticLockingFailureException e) {
                log.info("Demande {} tranchée par le garage pendant son expiration", candidat.getNumero());
            } catch (RuntimeException e) {
                log.warn("Expiration de la demande {} non traitée, reprise au passage suivant",
                        candidat.getNumero(), e);
            }
        }
        return expirees;
    }

    /** Rend le nombre de relances envoyées par ce passage. */
    public int relancerLeGarage() {
        Instant maintenant = horloge.instant();
        int relancees = 0;
        for (Rdv candidat : rdvs.relancesDues(maintenant)) {
            try {
                Boolean relancee = transaction.execute(statut -> {
                    if (rdvs.reserverRelance(candidat.getId(), maintenant) == 0) {
                        return false;
                    }
                    Rdv rdv = rdvs.findById(candidat.getId()).orElseThrow();
                    avis.relance(rdv);
                    return true;
                });
                if (Boolean.TRUE.equals(relancee)) {
                    relancees++;
                }
            } catch (RuntimeException e) {
                log.warn("Relance de la demande {} non traitée, reprise au passage suivant",
                        candidat.getNumero(), e);
            }
        }
        return relancees;
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
