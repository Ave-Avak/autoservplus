package be.autoservplus.support;

import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Socle commun aux classes d integration : <b>un seul</b> conteneur PostgreSQL pour
 * toute la build, et une <b>base de donnees dediee par classe de test</b>.
 *
 * <h2>Le defaut corrige</h2>
 *
 * <p>Chaque classe {@code *IT} declarait son propre {@code @Container} : la build
 * demarrait <b>49 conteneurs</b> et 49 pools de connexions. Chacun etait arrete des sa
 * classe terminee, mais le contexte Spring correspondant, lui, restait en cache
 * jusqu a la fin de la JVM — avec son ordonnanceur vivant, qui interrogeait toutes les
 * minutes une base eteinte. C est ce qui retardait l arret de la JVM de test, faisait
 * survivre des JVM a un build interrompu, et a fini par produire un
 * {@code BUILD SUCCESS} sur une fraction des tests (registre §4).</p>
 *
 * <h2>Pourquoi une base par CLASSE et non par contexte</h2>
 *
 * <p>Le conteneur est partage, l isolation ne l est pas. Elle reste <b>exactement</b>
 * celle d avant : chaque classe voit un schema vierge. Ce n est pas du confort —
 * <b>31 des 49 classes emploient des adresses fixes</b> ({@code marie@exemple.be}…)
 * sur une colonne unique, <b>33 ne sont pas {@code @Transactional}</b> et committent
 * pour de bon, et deux d entre elles vident des tables de reference : {@code
 * RdvServiceIT} supprime toutes les plages et tous les postes, {@code
 * NumerotationFactureIT} vide {@code compteur_facture}. Une base commune les ferait
 * tomber des la deuxieme classe executee.</p>
 *
 * <p><b>Le piege a connaitre</b> : la cle du cache de contexte de Spring ne contient
 * PAS la classe de test. Sans precaution, toutes les classes portant les memes
 * annotations partageraient un contexte — donc une base — et l isolation ci-dessus
 * serait perdue en silence. C est {@link BaseDedieeParClasse} qui l empeche, en
 * faisant dependre la cle de la classe racine. Aujourd hui le meme effet etait obtenu
 * par accident, chaque {@code @ServiceConnection} differant d une classe a l autre.</p>
 *
 * <h2>Classes {@code @Nested}</h2>
 *
 * <p>Vingt-et-une classes d integration en portent. Une classe {@code @Nested}
 * <b>n herite pas</b> de cette classe — seule son hote en herite — donc la fabrique
 * raisonne sur la classe <b>englobante racine</b>, jamais sur la classe de test
 * recue. Un garde pose sur la classe recue laisserait les cas imbriques sans source
 * de donnees, et ils echoueraient sur un {@code localhost:5432} de repli.</p>
 *
 * <h2>Arret</h2>
 *
 * <p>Aucun {@code stop()} : le conteneur est demarre une fois dans un bloc statique et
 * confie a Ryuk, qui le supprime a la mort de la JVM. L arreter explicitement
 * supposerait de savoir quelle classe s execute en dernier — ce que rien ne garantit.</p>
 */
public abstract class SocleIntegration {

    /**
     * Conteneur unique, demarre au premier chargement de cette classe.
     *
     * <p>{@code max_connections} est releve a 300. Le defaut de PostgreSQL est 100, et
     * il suffisait tant que chaque contexte avait SON serveur. Ils visent desormais le
     * meme : le cache de contexte de Spring en garde jusqu a 32, chacun avec son pool
     * plafonne a quatre connexions (voir {@link BaseDedieeParClasse}), soit 128 au
     * pire — plus les connexions d administration transitoires et les trois places que
     * PostgreSQL reserve au superutilisateur. 300 laisse donc une marge de plus du
     * double, sans consommer de memoire tant que les connexions ne sont pas ouvertes.</p>
     */
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withCommand("postgres", "-c", "max_connections=300");

    static {
        POSTGRES.start();
    }
}
