package be.autoservplus.config;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Identité du garage en lignes « libellé : valeur », dans la langue demandée, pour les
 * supports qui n'ont pas de gabarit : le courriel de confirmation de commande et le PDF
 * des conditions générales. Mêmes rubriques, dans le même ordre, que le fragment
 * {@code legal :: identite} des pages, pour qu'un document et une page ne nomment pas le
 * vendeur de deux façons.
 *
 * <p>Le nom commercial n'y figure que s'il est configuré : un garage sans enseigne se
 * présente sous sa raison sociale, déjà nommée à la première ligne.</p>
 */
@Component
public class MentionsIdentiteGarage {

    private final IdentiteGarage garage;
    private final MessageSource messages;

    public MentionsIdentiteGarage(IdentiteGarage garage, MessageSource messages) {
        this.garage = garage;
        this.messages = messages;
    }

    public List<String> lignes(Locale langue) {
        List<String> lignes = new ArrayList<>();
        ajouter(lignes, "legal.garage.raison-sociale", garage.raisonSociale(), langue);
        if (garage.aUneFormeJuridique()) {
            ajouter(lignes, "legal.garage.forme-juridique", garage.formeJuridique(), langue);
        }
        if (garage.aUnNomCommercial()) {
            ajouter(lignes, "legal.garage.nom-commercial", garage.nomCommercial(), langue);
        }
        String adresse = garage.adresseLisible();
        if (garage.pays() != null && !garage.pays().isBlank()) {
            adresse = adresse + ", " + garage.pays();
        }
        ajouter(lignes, "legal.garage.adresse", adresse, langue);
        ajouter(lignes, "legal.garage.bce", garage.numeroBce(), langue);
        if (garage.aUnRpm()) {
            ajouter(lignes, "legal.garage.rpm", garage.rpm(), langue);
        }
        ajouter(lignes, "legal.garage.tva", garage.numeroTva(), langue);
        ajouter(lignes, "legal.garage.telephone", garage.telephone(), langue);
        ajouter(lignes, "legal.garage.courriel", garage.courriel(), langue);
        return List.copyOf(lignes);
    }

    private void ajouter(List<String> lignes, String cle, String valeur, Locale langue) {
        String libelle = messages.getMessage(cle, null, langue);
        lignes.add(messages.getMessage("legal.garage.ligne", new Object[]{libelle, valeur}, langue));
    }
}
