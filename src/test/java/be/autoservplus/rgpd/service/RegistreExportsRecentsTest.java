package be.autoservplus.rgpd.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Limite d un export par 24 heures et par membre (F22).
 *
 * <p>La regle est temporelle : elle se teste en deplacant l horloge, jamais en
 * attendant. L horloge reglable ci-dessous est la contrepartie de l injection de
 * {@link Clock} dans le composant.
 */
@DisplayName("RegistreExportsRecents")
class RegistreExportsRecentsTest {

    private static final String MARIE = "marie@exemple.be";
    private static final String JEAN = "jean@exemple.be";
    private static final Instant DEPART = Instant.parse("2026-08-22T07:30:00Z");

    private HorlogeReglable horloge;
    private RegistreExportsRecents registre;

    @BeforeEach
    void setUp() {
        horloge = new HorlogeReglable(DEPART);
        registre = new RegistreExportsRecents(horloge);
    }

    @Test
    @DisplayName("un membre qui n'a jamais exporte peut exporter")
    void aucunExportPrecedent() {
        assertThat(registre.attenteRestante(MARIE)).isEmpty();
        assertThat(registre.dernierExport(MARIE)).isEmpty();
    }

    @Test
    @DisplayName("juste apres un export, il reste 24 heures a attendre")
    void justeApresUnExport() {
        registre.enregistrer(MARIE);

        assertThat(registre.attenteRestante(MARIE))
                .contains(RegistreExportsRecents.DELAI_ENTRE_EXPORTS);
        assertThat(registre.dernierExport(MARIE)).contains(DEPART);
    }

    @Test
    @DisplayName("l'attente decroit avec le temps")
    void attenteDecroissante() {
        registre.enregistrer(MARIE);
        horloge.avancerDe(Duration.ofHours(20));

        assertThat(registre.attenteRestante(MARIE)).contains(Duration.ofHours(4));
    }

    @Test
    @DisplayName("a 23 h 59, l'export reste refuse")
    void justeAvantLEcheance() {
        registre.enregistrer(MARIE);
        horloge.avancerDe(Duration.ofHours(23).plusMinutes(59));

        assertThat(registre.attenteRestante(MARIE)).contains(Duration.ofMinutes(1));
    }

    @Test
    @DisplayName("a 24 h pile, l'export redevient possible")
    void aLEcheance() {
        registre.enregistrer(MARIE);
        horloge.avancerDe(RegistreExportsRecents.DELAI_ENTRE_EXPORTS);

        // Borne inclusive : la fenetre est « moins de 24 heures », pas
        // « 24 heures ou moins » — a l'echeance exacte, le droit se rouvre.
        assertThat(registre.attenteRestante(MARIE)).isEmpty();
        assertThat(registre.dernierExport(MARIE)).isEmpty();
    }

    @Test
    @DisplayName("la limite d'un membre n'affecte pas les autres")
    void limitePropreAuMembre() {
        registre.enregistrer(MARIE);

        assertThat(registre.attenteRestante(MARIE)).isPresent();
        assertThat(registre.attenteRestante(JEAN)).isEmpty();
    }

    @Test
    @DisplayName("la casse de l'adresse ne contourne pas la limite")
    void casseInsensible() {
        registre.enregistrer("Marie@Exemple.BE");

        assertThat(registre.attenteRestante(MARIE)).isPresent();
    }

    @Test
    @DisplayName("un nouvel export repousse l'echeance")
    void nouvelExportRepousse() {
        registre.enregistrer(MARIE);
        horloge.avancerDe(RegistreExportsRecents.DELAI_ENTRE_EXPORTS);
        registre.enregistrer(MARIE);

        assertThat(registre.attenteRestante(MARIE))
                .contains(RegistreExportsRecents.DELAI_ENTRE_EXPORTS);
    }

    @Test
    @DisplayName("les entrees perimees sont purgees a l'ecriture suivante")
    void purgeDesEntreesPerimees() {
        // Sans purge, la carte grandirait avec la population des membres : elle
        // ne doit retenir que la fenetre courante.
        registre.enregistrer(MARIE);
        horloge.avancerDe(Duration.ofHours(25));
        registre.enregistrer(JEAN);

        assertThat(tailleInterne()).isEqualTo(1);
        assertThat(registre.dernierExport(JEAN)).isPresent();
        assertThat(registre.dernierExport(MARIE)).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private int tailleInterne() {
        return ((java.util.Map<String, Instant>) org.springframework.test.util.ReflectionTestUtils
                .getField(registre, "derniersExports")).size();
    }

    /** Horloge deplacable a la main : la fenetre de 24 h se teste en secondes. */
    private static final class HorlogeReglable extends Clock {

        private Instant instant;

        private HorlogeReglable(Instant depart) {
            this.instant = depart;
        }

        void avancerDe(Duration duree) {
            this.instant = this.instant.plus(duree);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
