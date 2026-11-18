package be.autoservplus.common.web;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("IdentifiantRequeteFiltre")
class IdentifiantRequeteFiltreTest {

    private final IdentifiantRequeteFiltre filtre = new IdentifiantRequeteFiltre();

    @AfterEach
    void nettoyer() {
        MDC.clear();
    }

    private String passer(MockHttpServletRequest requete) throws Exception {
        AtomicReference<String> vu = new AtomicReference<>();
        filtre.doFilter(requete, new MockHttpServletResponse(),
                (req, rep) -> vu.set(MDC.get(IdentifiantRequeteFiltre.CLE)));
        return vu.get();
    }

    @Test
    @DisplayName("pose un identifiant de huit caractères hexadécimaux pendant la requête, et l'efface après")
    void poseEtEfface() throws Exception {
        String identifiant = passer(new MockHttpServletRequest("GET", "/"));

        assertThat(identifiant).matches("[0-9a-f]{8}");
        assertThat(MDC.get(IdentifiantRequeteFiltre.CLE)).isNull();
    }

    @Test
    @DisplayName("deux requêtes reçoivent deux identifiants")
    void unParRequete() throws Exception {
        assertThat(passer(new MockHttpServletRequest("GET", "/")))
                .isNotEqualTo(passer(new MockHttpServletRequest("GET", "/")));
    }

    @Test
    @DisplayName("le dispatch d'erreur reprend l'identifiant de la requête qui a échoué")
    void dispatchErreur() throws Exception {
        MockHttpServletRequest requete = new MockHttpServletRequest("GET", "/commandes");
        String premier = passer(requete);

        requete.setDispatcherType(DispatcherType.ERROR);
        String surErreur = passer(requete);

        assertThat(surErreur).isEqualTo(premier);
        assertThat(MDC.get(IdentifiantRequeteFiltre.CLE)).isNull();
    }

    @Test
    @DisplayName("un identifiant déjà dans le MDC est repris et laissé à qui l'a posé")
    void identifiantDejaPose() throws Exception {
        MDC.put(IdentifiantRequeteFiltre.CLE, "0badcafe");

        assertThat(passer(new MockHttpServletRequest("GET", "/"))).isEqualTo("0badcafe");
        assertThat(MDC.get(IdentifiantRequeteFiltre.CLE)).isEqualTo("0badcafe");
    }

    @Test
    @DisplayName("le MDC est vidé même quand la chaîne lève")
    void videSurException() {
        assertThatThrownBy(() -> filtre.doFilter(new MockHttpServletRequest("GET", "/"),
                new MockHttpServletResponse(), (req, rep) -> {
                    throw new ServletException("panne");
                })).isInstanceOf(ServletException.class);

        assertThat(MDC.get(IdentifiantRequeteFiltre.CLE)).isNull();
    }
}
