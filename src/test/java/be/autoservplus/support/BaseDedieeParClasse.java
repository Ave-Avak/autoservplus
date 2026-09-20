package be.autoservplus.support;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Donne a chaque classe d integration sa propre base de donnees dans le conteneur
 * partage de {@link SocleIntegration}.
 *
 * <p><b>Ce que cette fabrique protege.</b> La cle du cache de contexte de Spring ne
 * contient pas la classe de test : deux classes aux memes annotations partagent un
 * contexte. En y injectant un {@link ContextCustomizer} dont l egalite depend de la
 * classe racine, on force un contexte — donc une base — par classe. Sans cela, les
 * vingt-six classes {@code @SpringBootTest @AutoConfigureMockMvc} se retrouveraient
 * dans une seule base et se marcheraient dessus.</p>
 */
public class BaseDedieeParClasse implements ContextCustomizerFactory {

    /**
     * Bases deja creees dans cette JVM. Creer la base <b>une seule fois par nom</b>,
     * et non a chaque montage de contexte, n est pas une optimisation : une classe
     * {@code @Nested} peut provoquer un second montage pendant que sa classe hote
     * s execute, et un {@code DROP} a cet instant effacerait sous ses pieds les
     * donnees qu elle vient d ecrire — silencieusement.
     */
    private static final Set<String> DEJA_CREEES = ConcurrentHashMap.newKeySet();

    @Override
    public ContextCustomizer createContextCustomizer(
            Class<?> classeDeTest, List<ContextConfigurationAttributes> ignore) {
        Class<?> racine = racine(classeDeTest);
        // Le garde porte sur la RACINE, jamais sur la classe recue : une classe
        // @Nested n herite pas du socle, seule son hote en herite. Teste sur la classe
        // recue, ce garde rendait null pour les cas imbriques, qui repartaient alors
        // sur un localhost:5432 inexistant.
        if (!SocleIntegration.class.isAssignableFrom(racine)) {
            return null;
        }
        return new BaseDediee(nomDeBase(racine));
    }

    /**
     * Classe englobante la plus externe. Une classe {@code @Nested} doit tomber sur la
     * base de son hote : le conteneur appartenait deja a la classe englobante, et les
     * cas imbriques partagent aujourd hui son etat.
     */
    private static Class<?> racine(Class<?> classe) {
        Class<?> courante = classe;
        while (courante.getEnclosingClass() != null) {
            courante = courante.getEnclosingClass();
        }
        return courante;
    }

    /**
     * Nom lisible et unique. Le nom simple aide a lire un journal ; l empreinte du nom
     * complet evite la collision entre deux classes homonymes de paquets differents.
     * PostgreSQL tronque les identifiants a 63 octets, d ou la coupe.
     */
    private static String nomDeBase(Class<?> racine) {
        String empreinte = Integer.toHexString(racine.getName().hashCode() & 0x7fffffff);
        String simple = racine.getSimpleName().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "_");
        String nom = "it_" + simple + "_" + empreinte;
        return nom.length() <= 63 ? nom : nom.substring(0, 63);
    }

    /**
     * Le customizer proprement dit. Son {@code equals} porte sur le seul nom de base :
     * c est lui qui decide du partage de contexte, et donc de l isolation.
     */
    private record BaseDediee(String nomBase) implements ContextCustomizer {

        @Override
        public void customizeContext(ConfigurableApplicationContext contexte,
                                     MergedContextConfiguration configuration) {
            creerBaseVierge();
            TestPropertyValues.of(
                            "spring.datasource.url=" + url(),
                            "spring.datasource.username=" + SocleIntegration.POSTGRES.getUsername(),
                            "spring.datasource.password=" + SocleIntegration.POSTGRES.getPassword(),
                            // Quatre et non dix (defaut Hikari) : les tests s executent
                            // en sequence, un contexte n a jamais besoin de dix
                            // connexions simultanees. Mais jusqu a 32 contextes restent
                            // en cache avec leur pool, tous sur le MEME serveur
                            // desormais : dix chacun demanderaient 320 connexions.
                            "spring.datasource.hikari.maximum-pool-size=4",
                            // Hikari aligne minimum-idle sur maximum-pool-size par
                            // defaut : chaque contexte en cache retiendrait donc quatre
                            // connexions oisives jusqu a la fin de la build. A zero,
                            // elles sont rendues au serveur.
                            "spring.datasource.hikari.minimum-idle=0",
                            // Ordonnanceur eteint : voir PlanificationConfig. Pose ici
                            // et non dans un application.yml de test, qui masquerait
                            // celui de production — src/test/resources n en contient
                            // volontairement aucun.
                            "autoservplus.planification.activee=false")
                    .applyTo(contexte);
        }

        /**
         * {@code DROP} puis {@code CREATE}, et une seule fois par nom de base : le
         * {@code DROP} met la base a neuf pour la classe qui commence, sans jamais
         * repasser derriere elle ensuite. {@code WITH (FORCE)} coupe les sessions
         * survivantes d une execution precedente — sans lui, un pool pas encore ferme
         * ferait echouer le DROP.
         */
        private void creerBaseVierge() {
            if (!DEJA_CREEES.add(nomBase)) {
                return;
            }
            try (Connection administration = DriverManager.getConnection(
                    SocleIntegration.POSTGRES.getJdbcUrl(),
                    SocleIntegration.POSTGRES.getUsername(),
                    SocleIntegration.POSTGRES.getPassword());
                 Statement ordre = administration.createStatement()) {
                ordre.execute("DROP DATABASE IF EXISTS " + nomBase + " WITH (FORCE)");
                ordre.execute("CREATE DATABASE " + nomBase);
            } catch (SQLException echec) {
                throw new IllegalStateException(
                        "Creation de la base de test " + nomBase + " impossible", echec);
            }
        }

        /**
         * URL reconstruite a partir de l hote et du port publie, et non deduite de
         * celle du conteneur : cette derniere porte la base par defaut et ses
         * parametres, qu il faudrait reecrire a l aveugle.
         */
        private String url() {
            return "jdbc:postgresql://" + SocleIntegration.POSTGRES.getHost()
                    + ":" + SocleIntegration.POSTGRES.getMappedPort(5432)
                    + "/" + nomBase;
        }

        @Override
        public boolean equals(Object autre) {
            return autre instanceof BaseDediee base && nomBase.equals(base.nomBase);
        }

        @Override
        public int hashCode() {
            return Objects.hash(nomBase);
        }
    }
}
