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
        Utilisateur membre = repository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UsernameNotFoundException(MESSAGE_GENERIQUE));

        return User.builder()
                .username(membre.getEmail())
                .password(membre.getMotDePasseHache())
                .roles(membre.getTypeUtilisateur().name())
                .accountLocked(membre.estVerrouille(Instant.now(horloge)))
                .disabled(membre.getStatut() != StatutUtilisateur.ACTIF)
                .build();
    }
}