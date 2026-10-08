package be.autoservplus.identite.web.dto;

import be.autoservplus.identite.domain.EvenementSecuriteCompte;
import be.autoservplus.identite.domain.TypeEvenementSecurite;

import java.time.Instant;

/**
 * Une ligne du journal de securite d un compte. Meme regle que
 * {@link EvenementStatutVue} pour l auteur : son nom, jamais son adresse ; {@code null}
 * quand le titulaire a agi lui-meme ou que l evenement vient du systeme.
 */
public record EvenementSecuriteVue(Instant horodatage, TypeEvenementSecurite type, String auteur) {

    public static EvenementSecuriteVue de(EvenementSecuriteCompte evenement) {
        return new EvenementSecuriteVue(
                evenement.getHorodatage(),
                evenement.getType(),
                evenement.getAuteur() == null ? null : evenement.getAuteur().nomComplet());
    }
}
