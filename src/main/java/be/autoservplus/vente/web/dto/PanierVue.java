package be.autoservplus.vente.web.dto;

import be.autoservplus.reservation.service.support.FormatageRdv;
import be.autoservplus.vente.domain.LignePanier;
import be.autoservplus.vente.domain.Panier;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Vue du panier destinee au membre proprietaire (F13).
 *
 * <p><b>RM-30</b> : le detail HTVA / TVA / TVAC est expose par ligne et en totaux,
 * pre-formate en euros via {@code FormatageRdv} (convention du projet : le DTO
 * connait la locale monetaire, le template non). Les montants viennent des valeurs
 * <b>figees</b> des lignes, jamais du catalogue courant.</p>
 *
 * <p><b>RM-28</b> : une piece devenue inactive apres son ajout reste listee — le
 * drapeau {@code pieceActive} de la ligne et {@code contientPieceInactive} du
 * panier permettent au recapitulatif de la signaler.</p>
 */
public record PanierVue(
        List<LignePanierVue> lignes,
        int nombreArticles,
        String totalHtva,
        String totalTva,
        String totalTvac,
        boolean estVide,
        boolean contientPieceInactive) {

    public static PanierVue de(Panier panier) {
        return new PanierVue(
                panier.getLignes().stream().map(LignePanierVue::de).toList(),
                panier.nombreArticles(),
                FormatageRdv.euros(panier.totalHtva()),
                FormatageRdv.euros(panier.totalTva()),
                FormatageRdv.euros(panier.totalTvac()),
                panier.estVide(),
                panier.getLignes().stream().anyMatch(l -> !l.getPiece().isActif()));
    }

    /** Panier jamais cree : la lecture ne provoque aucune ecriture, elle rend du vide. */
    public static PanierVue vide() {
        String zero = FormatageRdv.euros(BigDecimal.ZERO);
        return new PanierVue(List.of(), 0, zero, zero, zero, true, false);
    }

    public record LignePanierVue(
            Long id,
            UUID referencePiece,
            String libelle,
            short quantite,
            String prixUnitaireHtva,
            /** Taux fige a l ajout, en pourcent sans decimales inutiles (« 6 », « 21 »). */
            String tauxTva,
            String totalHtva,
            String totalTva,
            String totalTvac,
            boolean pieceActive) {

        public static LignePanierVue de(LignePanier ligne) {
            return new LignePanierVue(
                    ligne.getId(),
                    ligne.getPiece().getReference(),
                    ligne.getLibelleFige(),
                    ligne.getQuantite(),
                    FormatageRdv.euros(ligne.getPrixUnitaireHtva()),
                    ligne.getTauxTva().stripTrailingZeros().toPlainString(),
                    FormatageRdv.euros(ligne.totalHtva()),
                    FormatageRdv.euros(ligne.totalTva()),
                    FormatageRdv.euros(ligne.totalTvac()),
                    ligne.getPiece().isActif());
        }
    }
}
