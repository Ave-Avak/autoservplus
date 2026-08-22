package be.autoservplus.catalogue.service.dto;

import be.autoservplus.catalogue.domain.HistoriqueModificationCatalogue;
import be.autoservplus.identite.domain.Utilisateur;

import java.time.Instant;

/**
 * Une ligne de l historique des modifications d un element du catalogue (A2, A5) :
 * le « quoi » ({@code champ}, {@code valeurAvant} vers {@code valeurApres}), le
 * « quand » ({@code horodatage}) et le « qui » ({@code auteur}).
 *
 * <p>{@code champ} porte le nom technique tel qu il est stocke — stable, donc
 * requetable. Sa traduction pour l affichage releve de la presentation, pas du
 * journal.</p>
 *
 * <p>{@code auteur} est le nom lisible de l utilisateur, jamais l entite : rien du
 * modele identite ne franchit la couche service. Il vaut {@code null} quand aucun
 * auteur n est identifiable — compte supprime depuis, ou traitement systeme.</p>
 */
public record ModificationCatalogueVue(
        String champ,
        String valeurAvant,
        String valeurApres,
        Instant horodatage,
        String auteur) {

    public static ModificationCatalogueVue de(HistoriqueModificationCatalogue historique) {
        Utilisateur auteur = historique.getAuteur();
        return new ModificationCatalogueVue(
                historique.getChampModifie(),
                historique.getValeurAvant(),
                historique.getValeurApres(),
                historique.getHorodatage(),
                auteur == null ? null : "%s %s".formatted(auteur.getPrenom(), auteur.getNom()));
    }
}
