package be.autoservplus.communication.service;

import be.autoservplus.identite.domain.Langue;

import java.time.Instant;
import java.util.List;

/**
 * Éléments de la confirmation du contrat conclu (art. VI.46, § 7, CDE), envoyée au
 * membre quand sa commande est payée. Chaînes déjà formatées et pièces déjà produites :
 * le module communication ne dépend pas du module vente.
 *
 * @param conclueLe     conclusion du contrat, c'est-à-dire la validation de la commande
 * @param pieces        la commande porte des pièces à retirer
 * @param prestations   la commande porte des prestations à planifier
 * @param versionCgv    version des CGV acceptée pour cette commande, nulle si aucune
 *                      preuve ne la désigne, et alors aucune n'est jointe
 * @param renonciation  renonciation VI.53 cochée pour cette commande, nulle sinon
 * @param piecesJointes CGV acceptées (quand elles sont connues) et formulaire type
 */
public record DetailsConfirmationCommande(String adresseEmail,
                                          String prenom,
                                          Langue langue,
                                          String numeroCommande,
                                          Instant conclueLe,
                                          Instant payeeLe,
                                          List<LigneCommandee> lignes,
                                          String totalHtva,
                                          String totalTva,
                                          String totalTvac,
                                          boolean pieces,
                                          boolean prestations,
                                          String versionCgv,
                                          RenonciationConfirmee renonciation,
                                          List<PieceJointeCourriel> piecesJointes) {

    public DetailsConfirmationCommande {
        lignes = List.copyOf(lignes);
        piecesJointes = List.copyOf(piecesJointes);
    }

    /**
     * Une ligne de la commande, prix figés à l'ajout au panier (RM-30).
     *
     * @param mentionPieces clé de la mention du choix des pièces, {@code null} sans choix proposé
     */
    public record LigneCommandee(String libelle, int quantite, String totalTvac, String tauxTva,
                                 String mentionPieces) {
    }

    /**
     * Accord préalable exprès et reconnaissance de la perte du droit de rétractation,
     * tels que la preuve les a enregistrés : date, version et texte coché.
     */
    public record RenonciationConfirmee(Instant le, String version, String libelle) {
    }
}
