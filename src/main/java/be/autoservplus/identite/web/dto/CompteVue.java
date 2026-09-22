package be.autoservplus.identite.web.dto;

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
 * verification, ni jeton de changement d adresse — un ecran de gestion n a aucun usage
 * de secrets d authentification, et les y faire transiter suffirait a les exposer au
 * rendu.</p>
 *
 * @param suspendu etat derive, pour que le gabarit n ait pas a comparer une enumeration
 */
public record CompteVue(UUID reference, String nom, String prenom, String email,
                        TypeUtilisateur type, StatutUtilisateur statut, boolean suspendu,
                        boolean emailVerifie, Instant derniereConnexion) {

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
                compte.getDerniereConnexion());
    }

    public String nomComplet() {
        return prenom + " " + nom;
    }
}
