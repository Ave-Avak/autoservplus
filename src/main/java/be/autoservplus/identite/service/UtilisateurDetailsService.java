package be.autoservplus.identite.service;

import be.autoservplus.identite.domain.StatutUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Fait le pont entre les comptes AutoServ+ et Spring Security.
 *
 * <p>Le message d erreur est volontairement identique que l adresse existe ou non :
 * une reponse differenciee permettrait d enumerer les comptes de la plateforme.</p>
 *
 * <p>Verrouillage et désactivation sont lus ici, mais contrôlés après le mot de passe
 * (voir {@code SecuriteConfig.fournisseurAuthentification}).</p>
 */
@Service
@Transactional(readOnly = true)
public class UtilisateurDetailsService implements UserDetailsService {

    private static final String MESSAGE_GENERIQUE = "Identifiants incorrects.";

    private final UtilisateurRepository repository;
    private final Clock horloge;

    public UtilisateurDetailsService(UtilisateurRepository repository, Clock horloge) {
        this.repository = repository;
        this.horloge = horloge;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        Utilisateur membre = repository.findByEmail(AdresseCourriel.normaliser(email))
                .orElseThrow(() -> new UsernameNotFoundException(MESSAGE_GENERIQUE));

        return User.builder()
                .username(membre.getEmail())
                .password(membre.getMotDePasseHache())
                .roles(membre.getTypeUtilisateur().name())
                .accountLocked(membre.estVerrouille(Instant.now(horloge)))
                .disabled(membre.getStatut() != StatutUtilisateur.ACTIF)
                .build();
    }

    /**
     * Remplace l'empreinte de la forme brute d'un mot de passe par celle de sa forme
     * normalisée, après une connexion qui l'a prouvé. Rien d'autre ne change : c'est le
     * même mot de passe, ni changé ni réinitialisé.
     */
    @Transactional
    public void normaliserEmpreinte(String email, String empreinte) {
        repository.findByEmail(email)
                .ifPresent(membre -> membre.remplacerEmpreinte(empreinte));
    }
}