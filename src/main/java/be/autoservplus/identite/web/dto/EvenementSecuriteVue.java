package be.autoservplus.identite.web.dto;

import be.autoservplus.identite.domain.EvenementSecuriteCompte;
import be.autoservplus.identite.domain.TypeEvenementSecurite;
import be.autoservplus.identite.domain.Utilisateur;

import java.time.Instant;

/**
 * Une ligne du journal de securite d un compte. Meme regle que
 * {@link EvenementStatutVue} pour l auteur : son nom, jamais son adresse. Trois cas a
 * l ecran : le titulaire ({@code parTitulaire}), le systeme ({@code auteur} nul), ou un
 * autre compte nomme.
 */
public record EvenementSecuriteVue(Instant horodatage, TypeEvenementSecurite type,
                                   boolean parTitulaire, String auteur) {

    public static EvenementSecuriteVue de(EvenementSecuriteCompte evenement) {
        Utilisateur auteur = evenement.getAuteur();
        boolean parTitulaire = auteur != null
                && auteur.getId().equals(evenement.getUtilisateur().getId());
        return new EvenementSecuriteVue(
                evenement.getHorodatage(),
                evenement.getType(),
                parTitulaire,
                auteur == null || parTitulaire ? null : auteur.nomComplet());
    }
}
