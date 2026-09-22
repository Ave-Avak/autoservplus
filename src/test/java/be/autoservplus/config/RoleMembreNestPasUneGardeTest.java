package be.autoservplus.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fige la decision de n avoir que <b>deux</b> niveaux dans la hierarchie des roles.
 *
 * <p>{@code ROLE_MEMBRE} n y figure pas, et le motif n est pas celui qu on croirait :
 * ce n est pas qu un administrateur serait tenu a l ecart des ecrans personnels — il
 * les atteint, {@code anyRequest().authenticated()} suffisant a les couvrir, et leur
 * cloisonnement venant de l <i>ownership</i> et non du role. C est que
 * <b>{@code ROLE_MEMBRE} n est garde nulle part</b> : l inscrire dans la hierarchie
 * ne changerait donc strictement rien, et n ajouterait que du bruit a une declaration
 * dont toute la valeur est d etre lisible d un coup d oeil.</p>
 *
 * <p>Cette verite n est vraie que tant qu elle dure. Le jour ou un ecran exigerait
 * {@code hasRole('MEMBRE')}, l absence de {@code ROLE_MEMBRE} dans la hierarchie
 * cesserait d etre neutre : elle <b>fermerait cet ecran aux administrateurs</b>, sans
 * que personne l ait decide. Ce cas casse alors la build en nommant le fichier
 * fautif, au moment ou le choix peut encore etre fait.</p>
 *
 * <p><b>Ce cas a une histoire</b>, et elle vaut d etre racontee : l analyse qui a
 * precede ce lot affirmait qu un administrateur n atteignait pas les ecrans membre.
 * C etait faux — un test l a montre en repondant 200 la ou il attendait 403. La
 * decision de s en tenir a deux niveaux restait bonne, mais pour une autre raison que
 * celle avancee, et c est cette raison-la que ce cas verrouille.</p>
 */
@DisplayName("ROLE_MEMBRE n'est une garde nulle part")
class RoleMembreNestPasUneGardeTest {

    private static final Path SOURCES = Path.of("src", "main", "java");

    /** Ce fichier documente la decision : il cite necessairement le role. */
    private static final String DECLARATION = "config/SecuriteConfig.java";

    @Test
    @DisplayName("aucune garde de production n'exige le rôle MEMBRE")
    void aucuneGardeSurRoleMembre() {
        List<String> gardes = sources().stream()
                .filter(source -> !chemin(source).endsWith(DECLARATION))
                .filter(source -> {
                    String contenu = lire(source);
                    return contenu.contains("hasRole('MEMBRE')")
                            || contenu.contains("hasRole(\"MEMBRE\")")
                            || contenu.contains("hasAuthority('ROLE_MEMBRE')")
                            || contenu.contains("hasAuthority(\"ROLE_MEMBRE\")");
                })
                .map(RoleMembreNestPasUneGardeTest::chemin)
                .toList();

        assertThat(gardes)
                .as("une garde exige desormais ROLE_MEMBRE : la hierarchie des roles de "
                        + "SecuriteConfig doit etre rouverte, sinon cet ecran se ferme "
                        + "aux administrateurs sans que personne l ait decide")
                .isEmpty();
    }

    @Test
    @DisplayName("le balayage lit bien les sources (sans quoi le cas précédent serait vide)")
    void leBalayageTrouveDesSources() {
        assertThat(sources()).hasSizeGreaterThan(100);
    }

    private static List<Path> sources() {
        try (Stream<Path> arbre = Files.walk(SOURCES)) {
            return arbre.filter(Files::isRegularFile)
                    .filter(chemin -> chemin.toString().endsWith(".java"))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Sources illisibles : " + SOURCES, e);
        }
    }

    private static String lire(Path source) {
        try {
            return Files.readString(source, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Source illisible : " + source, e);
        }
    }

    private static String chemin(Path source) {
        return SOURCES.relativize(source).toString().replace('\\', '/');
    }
}
