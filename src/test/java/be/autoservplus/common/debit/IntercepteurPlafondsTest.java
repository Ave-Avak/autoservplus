package be.autoservplus.common.debit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.autoservplus.common.debit.ProprietesPlafonds.Par;
import be.autoservplus.common.debit.ProprietesPlafonds.Surface;
import be.autoservplus.common.debit.SeauxParCle.Limite;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;

@DisplayName("IntercepteurPlafonds")
class IntercepteurPlafondsTest {

    static class Cible {
        @Plafonne("par-compte")
        public void parCompte() {
        }

        @Plafonne("par-ip")
        public void parIp() {
        }

        public void libre() {
        }
    }

    @Plafonne("par-ip")
    static class CiblePlafonneeEntiere {
        public void route() {
        }
    }

    private final PlafondsRequetes plafonds = new PlafondsRequetes(new ProprietesPlafonds(100, Map.of(
            "par-compte", new Surface(Par.COMPTE, List.of(new Limite(1, Duration.ofMinutes(1)))),
            "par-ip", new Surface(Par.IP, List.of(new Limite(1, Duration.ofMinutes(1)))))),
            Clock.fixed(Instant.parse("2026-11-26T10:00:00Z"), ZoneOffset.UTC));
    private final IntercepteurPlafonds intercepteur = new IntercepteurPlafonds(plafonds);

    @AfterEach
    void viderLeContexte() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("une route sans annotation n'est jamais plafonnée")
    void routeLibre() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(appeler("libre", Cible.class, "203.0.113.7")).isTrue();
        }
    }

    @Test
    @DisplayName("compté par compte : deux comptes derrière la même IP ont chacun leur budget")
    void parCompte() throws Exception {
        connecter("alex@exemple.be");
        assertThat(appeler("parCompte", Cible.class, "203.0.113.7")).isTrue();
        assertThatThrownBy(() -> appeler("parCompte", Cible.class, "203.0.113.7"))
                .isInstanceOf(PlafondDepasseException.class);
        connecter("sam@exemple.be");
        assertThat(appeler("parCompte", Cible.class, "203.0.113.7")).isTrue();
    }

    @Test
    @DisplayName("compté par compte, visiteur anonyme : l'IP sert de clé")
    void anonymeParIp() throws Exception {
        assertThat(appeler("parCompte", Cible.class, "203.0.113.7")).isTrue();
        assertThatThrownBy(() -> appeler("parCompte", Cible.class, "203.0.113.7"))
                .isInstanceOf(PlafondDepasseException.class);
        assertThat(appeler("parCompte", Cible.class, "198.51.100.1")).isTrue();
    }

    @Test
    @DisplayName("IPv6 comptée par /64")
    void ipv6ParReseau() throws Exception {
        assertThat(appeler("parIp", Cible.class, "2001:db8:1:2::1")).isTrue();
        assertThatThrownBy(() -> appeler("parIp", Cible.class, "2001:db8:1:2:ffff::9"))
                .isInstanceOf(PlafondDepasseException.class);
    }

    @Test
    @DisplayName("l'annotation posée sur la classe vaut pour toutes ses routes")
    void annotationDeClasse() throws Exception {
        assertThat(appeler("route", CiblePlafonneeEntiere.class, "203.0.113.7")).isTrue();
        assertThatThrownBy(() -> appeler("route", CiblePlafonneeEntiere.class, "203.0.113.7"))
                .isInstanceOf(PlafondDepasseException.class);
    }

    @Test
    @DisplayName("Retry-After en secondes entières arrondies au-dessus, une au moins")
    void secondesArrondies() {
        assertThat(new PlafondDepasseException("s", Duration.ofMillis(1500)).secondesAvantNouvelEssai())
                .isEqualTo(2);
        assertThat(new PlafondDepasseException("s", Duration.ZERO).secondesAvantNouvelEssai()).isEqualTo(1);
        assertThat(new PlafondDepasseException("s", Duration.ofSeconds(3)).secondesAvantNouvelEssai())
                .isEqualTo(3);
    }

    @Test
    @DisplayName("une surface absente de la configuration est une erreur de code")
    void surfaceInconnue() {
        assertThatThrownBy(() -> plafonds.consommer("inconnue", "ip:1"))
                .isInstanceOf(IllegalStateException.class);
    }

    private boolean appeler(String methode, Class<?> type, String ip) throws Exception {
        MockHttpServletRequest requete = new MockHttpServletRequest();
        requete.setRemoteAddr(ip);
        Object instance = type.getDeclaredConstructor().newInstance();
        HandlerMethod cible = new HandlerMethod(instance, type.getMethod(methode));
        return intercepteur.preHandle(requete, new MockHttpServletResponse(), cible);
    }

    private static void connecter(String adresse) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                adresse, null, AuthorityUtils.createAuthorityList("ROLE_MEMBRE")));
    }
}
