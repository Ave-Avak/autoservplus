package be.autoservplus.infrastructure;

import be.autoservplus.identite.domain.TypeEvenementSecurite;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.support.SocleIntegration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contraintes de V41 : la base porte elle-meme les invariants du second facteur, comme
 * elle porte ailleurs les enumerations et les exclusivites.
 */
@SpringBootTest
@DisplayName("Schéma du second facteur (V41)")
class SchemaMfaIT extends SocleIntegration {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private UtilisateurRepository utilisateurs;

    private long compte(String email) {
        return utilisateurs.saveAndFlush(new Utilisateur(email, "x", "Nom", "Prenom",
                TypeUtilisateur.MEMBRE)).getId();
    }

    @Test
    @DisplayName("le CHECK des types d'événement est exactement l'énumération Java")
    void typesAlignesSurLEnumeration() {
        long id = compte("schema-types@exemple.be");
        for (TypeEvenementSecurite type : TypeEvenementSecurite.values()) {
            jdbc.update("INSERT INTO evenement_securite_compte (utilisateur_id, type, horodatage) "
                    + "VALUES (?, ?, now())", id, type.name());
        }

        assertThatThrownBy(() -> jdbc.update("INSERT INTO evenement_securite_compte "
                + "(utilisateur_id, type, horodatage) VALUES (?, 'INCONNU', now())", id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("un code de secours n'est jamais à la fois consommé et remplacé")
    void codeSecoursUnSeulMotif() {
        long id = compte("schema-codes@exemple.be");

        assertThatThrownBy(() -> jdbc.update("INSERT INTO code_secours (utilisateur_id, empreinte, "
                + "consomme_le, invalide_le) VALUES (?, 'e', now(), now())", id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("un second facteur actif suppose un secret, un secret suppose sa version de clé")
    void secondFacteurCoherent() {
        long id = compte("schema-mfa@exemple.be");

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE utilisateur SET mfa_active_le = now() WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE utilisateur SET mfa_secret_chiffre = '\\x01'::bytea WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE utilisateur SET jeton_definition_hache = 'abc' WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("les deux tables nouvelles sont déclarées au balayage d'anonymisation")
    void tracesDeclarees() {
        List<String> tables = jdbc.queryForList(
                "SELECT DISTINCT nom_table FROM fn_tables_traces_audit()", String.class);

        assertThat(tables).containsAll(Arrays.asList("code_secours", "evenement_securite_compte"));
    }
}
