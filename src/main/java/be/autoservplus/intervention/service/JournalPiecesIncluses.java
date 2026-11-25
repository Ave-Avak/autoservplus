package be.autoservplus.intervention.service;

import be.autoservplus.identite.service.AuteurCourant;
import be.autoservplus.intervention.domain.ChangementPieceIncluse;
import be.autoservplus.intervention.domain.HistoriquePieceIncluse;
import be.autoservplus.intervention.domain.Intervention;
import be.autoservplus.intervention.repository.HistoriquePieceIncluseRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Écrit au journal les changements de pièces incluses que le domaine rend, dans la
 * transaction de l'appelant : un changement sans trace ne peut pas être validé seul.
 * L'auteur est l'administrateur connecté, l'instant celui de l'horloge injectée.
 */
@Component
public class JournalPiecesIncluses {

    private final HistoriquePieceIncluseRepository historiques;
    private final AuteurCourant auteurCourant;
    private final Clock horloge;

    public JournalPiecesIncluses(HistoriquePieceIncluseRepository historiques, AuteurCourant auteurCourant,
                                 Clock horloge) {
        this.historiques = historiques;
        this.auteurCourant = auteurCourant;
        this.horloge = horloge;
    }

    public void ecrire(Intervention intervention, List<ChangementPieceIncluse> changements) {
        if (changements.isEmpty()) {
            return;
        }
        Instant maintenant = horloge.instant();
        var auteur = auteurCourant.resoudre();
        historiques.saveAll(changements.stream()
                .map(changement -> new HistoriquePieceIncluse(intervention, changement, maintenant, auteur))
                .toList());
    }
}
