package be.autoservplus.reservation.service;

import be.autoservplus.communication.service.DetailsRdvCourriel;
import be.autoservplus.communication.service.ServiceCourriel;
import be.autoservplus.identite.domain.StatutUtilisateur;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.notification.domain.TypeNotification;
import be.autoservplus.notification.service.NotificationService;
import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.domain.StatutRdv;
import be.autoservplus.reservation.repository.ParametreAtelierRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Avis émis par les gestes du membre et par la tâche planifiée : accusé de réception et
 * avis au garage d'une nouvelle demande, rappel avant le rendez-vous, annulation par le
 * membre. Les transitions décidées par le garage restent dans {@link AdminRdvService}.
 *
 * <p>Appelé dans la transaction du geste. Les courriels partent après sa validation
 * ({@code EnvoiApresCommit}), les notifications s'écrivent avec elle. Une erreur de
 * rédaction est journalisée sans remonter : la demande enregistrée compte plus que son
 * avis, et un membre dont la réservation échouerait faute de texte devrait tout
 * ressaisir.</p>
 */
@Component
public class AvisRendezVous {

    private static final Logger log = LoggerFactory.getLogger(AvisRendezVous.class);

    private final ServiceCourriel courriel;
    private final NotificationService notifications;
    private final UtilisateurRepository utilisateurs;
    private final ParametreAtelierRepository parametres;

    public AvisRendezVous(ServiceCourriel courriel, NotificationService notifications,
                          UtilisateurRepository utilisateurs, ParametreAtelierRepository parametres) {
        this.courriel = courriel;
        this.notifications = notifications;
        this.utilisateurs = utilisateurs;
        this.parametres = parametres;
    }

    /**
     * Accusé au membre, courriel à l'adresse de l'atelier et notification à chaque
     * administrateur actif. Un rendez-vous confirmé d'office l'est dit dans les trois.
     */
    public void nouvelleDemande(Rdv rdv) {
        boolean confirme = rdv.getStatut() == StatutRdv.CONFIRME;
        DetailsRdvCourriel details = detailsPour(rdv);
        sansEchouer(rdv, () -> courriel.envoyerAccuseDemandeRdv(rdv.getMembre(), details, confirme));
        sansEchouer(rdv, () -> courriel.envoyerAvisNouvelleDemandeAuGarage(details,
                rdv.getMembre().nomComplet(), confirme, "/admin/rendez-vous/" + rdv.getReference()));
        List<Utilisateur> administrateurs = utilisateurs.findByTypeUtilisateurAndStatut(
                TypeUtilisateur.ADMINISTRATEUR, StatutUtilisateur.ACTIF);
        if (administrateurs.isEmpty()) {
            log.warn("Demande {} : aucun administrateur actif à notifier.", rdv.getNumero());
        }
        administrateurs.forEach(administrateur -> sansEchouer(rdv,
                () -> notifications.deposer(administrateur, TypeNotification.RDV_DEMANDE, rdv.getNumero())));
    }

    public void rappel(Rdv rdv) {
        sansEchouer(rdv, () -> courriel.envoyerRappelRdv(rdv.getMembre(), detailsPour(rdv)));
    }

    public void annulationParLeMembre(Rdv rdv) {
        sansEchouer(rdv, () -> courriel.envoyerAnnulationParLeMembre(rdv.getMembre(), detailsPour(rdv)));
    }

    private DetailsRdvCourriel detailsPour(Rdv rdv) {
        return new DetailsRdvCourriel(rdv.getNumero(), rdv.getDebut().atZone(parametres.courants().zone()));
    }

    private static void sansEchouer(Rdv rdv, Runnable avis) {
        try {
            avis.run();
        } catch (RuntimeException e) {
            log.warn("Avis du rendez-vous {} non émis, le geste reste enregistré", rdv.getNumero(), e);
        }
    }
}
