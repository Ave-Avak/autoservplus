package be.autoservplus.communication.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Un envoi de courriel doit etre cable dans TOUT environnement, et ce test le
 * verifie a la structure plutot qu au demarrage.
 *
 * <p><b>Le defaut qu il verrouille a reellement existe.</b>
 * {@link CourrielConsole} etait la seule implementation de {@link ServiceCourriel}
 * et portait {@code @Profile("!prod")} : demarrer en profil {@code prod} echouait
 * au cablage, faute de bean a injecter. Le probleme ne se voyait dans aucun test,
 * puisqu aucun ne demarre sous ce profil — il ne serait apparu qu au premier
 * deploiement reel, c est-a-dire au pire moment.</p>
 *
 * <p>La regle est generale et se lit telle quelle : tant qu il n existe qu une
 * implementation, elle ne peut etre conditionnee par rien. Le jour ou une seconde
 * apparaitra (Brevo), ce test echouera et devra etre reecrit pour verifier que les
 * conditions des deux <b>partitionnent</b> les environnements — ce qui est
 * exactement la question a se reposer a ce moment-la.</p>
 */
@DisplayName("Disponibilite du service de courriel")
class ServiceCourrielDisponibleTest {

    @Test
    @DisplayName("l unique implementation n est conditionnee par aucun profil")
    void uniqueImplementationInconditionnelle() {
        var chercheur = new ClassPathScanningCandidateComponentProvider(false);
        chercheur.addIncludeFilter(new AssignableTypeFilter(ServiceCourriel.class));
        List<String> implementations = chercheur
                .findCandidateComponents("be.autoservplus")
                .stream()
                .map(definition -> definition.getBeanClassName())
                .toList();

        assertThat(implementations)
                .as("implementations de ServiceCourriel trouvees au classpath")
                .hasSize(1);

        Class<?> unique = chargerLaClasse(implementations.get(0));
        assertThat(unique.getAnnotation(Profile.class))
                .as("""
                        %s est la seule implementation de ServiceCourriel : la conditionner \
                        a un profil prive les autres environnements de tout envoi, et le \
                        contexte Spring echoue au cablage. Si une seconde implementation \
                        vient d etre ajoutee, c est ce test qu il faut reecrire — pour \
                        verifier que les conditions des deux couvrent tous les cas.""",
                        unique.getSimpleName())
                .isNull();
    }

    private static Class<?> chargerLaClasse(String nom) {
        try {
            return Class.forName(nom);
        } catch (ClassNotFoundException introuvable) {
            throw new IllegalStateException(
                    "Classe detectee au scan mais introuvable : " + nom, introuvable);
        }
    }
}
