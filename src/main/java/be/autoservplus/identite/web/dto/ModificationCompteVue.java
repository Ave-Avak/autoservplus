package be.autoservplus.identite.web.dto;

import be.autoservplus.identite.domain.ChampCompte;
import be.autoservplus.identite.domain.ModificationCompte;
import be.autoservplus.identite.domain.Utilisateur;

import java.time.Instant;

/**
 * Une ligne de l'historique des modifications d'un compte, pour la fiche du
 * back-office. L'auteur suit la règle de {@link EvenementSecuriteVue} : le titulaire,
 * un compte disparu ({@code auteur} nul), ou un administrateur nommé.
 *
 * @param motifAnonymise le motif a été remplacé par {@link Utilisateur#MARQUEUR_ANONYME}
 *                       à la suppression du compte : l'écran affiche un libellé traduit
 */
public record ModificationCompteVue(Instant horodatage, ChampCompte champ,
                                    String valeurAvant, String valeurApres,
                                    boolean parTitulaire, String auteur, String motif,
                                    boolean motifAnonymise) {

    public static ModificationCompteVue de(ModificationCompte modification) {
        Utilisateur auteur = modification.getAuteur();
        boolean parTitulaire = modification.parTitulaire();
        return new ModificationCompteVue(
                modification.getHorodatage(),
                modification.getChamp(),
                modification.getValeurAvant(),
                modification.getValeurApres(),
                parTitulaire,
                auteur == null || parTitulaire ? null : auteur.nomComplet(),
                modification.getMotif(),
                Utilisateur.MARQUEUR_ANONYME.equals(modification.getMotif()));
    }
}
