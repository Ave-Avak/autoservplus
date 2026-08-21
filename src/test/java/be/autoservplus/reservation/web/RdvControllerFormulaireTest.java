package be.autoservplus.reservation.web;

import be.autoservplus.reservation.service.RdvService;
import be.autoservplus.reservation.service.VehiculeService;
import be.autoservplus.reservation.web.dto.VehiculeVue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rendu du formulaire de prise de rendez-vous.
 *
 * <p>Verifie que les bornes de la fenetre de reservation sont poussees dans le HTML au
 * format ISO yyyy-MM-dd. C est ce format qu attend l input HTML5 <code>type="date"</code> :
 * le rendre au format belge dd/MM/yyyy casserait la validation cote navigateur. Les
 * valeurs viennent du service ; le test fige la fenetre pour verifier la sortie exacte.</p>
 */
@WebMvcTest(RdvController.class)
@DisplayName("RdvController — formulaire")
class RdvControllerFormulaireTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private RdvService rdvs;
    @MockitoBean private VehiculeService vehicules;

    @Test
    @DisplayName("expose min et max au format ISO yyyy-MM-dd sur l input date")
    @WithMockUser(username = "membre@exemple.be")
    void exposeMinMaxISO() throws Exception {
        LocalDate min = LocalDate.of(2026, 9, 15);
        LocalDate max = LocalDate.of(2026, 11, 14);

        when(rdvs.premierJourReservable()).thenReturn(min);
        when(rdvs.dernierJourReservable()).thenReturn(max);
        when(rdvs.prestationsProposees()).thenReturn(Map.of());
        when(rdvs.creneauxPour(min, List.of())).thenReturn(List.of());
        when(vehicules.vuesDuMembre("membre@exemple.be")).thenReturn(List.of(
                new VehiculeVue(UUID.randomUUID(), "1-ABC-123", "VW", "Golf",
                        "DIESEL", (short) 2020, 100000, null, "VW Golf (1-ABC-123)")));

        mvc.perform(get("/mes-rendez-vous/nouveau"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("min=\"2026-09-15\"")))
                .andExpect(content().string(containsString("max=\"2026-11-14\"")));
    }
}
