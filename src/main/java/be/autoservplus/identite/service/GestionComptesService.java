package be.autoservplus.identite.service;

import be.autoservplus.common.exception.RefusTraduit;
import be.autoservplus.common.exception.RegleMetierException;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.identite.domain.HistoriqueStatutUtilisateur;
import be.autoservplus.identite.domain.StatutUtilisateur;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.HistoriqueStatutUtilisateurRepository;
import be.autoservplus.identite.repository.UtilisateurRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Gestion des comptes depuis le back-office (CdC 5.2.3).
 *
 * <p><b>La suspension n a rien a faire du chemin d authentification.</b>
 * {@code UtilisateurDetailsService} construit deja le {@code UserDetails} avec
 * {@code .disabled(statut != ACTIF)} : ecrire {@code SUSPENDU} suffit a refuser toute
 * nouvelle connexion. Ce service ecrit donc le statut, le journalise, et rien de
 * plus — aucune garde a ajouter ailleurs.</p>
 *
 * <p><b>Ce qu il ne fait PAS, et qu il faut savoir dire.</b> {@code disabled} est lu
 * dans {@code loadUserByUsername}, donc <b>a l authentification seulement</b> : une
 * session deja ouverte survit a la suspension jusqu a son expiration. C est la limite
 * que le registre de sessions traite, et c est la meme cause que la session non
 * coupee au changement d adresse.</p>
 *
 * <p><b>Les quatre gardes, et pourquoi chacune.</b></p>
 * <ul>
 *   <li><b>Pas d auto-suspension</b> : un administrateur qui se suspend se ferme la
 *       porte, et personne d autre ne peut necessairement la rouvrir. Le refus est
 *       plus utile que la liberte.</li>
 *   <li><b>Il doit rester un super-administrateur actif</b> : suspendre le dernier
 *       rendrait la creation d administrateurs definitivement impossible — un systeme
 *       dont on ne peut plus sortir sans acces a la base.</li>
 *   <li><b>Un administrateur n agit pas sur un autre compte privilegie</b> : les
 *       pouvoirs sur le back-office sont exclusifs au super-administrateur. Sans
 *       cela, deux administrateurs pourraient se suspendre mutuellement.</li>
 *   <li><b>Le motif est obligatoire a la suspension</b> : c est ce que le journal
 *       aura a montrer des mois plus tard, quand le geste ne sera plus dans la
 *       memoire de personne.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasRole('ADMINISTRATEUR')")
public class GestionComptesService {

    private static final Logger JOURNAL = LoggerFactory.getLogger(GestionComptesService.class);

    private final UtilisateurRepository utilisateurs;
    private final HistoriqueStatutUtilisateurRepository historique;
    private final AuteurCourant auteurCourant;
    private final Clock horloge;

    public GestionComptesService(UtilisateurRepository utilisateurs,
                                 HistoriqueStatutUtilisateurRepository historique,
                                 AuteurCourant auteurCourant, Clock horloge) {
        this.utilisateurs = utilisateurs;
        this.historique = historique;
        this.auteurCourant = auteurCourant;
        this.horloge = horloge;
    }

    // --- lectures ---------------------------------------------------------------------

    public List<Utilisateur> membres(String filtre) {
        String recherche = filtre == null || filtre.isBlank() ? null : filtre.strip();
        return utilisateurs.rechercherMembres(recherche);
    }

    /** Comptes du back-office : les deux natures privilegiees, jamais les membres. */
    @PreAuthorize("hasRole('SUPER_ADMINISTRATEUR')")
    public List<Utilisateur> comptesPrivilegies() {
        return utilisateurs.findByTypeUtilisateurInOrderByNomAscPrenomAsc(
                List.of(TypeUtilisateur.ADMINISTRATEUR, TypeUtilisateur.SUPER_ADMINISTRATEUR));
    }

    public Utilisateur parReference(UUID reference) {
        return utilisateurs.findByReference(reference)
                .orElseThrow(() -> new RessourceIntrouvableException("Compte", reference.toString()));
    }

    public List<HistoriqueStatutUtilisateur> historiqueDe(Utilisateur compte) {
        return historique.historiqueDe(compte.getId());
    }

    // --- ecritures --------------------------------------------------------------------

