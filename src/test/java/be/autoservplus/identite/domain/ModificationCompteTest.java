package be.autoservplus.identite.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@DisplayName("Historique des modifications du compte : invariants")
class ModificationCompteTest {

    private static final Instant MAINTENANT = Instant.parse("2026-11-20T10:00:00Z");

    private static Utilisateur membre() {
        return new Utilisateur("marie@exemple.be", "$2a$12$empreinte", "Dupont", "Marie",
                TypeUtilisateur.MEMBRE);
    }

    @Test
    @DisplayName("un changement sans différence est refusé")
    void changementSansDifference() {
        assertThatThrownBy(() -> new ChangementChamp(ChampCompte.NOM, "Dupont", "Dupont"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("un administrateur doit donner le motif de la demande")
    void motifExigePourUnTiers() {
        Utilisateur membre = membre();
        Utilisateur admin = new Utilisateur("chef@exemple.be", "$2a$12$empreinte", "Garage",
                "Chef", TypeUtilisateur.ADMINISTRATEUR);
        ChangementChamp changement = new ChangementChamp(ChampCompte.NOM, "Dupont", "Durand");

        assertThatThrownBy(() -> new ModificationCompte(membre, changement, MAINTENANT, admin, " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new ModificationCompte(membre, changement, MAINTENANT, admin,
                " Appel du 20 novembre ").getMotif()).isEqualTo("Appel du 20 novembre");
    }

    @Test
    @DisplayName("le titulaire modifie sans motif")
    void titulaireSansMotif() {
        Utilisateur membre = membre();
        ModificationCompte ligne = new ModificationCompte(membre,
                new ChangementChamp(ChampCompte.NOM, "Dupont", "Durand"), MAINTENANT, membre, null);

        assertThat(ligne.parTitulaire()).isTrue();
        assertThat(ligne.getMotif()).isNull();
    }

    @Test
    @DisplayName("modifierProfil rend les seuls champs changés, valeurs normalisées")
    void champsChanges() {
        Utilisateur membre = membre();

        var changements = membre.modifierProfil(" Marie ", "Durand", "", "Rue Neuve", null,
                "1000", "Bruxelles", "Belgique", Langue.nl);

        assertThat(changements)
                .extracting(ChangementChamp::champ, ChangementChamp::avant, ChangementChamp::apres)
                .containsExactly(
                        tuple(ChampCompte.NOM, "Dupont", "Durand"),
                        tuple(ChampCompte.RUE, null, "Rue Neuve"),
                        tuple(ChampCompte.CODE_POSTAL, null, "1000"),
                        tuple(ChampCompte.LOCALITE, null, "Bruxelles"),
                        tuple(ChampCompte.LANGUE, "fr", "nl"));
    }
}
