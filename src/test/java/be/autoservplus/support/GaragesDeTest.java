package be.autoservplus.support;

import be.autoservplus.config.IdentiteGarage;
import be.autoservplus.identite.domain.Langue;

import java.util.List;

/**
 * Identités de garage des tests qui n'en vérifient pas les mentions, seulement la langue
 * des documents : elle suit la région du siège, et non le client.
 */
public final class GaragesDeTest {

    private GaragesDeTest() {
    }

    /** Siège à Bruxelles : français ou néerlandais, le français par défaut. */
    public static IdentiteGarage bruxellois() {
        return siege("Bruxelles", "RPM Bruxelles", List.of(Langue.fr, Langue.nl));
    }

    /** Siège en Flandre : néerlandais seul, quel que soit le client. */
    public static IdentiteGarage flamand() {
        return siege("Leuven", "RPR Leuven", List.of(Langue.nl));
    }

    /** Siège en Wallonie : français seul. */
    public static IdentiteGarage wallon() {
        return siege("Namur", "RPM Namur", List.of(Langue.fr));
    }

    private static IdentiteGarage siege(String localite, String rpm, List<Langue> langues) {
        return new IdentiteGarage("Garage de test", "Garage de test", "SRL", "Rue de l'Atelier", "12", "1000",
                localite, "Belgique", "0123.456.789", rpm, "BE0123456789", "BE68 5390 0754 7034",
                "+32 2 000 00 00", "garage@exemple.be", langues);
    }
}
