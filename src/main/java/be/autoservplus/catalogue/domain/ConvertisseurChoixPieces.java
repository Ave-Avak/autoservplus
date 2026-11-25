package be.autoservplus.catalogue.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Colonne {@code pieces_client} (V77) vers {@link ChoixPieces} : nul pour aucun choix
 * proposé, faux pour les pièces fournies par le garage, vrai pour les pièces apportées.
 *
 * <p>Seul endroit qui lit ou écrit le booléen. Le code compare des valeurs de l'énumération,
 * jamais le booléen, dont le sens tient au nom de la colonne.</p>
 */
@Converter
public class ConvertisseurChoixPieces implements AttributeConverter<ChoixPieces, Boolean> {

    @Override
    public Boolean convertToDatabaseColumn(ChoixPieces choix) {
        return choix == null ? null : choix == ChoixPieces.APPORTEES_PAR_CLIENT;
    }

    @Override
    public ChoixPieces convertToEntityAttribute(Boolean piecesClient) {
        if (piecesClient == null) {
            return null;
        }
        return piecesClient ? ChoixPieces.APPORTEES_PAR_CLIENT : ChoixPieces.FOURNIES_PAR_GARAGE;
    }
}
