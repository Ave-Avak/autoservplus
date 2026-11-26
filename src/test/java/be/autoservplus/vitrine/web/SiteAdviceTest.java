package be.autoservplus.vitrine.web;

import be.autoservplus.config.IdentiteGarage;
import be.autoservplus.vitrine.service.EtatDuJourService;
import be.autoservplus.vitrine.service.HorairesOuvertureService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.ui.ExtendedModelMap;
import org.thymeleaf.context.LazyContextVariable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("SiteAdvice : bandeau et pied du site")
class SiteAdviceTest {

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> fournisseur(T valeur) {
        ObjectProvider<T> fournisseur = mock(ObjectProvider.class);
        when(fournisseur.getIfAvailable()).thenReturn(valeur);
        return fournisseur;
    }

    @Test
    @DisplayName("une panne de la base pendant le rendu laisse la page s'afficher, sans horaires ni état du jour")
    void panneDeLaBase() {
        HorairesOuvertureService horaires = mock(HorairesOuvertureService.class);
        EtatDuJourService etat = mock(EtatDuJourService.class);
        when(horaires.resume()).thenThrow(new DataAccessResourceFailureException("base injoignable"));
        when(etat.maintenant()).thenThrow(new DataAccessResourceFailureException("base injoignable"));
        SiteAdvice conseil = new SiteAdvice(fournisseur((IdentiteGarage) null), fournisseur(horaires),
                fournisseur(etat), false);

        ExtendedModelMap modele = new ExtendedModelMap();
        conseil.exposerLeSite(modele);

        assertThat(((LazyContextVariable<?>) modele.getAttribute("horairesResume")).getValue())
                .asList().isEmpty();
        assertThat(((LazyContextVariable<?>) modele.getAttribute("etatDuJour")).getValue()).isNull();
    }
}
