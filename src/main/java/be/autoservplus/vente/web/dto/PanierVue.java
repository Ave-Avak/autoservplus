package be.autoservplus.vente.web.dto;

import be.autoservplus.reservation.service.support.FormatageRdv;
import be.autoservplus.i18n.LanguesSupportees;
import org.springframework.context.i18n.LocaleContextHolder;
import be.autoservplus.vente.domain.LignePanier;
import be.autoservplus.vente.domain.Panier;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Vue du panier destinee au membre proprietaire (F13).
 *
 * <p><b>RM-30</b> : les prix sont affichés taxe comprise, ligne par ligne, et les totaux
 * donnent le hors TVA, la TVA par taux et le total taxe comprise. Une ligne n'a ni base ni
 * TVA propres. Les montants sont pré-formatés en euros via {@code FormatageRdv} et viennent
 * des valeurs <b>figées</b> des lignes, jamais du catalogue courant.</p>
 *
 * <p>Contrainte de <b>F13</b> : une piece devenue inactive apres son ajout reste
 * listee. Le drapeau {@code pieceActive} de la ligne et
 * {@code contientPieceInactive} du panier permettent au recapitulatif de la
 * signaler.</p>
 */
public record PanierVue(
        List<LignePanierVue> lignes,
        int nombreArticles,
        String totalHtva,
        String totalTva,
        /** TVA de chaque taux présent au panier, par taux croissant. */
        List<TvaParTauxVue> tvaParTaux,
        String totalTvac,
        boolean estVide,
        boolean contientPieceInactive,
        /** F12 : le panier ne contient que des prestations. Conditionne la case VI.53. */
        boolean contientService,
        /**
         * Au moins une pièce : la notice harmonisée sur la garantie légale (VI.45 § 1er,
         * 12°) ne vise que la vente de biens.
         */
        boolean contientDesPieces) {

    public static PanierVue de(Panier panier) {
        return new PanierVue(
                panier.getLignes().stream().map(LignePanierVue::de).toList(),
                panier.nombreArticles(),
                FormatageRdv.eurosEcran(panier.totalHtva()),
                FormatageRdv.eurosEcran(panier.totalTva()),
                TvaParTauxVue.de(panier.ventilation()),
                FormatageRdv.eurosEcran(panier.totalTvac()),
                panier.estVide(),
                // Ne porte que sur les pieces : une ligne de service n a pas de piece,
                // et l interroger levait une NPE.
                panier.getLignes().stream()
                        .filter(ligne -> !ligne.estService())
                        .anyMatch(ligne -> !ligne.getPiece().isActif()),
                panier.estPanierDeServices(),
                panier.getLignes().stream().anyMatch(ligne -> !ligne.estService()));
    }

    /** Panier jamais cree : la lecture ne provoque aucune ecriture, elle rend du vide. */
    public static PanierVue vide() {
        String zero = FormatageRdv.eurosEcran(BigDecimal.ZERO);
        return new PanierVue(List.of(), 0, zero, zero, List.of(), zero, true, false, false, false);
    }

    public record LignePanierVue(
            Long id,
            UUID referencePiece,
            String libelle,
            short quantite,
            /** Prix unitaire taxe comprise, au prix et au taux figés à l'ajout. */
            String prixUnitaireTvac,
            String totalTvac,
            boolean pieceActive) {

        public static LignePanierVue de(LignePanier ligne) {
            return new LignePanierVue(
                    ligne.getId(),
                    ligne.estService()
                            ? ligne.getPrestation().getReference()
                            : ligne.getPiece().getReference(),
                    // Dans la langue de l'écran tant que la commande n'est pas conclue.
                    ligne.libelleAffiche(LanguesSupportees.plusProche(LocaleContextHolder.getLocale())),
                    ligne.getQuantite(),
                    FormatageRdv.eurosEcran(ligne.prixUnitaireTvac()),
                    FormatageRdv.eurosEcran(ligne.totalTvac()),
                    // Un service inactif se signale comme une piece inactive : meme
                    // colonne a l ecran, meme sens pour le membre.
                    ligne.estService()
                            ? ligne.getPrestation().isActif()
                            : ligne.getPiece().isActif());
        }
    }
}
