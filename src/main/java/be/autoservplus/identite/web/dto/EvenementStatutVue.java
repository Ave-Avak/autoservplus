package be.autoservplus.identite.web.dto;

import be.autoservplus.identite.domain.HistoriqueStatutUtilisateur;
import be.autoservplus.identite.domain.StatutUtilisateur;

import java.time.Instant;

/**
 * Une ligne du journal des statuts, telle que l ecran la rend (CdC 5.2.3).
 *
 * <p>L auteur est rendu par son <b>nom complet</b> et non par son adresse : le journal
 * est lu par des collegues, a qui un nom parle, et l adresse d un administrateur n a
 * pas a etre recopiee sur un ecran de plus. {@code auteur} vaut {@code null} lorsque le
 * compte auteur a disparu — la cle etrangere est en {@code ON DELETE SET NULL}, la
 * trace survivant a son auteur.</p>
 */
public record EvenementStatutVue(Instant horodatage, StatutUtilisateur statutAvant,
                                 StatutUtilisateur statutApres, String auteur, String motif) {

    public static EvenementStatutVue de(HistoriqueStatutUtilisateur ligne) {
        return new EvenementStatutVue(
                ligne.getHorodatage(),
                ligne.getStatutAvant(),
                ligne.getStatutApres(),
                ligne.getAuteur() == null ? null : ligne.getAuteur().nomComplet(),
                ligne.getMotif());
    }
}
