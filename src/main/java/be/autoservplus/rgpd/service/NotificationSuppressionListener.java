package be.autoservplus.rgpd.service;

import be.autoservplus.communication.service.DetailsSuppressionCompteCourriel;
import be.autoservplus.communication.service.ServiceCourriel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Confirme au membre la suppression de son compte (F23).
 *
 * <p>{@code AFTER_COMMIT}, comme les autres notifications du projet : un courriel
 * n annonce jamais un effacement qu un rollback pourrait encore annuler. La
 * suppression touche ici plusieurs tables et un balayage natif — si quoi que ce soit
 * echoue, l evenement meurt avec la transaction et personne n est prevenu a tort.</p>
 *
 * <p><b>Pas de {@code @Transactional}</b>, contrairement aux autres listeners du
 * projet : ceux-la rechargent une entite par sa reference et ont besoin d une
 * session. Celui-ci n a rien a lire — l evenement porte deja l adresse et le prenom,
 * captures avant l ecrasement. Ouvrir une transaction pour ne rien interroger serait
 * du bruit, et un rechargement ne rendrait de toute facon que le jeton anonyme.</p>
 *
 * <p>Exceptions du fournisseur avalees apres journal : le compte est supprime, l acces
 * revoque, et la personne le constate a l ecran. Un courriel manquant ne doit pas
 * laisser croire que le droit n a pas ete exerce — c est d autant plus vrai ici que
 * l operation est irreversible et qu il n existe plus de compte pour la rejouer.</p>
 */
@Component
public class NotificationSuppressionListener {

    private static final Logger JOURNAL =
            LoggerFactory.getLogger(NotificationSuppressionListener.class);

    private final ServiceCourriel courriel;

    public NotificationSuppressionListener(ServiceCourriel courriel) {
        this.courriel = courriel;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void surCompteSupprime(CompteSupprimeEvent evenement) {
        try {
            courriel.envoyerConfirmationSuppressionCompte(new DetailsSuppressionCompteCourriel(
                    evenement.adresseEmail(), evenement.prenom()));
        } catch (RuntimeException e) {
            // La reference, jamais l adresse : le journal ne doit pas recreer la
            // donnee que la suppression vient d effacer.
            JOURNAL.warn("Confirmation de suppression non envoyee pour le compte {} : {}",
                    evenement.reference(), e.getMessage());
        }
    }
}