    /**
     * Suspend un compte : il ne pourra plus se connecter.
     *
     * @param motif obligatoire — voir la garde correspondante
     * @throws RegleMetierException si une des quatre gardes s y oppose
     */
    @Transactional
    public Utilisateur suspendre(UUID reference, String motif) {
        Utilisateur cible = parReference(reference);
        Utilisateur auteur = exigerAuteur();

        exigerPouvoirSurLaCible(auteur, cible);
        exigerNonSoiMeme(auteur, cible);

        if (motif == null || motif.isBlank()) {
            throw new RefusTraduit(
                    "admin.comptes.erreur.motif",
                    "Indiquez le motif de la suspension : il figurera au journal.");
        }
        if (cible.getStatut() == StatutUtilisateur.SUSPENDU) {
            throw new RefusTraduit(
                    "admin.comptes.erreur.deja-suspendu",
                    "Ce compte est deja suspendu.");
        }
        if (cible.getStatut() == StatutUtilisateur.SUPPRIME) {
            throw new RefusTraduit(
                    "admin.comptes.erreur.supprime",
                    "Ce compte est supprime : il ne se suspend plus.");
        }

        // Le dernier super-administrateur actif ne se suspend pas : le decompte
        // EXCLUT la cible, donc il rend ce qu il resterait apres le geste.
        if (cible.estSuperAdministrateur()
                && utilisateurs.compterSuperAdministrateursActifsSauf(cible.getId()) == 0) {
            throw new RefusTraduit(
                    "admin.comptes.erreur.dernier-super-admin",
                    "C est le dernier super-administrateur actif : le suspendre fermerait "
                            + "definitivement la gestion des comptes.");
        }

        return transiter(cible, StatutUtilisateur.SUSPENDU, auteur, motif.strip());
    }

    /** Reactive un compte suspendu. Le motif n a pas d objet ici. */
    @Transactional
    public Utilisateur reactiver(UUID reference) {
        Utilisateur cible = parReference(reference);
        Utilisateur auteur = exigerAuteur();

        exigerPouvoirSurLaCible(auteur, cible);

        if (cible.getStatut() != StatutUtilisateur.SUSPENDU) {
            throw new RefusTraduit(
                    "admin.comptes.erreur.non-suspendu",
                    "Seul un compte suspendu se reactive.");
        }

        return transiter(cible, StatutUtilisateur.ACTIF, auteur, null);
    }

    private Utilisateur transiter(Utilisateur cible, StatutUtilisateur vers,
                                  Utilisateur auteur, String motif) {
        StatutUtilisateur avant = cible.getStatut();
        cible.setStatut(vers);

        // Meme transaction que la transition : un journal ecrit apres coup pourrait
        // manquer si la transaction echoue, et un statut sans trace est precisement
        // ce que cette table existe pour empecher.
        historique.save(new HistoriqueStatutUtilisateur(
                cible, avant, vers, Instant.now(horloge), auteur, motif));

        JOURNAL.info("Compte {} : {} -> {} par {}",
                cible.getReference(), avant, vers, auteur.getReference());
        return cible;
    }

    // --- gardes -----------------------------------------------------------------------

    /**
     * L auteur doit etre identifiable. Contrairement aux journaux d intervention, dont
     * l auteur peut etre nul parce qu une tache planifiee les alimente, une suspension
     * est toujours une decision humaine : une trace sans auteur y serait un defaut.
     */
    private Utilisateur exigerAuteur() {
        Utilisateur auteur = auteurCourant.resoudre();
        if (auteur == null) {
            throw new IllegalStateException(
                    "Aucun auteur authentifie : une decision sur un compte doit etre imputable.");
        }
        return auteur;
    }

    /**
     * Un administrateur ordinaire n agit que sur des membres ; les comptes du
     * back-office sont reserves au super-administrateur.
     */
    private void exigerPouvoirSurLaCible(Utilisateur auteur, Utilisateur cible) {
        if (cible.estAdministrateur() && !auteur.estSuperAdministrateur()) {
            throw new RefusTraduit(
                    "admin.comptes.erreur.pouvoir",
                    "Seul un super-administrateur agit sur un compte du back-office.");
        }
    }

    /**
     * Personne ne se suspend soi-meme. Un super-administrateur qui le ferait se
     * fermerait la porte, et rien ne garantit qu un autre puisse la rouvrir.
     */
    private void exigerNonSoiMeme(Utilisateur auteur, Utilisateur cible) {
        if (auteur.getId().equals(cible.getId())) {
            throw new RefusTraduit(
                    "admin.comptes.erreur.soi-meme",
                    "Vous ne pouvez pas suspendre votre propre compte.");
        }
    }
}
