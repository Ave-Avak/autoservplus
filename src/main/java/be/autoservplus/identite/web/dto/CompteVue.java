package be.autoservplus.identite.web.dto;

import be.autoservplus.identite.domain.Langue;
import be.autoservplus.identite.domain.StatutUtilisateur;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;

import java.time.Instant;
import java.util.UUID;

/**
 * Un compte tel que le back-office l affiche (CdC 5.2.3).
 *
 * <p>L entite ne franchit pas la couche service. Ce qui est <b>volontairement absent</b>
 * importe autant que ce qui est present : ni empreinte de mot de passe, ni jeton de
 * verification, ni jeton de changement d adresse. Un ecran de gestion n a aucun usage
 * de secrets d authentification, et les y faire transiter suffirait a les exposer au
 * rendu.</p>
 *
 * <p>Du second facteur, l ecran ne recoit que deux booleens : s il est actif, et si un
 * enrolement est commence. Ni secret, ni version de cle, ni dernier pas.</p>
 *
 * <p>Les coordonnées (téléphone, adresse, langue) servent la fiche d'un membre, où
 * l'administrateur les corrige à sa demande.</p>
 *
 * @param suspendu   etat derive, pour que le gabarit n ait pas a comparer une enumeration
 * @param modifiable membre non supprimé, dont les coordonnées se modifient depuis la fiche
 */
public record CompteVue(UUID reference, String nom, String prenom, String email,
                        TypeUtilisateur type, StatutUtilisateur statut, boolean suspendu,
                        boolean emailVerifie, Instant derniereConnexion,
                        boolean secondFacteurActif, boolean enrolementEnCours,
                        boolean motDePasseADefinir, String telephone, String rue,
                        String numeroRue, String codePostal, String localite, String pays,
                        Langue langue, boolean membre, boolean modifiable) {

    public static CompteVue de(Utilisateur compte) {
        return new CompteVue(
                compte.getReference(),
                compte.getNom(),
                compte.getPrenom(),
                compte.getEmail(),
                compte.getTypeUtilisateur(),
                compte.getStatut(),
                compte.getStatut() == StatutUtilisateur.SUSPENDU,
                compte.isEmailVerifie(),
                compte.getDerniereConnexion(),
                compte.secondFacteurActif(),
                compte.enrolementEnCours(),
                compte.motDePasseADefinir(),
                compte.getTelephone(),
                compte.getRue(),
                compte.getNumeroRue(),
                compte.getCodePostal(),
                compte.getLocalite(),
                compte.getPays(),
                compte.getLangue(),
                compte.getTypeUtilisateur() == TypeUtilisateur.MEMBRE,
                compte.getTypeUtilisateur() == TypeUtilisateur.MEMBRE
                        && compte.getStatut() != StatutUtilisateur.SUPPRIME);
    }

    public String nomComplet() {
        return prenom + " " + nom;
    }
}
