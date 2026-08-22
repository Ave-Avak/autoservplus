package be.autoservplus.catalogue.domain;

/**
 * Nature de l element du catalogue vise par une ligne d historique de modification.
 *
 * <p>Discriminant de la colonne polymorphe {@code entite_id} de
 * {@link HistoriqueModificationCatalogue} : le catalogue n a que deux sortes
 * d elements modifiables, une prestation (table {@code service}) ou une piece.
 * La contrainte {@code ck_histo_catalogue_type} en base porte la meme liste fermee.</p>
 */
public enum TypeEntiteCatalogue {
    PRESTATION,
    PIECE
}
