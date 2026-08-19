package be.autoservplus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Point d entree de la plateforme AutoServ+.
 *
 * <p>L application suit une architecture de monolithe modulaire : chaque package de
 * premier niveau correspond a un domaine metier identifie dans le rapport d analyse
 * UML (livrable 07), et les dependances entre modules passent exclusivement par la
 * couche service.</p>
 */
@SpringBootApplication
public class AutoServPlusApplication {

    public static void main(String[] args) {
        SpringApplication.run(AutoServPlusApplication.class, args);
    }
}
