package be.autoservplus.config;

import be.autoservplus.i18n.LanguesSupportees;
import be.autoservplus.i18n.ResolveurLangueSession;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

/**
 * Changement de langue de l interface (F6) : francais, neerlandais, anglais.
 *
 * <p>Les trois fichiers de messages etaient complets et la colonne
 * {@code utilisateur.langue} etait deja lue pour les documents PDF, mais
 * <b>aucun moyen n existait de choisir la langue du site</b> : le projet n avait ni
 * {@code LocaleResolver} ni {@code WebMvcConfigurer}, et seul l en-tete
 * {@code Accept-Language} du navigateur decidait. C etait un MUST du cahier des
 * charges non resolu, et la cause d une non-conformite <b>WCAG 3.1.1</b> : chaque
 * gabarit annoncait {@code lang="fr"} en dur, y compris lorsqu il servait du
 * neerlandais.</p>
 *
 * <p><b>Aucun cookie de langue.</b> La preference tient dans la session, deja
 * couverte par les cookies strictement necessaires declares au bandeau F25. Poser un
 * cookie supplementaire obligerait a trancher s il releve de l exemption de
 * consentement, c est-a-dire a rouvrir un sujet regle, pour un confort. La
 * persistance durable existe deja par ailleurs, en base, pour les membres.</p>
 *
 * <p><b>La preference du profil n est PAS appliquee ici.</b> Elle l est a la
 * connexion seule, par {@code LangueApresConnexionHandler}, qui documente pourquoi :
 * appliquee a chaque requete authentifiee, elle servait du francais a tout membre
 * connecte — la colonne vaut {@code fr} par defaut et aucun ecran ne l ecrit — et
 * faisait donc perdre l en-tete du navigateur a ceux qui n avaient jamais rien
 * choisi. Quatre tests deja en place l ont montre.</p>
 */
@Configuration
public class InternationalisationConfig implements WebMvcConfigurer {

    /** Nom du parametre de changement de langue, aussi employe par le selecteur. */
    public static final String PARAMETRE_LANGUE = "lang";

    /**
     * Remplace le resolveur par defaut de Spring Boot, qui lit l en-tete du
     * navigateur sans jamais rien memoriser : un choix exprime y serait perdu des la
     * page suivante.
     *
     * <p>{@code defaultLocale} n est deliberement pas renseigne : la valeur de repli
     * est calculee par {@code ResolveurLangueSession.determineDefaultLocale}, qui
     * consulte l en-tete du navigateur puis le ramene a l ensemble admis. Fixer ici
     * un defaut fige aurait servi du francais a un visiteur neerlandophone qui n a
     * encore rien choisi, alors que le comportement d avant F6 lui donnait du
     * neerlandais. Le repli reste {@link LanguesSupportees#DEFAUT} des que l en-tete
     * ne designe aucune langue connue.</p>
     */
    @Bean
    public LocaleResolver localeResolver() {
        return new ResolveurLangueSession();
    }

    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName(PARAMETRE_LANGUE);
        // Un etiquetage de langue mal forme ne doit pas produire une erreur : le
        // parametre vient de l URL, donc de n importe qui. La valeur est ignoree et
        // la page se rend normalement. Les valeurs bien formees mais hors perimetre
        // sont ramenees au francais par ResolveurLangueSession.setLocale.
        interceptor.setIgnoreInvalidLocale(true);
        return interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registre) {
        registre.addInterceptor(localeChangeInterceptor());
    }
}
