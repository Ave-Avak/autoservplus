package be.autoservplus.legal.service;

import be.autoservplus.legal.domain.TypeDocumentVersionne;
import be.autoservplus.legal.repository.VersionDocumentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Resolution des versions de documents (F24)")
class VersionsDocumentsServiceTest {

    private static final Instant MAINTENANT = Instant.parse("2026-08-24T10:00:00Z");

    @Mock private VersionDocumentRepository versions;

    private VersionsDocumentsService service;

    @BeforeEach
    void construire() {
        // Horloge figee et non systeme : la resolution filtre sur date_effet, donc elle
        // depend du temps. Un test qui laisserait l heure courante s y glisser passerait
        // ou echouerait selon le jour.
        service = new VersionsDocumentsService(versions, Clock.fixed(MAINTENANT, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("l absence de version en vigueur echoue franchement au lieu d ecrire une preuve muette")
    void absenceDeVersion() {
        when(versions.versionsEnVigueur(eq(TypeDocumentVersionne.CGV), any(), any(Pageable.class)))
                .thenReturn(List.of());

        // Une preuve sans version enregistrerait que le membre a accepte « quelque chose ».
        // Mieux vaut refuser la commande que produire une preuve qui ne prouve rien :
        // l absence ne peut venir que d une base incompletement migree, jamais d une
        // saisie utilisateur, donc aucun parcours legitime ne bute ici.
        assertThatThrownBy(() -> service.versionCourante(TypeDocumentVersionne.CGV))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("version_document");

        assertThat(service.versionEnVigueur(TypeDocumentVersionne.CGV)).isEmpty();
    }

    @Test
    @DisplayName("une version inconnue ne rend aucun texte plutot qu un texte approchant")
    void archiveInconnue() {
        when(versions.findByTypeDocumentAndVersionOrderByLangue(TypeDocumentVersionne.COOKIES, "X"))
                .thenReturn(List.of());

        assertThat(service.archive(TypeDocumentVersionne.COOKIES, "X", Locale.FRENCH)).isEmpty();
    }
}
