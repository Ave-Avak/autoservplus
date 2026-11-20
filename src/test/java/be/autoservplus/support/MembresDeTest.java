package be.autoservplus.support;

import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;

/**
 * Membres des tests qui passent commande.
 *
 * <p>Une commande exige une adresse postale complète, celle que la facture imprimera
 * (AR n°1, art. 5). Le membre de test en porte donc une par défaut. Seuls les tests de
 * cette garde emploient {@link #sansAdresse}.</p>
 */
public final class MembresDeTest {

    public static final String RUE = "Rue de la Loi";
    public static final String NUMERO = "16";
    public static final String CODE_POSTAL = "1000";
    public static final String LOCALITE = "Bruxelles";

    private MembresDeTest() {
    }

    /** Membre avec une adresse complète, prêt à commander. */
    public static Utilisateur membre(String email, String empreinte, String nom, String prenom) {
        Utilisateur membre = sansAdresse(email, empreinte, nom, prenom);
        membre.setRue(RUE);
        membre.setNumeroRue(NUMERO);
        membre.setCodePostal(CODE_POSTAL);
        membre.setLocalite(LOCALITE);
        return membre;
    }

    /** Membre sans adresse, pour les tests qui vérifient que la commande l'exige. */
    public static Utilisateur sansAdresse(String email, String empreinte, String nom, String prenom) {
        return new Utilisateur(email, empreinte, nom, prenom, TypeUtilisateur.MEMBRE);
    }
}
