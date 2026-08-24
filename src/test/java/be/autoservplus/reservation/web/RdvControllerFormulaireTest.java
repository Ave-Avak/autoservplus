package be.autoservplus.reservation.web;

import be.autoservplus.reservation.service.CreneauIndisponibleException;
import org.springframework.validation.BindingResult;
import be.autoservplus.reservation.service.LimiteDemandesEnAttenteException;
import be.autoservplus.reservation.service.PrestationIndisponibleException;
import be.autoservplus.reservation.service.ExportAgendaService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
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
    @MockitoBean private ExportAgendaService agendas;

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

    // --- routage des refus metier vers les champs du formulaire -----------------------
    //
    // Le routage se fait par TYPE d exception, plus par le code de regle : chaque
    // branche est testee contre le rendu reel du template, prefixe [RM-xx] banni.

    /** Stubs necessaires au re-rendu du formulaire apres un refus (retourFormulaire). */
    private void stubsFormulaire() {
        when(rdvs.premierJourReservable()).thenReturn(LocalDate.of(2026, 9, 15));
        when(rdvs.dernierJourReservable()).thenReturn(LocalDate.of(2026, 11, 14));
        when(rdvs.prestationsProposees()).thenReturn(Map.of());
        when(rdvs.creneauxPour(any(), any())).thenReturn(List.of());
        // Au moins un vehicule : sans lui, le template court-circuite le formulaire
        // (« enregistrez d'abord un vehicule ») et aucune erreur de champ ne s affiche.
        when(vehicules.vuesDuMembre("membre@exemple.be")).thenReturn(List.of(
                new VehiculeVue(UUID.randomUUID(), "1-ABC-123", "VW", "Golf",
                        "DIESEL", (short) 2020, 100000, null, "VW Golf (1-ABC-123)")));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            postReservation() {
        return post("/mes-rendez-vous/nouveau").with(csrf())
                .param("vehicule", UUID.randomUUID().toString())
                .param("prestations", UUID.randomUUID().toString())
                .param("date", "2026-09-15")
                .param("debut", "2026-09-15T08:00:00Z");
    }

    @Test
    @WithMockUser(username = "membre@exemple.be")
    @DisplayName("prestation indisponible : erreur sous « prestations », message sans prefixe [RM-…]")
    void prestationIndisponibleSousLeChampPrestations() throws Exception {
        stubsFormulaire();
        when(rdvs.reserver(any(), any(), any(), any(), any()))
                .thenThrow(new PrestationIndisponibleException("Vidange"));

        mvc.perform(postReservation())
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("formulaire", "prestations"))
                .andExpect(content().string(containsString("La prestation Vidange n est plus proposee.")))
                .andExpect(content().string(not(containsString("[RM-"))));
    }

    @Test
    @WithMockUser(username = "membre@exemple.be")
    @DisplayName("plafond de demandes : erreur GLOBALE, pas sous « prestations »")
    void plafondDemandesEnErreurGlobale() throws Exception {
        stubsFormulaire();
        when(rdvs.reserver(any(), any(), any(), any(), any()))
                .thenThrow(new LimiteDemandesEnAttenteException(3));

        mvc.perform(postReservation())
                .andExpect(status().isOk())
                // Le plafond porte sur le COMPTE : l'ancrer sous « prestations »
                // disait au membre de corriger un choix qui n'est pas en cause.
                // Zero erreur de CHAMP, exactement une erreur GLOBALE.
                .andExpect(resultat -> {
                    BindingResult liaison = (BindingResult) resultat.getModelAndView()
                            .getModel().get(BindingResult.MODEL_KEY_PREFIX + "formulaire");
                    assertThat(liaison.getFieldErrorCount()).isZero();
                    assertThat(liaison.getGlobalErrorCount()).isEqualTo(1);
                })
                // ... et le message doit rester visible : le gabarit ne rendait
                // aucune erreur globale avant cette correction.
                .andExpect(content().string(containsString("3 demandes en attente")));
    }

    @Test
    @WithMockUser(username = "membre@exemple.be")
    @DisplayName("creneau indisponible : erreur sous « debut », message sans prefixe [RM-…]")
    void creneauIndisponibleSousLeChampDebut() throws Exception {
        stubsFormulaire();
        when(rdvs.reserver(any(), any(), any(), any(), any()))
                .thenThrow(new CreneauIndisponibleException(
                        "Ce creneau vient d etre pris. Choisissez-en un autre."));

        mvc.perform(postReservation())
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("formulaire", "debut"))
                .andExpect(content().string(containsString("Ce creneau vient d etre pris. Choisissez-en un autre.")))
                .andExpect(content().string(not(containsString("[RM-"))));
    }
}
