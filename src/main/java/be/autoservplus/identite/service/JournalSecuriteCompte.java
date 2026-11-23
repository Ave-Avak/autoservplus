package be.autoservplus.identite.service;

import be.autoservplus.identite.domain.EvenementSecuriteCompte;
import be.autoservplus.identite.domain.TypeEvenementSecurite;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.EvenementSecuriteCompteRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * Consigne les événements de sécurité d'un compte.
 *
 * <p>{@code MANDATORY} : l'événement s'écrit dans la transaction du geste qu'il trace.
 * Écrit après coup, il pourrait manquer si le geste échoue, ou survivre à un geste
 * annulé, ce qui serait pire.</p>
 */
@Component
public class JournalSecuriteCompte {

    private final EvenementSecuriteCompteRepository evenements;
    private final Clock horloge;

    public JournalSecuriteCompte(EvenementSecuriteCompteRepository evenements, Clock horloge) {
        this.evenements = evenements;
        this.horloge = horloge;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void consigner(Utilisateur compte, TypeEvenementSecurite type, Utilisateur auteur) {
        evenements.save(new EvenementSecuriteCompte(compte, type, horloge.instant(), auteur));
    }

    @Transactional(readOnly = true)
    public List<EvenementSecuriteCompte> evenementsDe(Utilisateur compte) {
        return evenements.evenementsDe(compte.getId());
    }
}
