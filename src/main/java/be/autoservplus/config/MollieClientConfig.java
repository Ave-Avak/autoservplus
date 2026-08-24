package be.autoservplus.config;

import be.autoservplus.vente.service.MollieGateway;
import be.autoservplus.vente.service.SiPrestataireConfigure;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Client HTTP dedie au prestataire de paiement, construit uniquement lorsqu un
 * identifiant est configure.
 *
 * <p><b>Un {@code RestClient} deja arme plutot qu un {@code RestClient.Builder}
 * injecte dans la passerelle.</b> L en-tete d autorisation et les delais sont ainsi
 * poses une fois, hors de la passerelle : celle-ci ne manipule jamais le jeton, elle
 * ne fait qu emettre des requetes deja authentifiees. C est aussi ce qui rend la
 * passerelle testable en substituant ce seul bean, sans variable d environnement ni
 * reseau.</p>
 *
 * <p>Les delais sont bornes des l etablissement de la connexion : un prestataire qui
 * ne repond pas doit echouer vite. Sans borne, un appel sortant immobiliserait un
 * thread de traitement — et, pour la relecture de statut, une connexion de base de
 * donnees avec lui.</p>
 */
@Configuration
@SiPrestataireConfigure
public class MollieClientConfig {

    /** Racine de l API v2 de Mollie. Les chemins employes lui sont relatifs. */
    private static final String URL_API = "https://api.mollie.com/v2";

    @Bean
    public RestClient clientMollie(MollieProprietes proprietes) {
        return RestClient.builder()
                .baseUrl(URL_API)
                .requestFactory(ClientHttpRequestFactoryBuilder.detect()
                        .build(ClientHttpRequestFactorySettings.defaults()
                                .withConnectTimeout(MollieGateway.DELAI_MAXIMUM)
                                .withReadTimeout(MollieGateway.DELAI_MAXIMUM)))
                // Le jeton ne vit que dans cet en-tete par defaut : aucune methode de
                // la passerelle ne le recoit, ne le recopie ni ne peut le journaliser.
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + proprietes.jeton())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
