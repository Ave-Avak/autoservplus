package be.autoservplus.identite.service;

import be.autoservplus.identite.domain.ChangementChamp;
import be.autoservplus.identite.domain.ModificationCompte;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.ModificationCompteRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Historique des modifications d'un compte (V57).
 *
 * <p>Écrit dans la transaction de la modification, jamais après : une adresse changée
 * sans sa ligne d'historique est précisément ce que la table existe pour empêcher.
 * {@code MANDATORY} le garantit, un appel hors transaction échoue au lieu d'écrire une
 * trace orpheline.</p>
 */
@Component
public class HistoriqueModificationsCompte {

    private final ModificationCompteRepository modifications;
    private final Clock horloge;

    public HistoriqueModificationsCompte(ModificationCompteRepository modifications, Clock horloge) {
        this.modifications = modifications;
        this.horloge = horloge;
    }

    /**
     * @param auteur le titulaire, ou l'administrateur qui agit à sa demande
     * @param motif  la demande du membre, obligatoire quand l'auteur est un administrateur
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void consigner(Utilisateur compte, List<ChangementChamp> changements,
                          Utilisateur auteur, String motif) {
        Instant maintenant = Instant.now(horloge);
        modifications.saveAll(changements.stream()
                .map(changement -> new ModificationCompte(compte, changement, maintenant, auteur, motif))
                .toList());
    }

    @Transactional(readOnly = true)
    public List<ModificationCompte> historiqueDe(Utilisateur compte) {
        return modifications.historiqueDe(compte.getId());
    }

    /**
     * Anonymisation (F23) : voir {@link ModificationCompteRepository#effacerValeurs}. Le
     * motif devient {@link Utilisateur#MARQUEUR_ANONYME}, une marque d'absence et non une
     * valeur inventée, que la fiche traduit à l'affichage.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int effacerValeurs(Utilisateur compte) {
        return modifications.effacerValeurs(compte.getId(), Utilisateur.MARQUEUR_ANONYME);
    }
}
