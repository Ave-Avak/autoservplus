package be.autoservplus.devis.service;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.devis.domain.Devis;
import be.autoservplus.identite.domain.Langue;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.reservation.domain.Motorisation;
import be.autoservplus.reservation.domain.Vehicule;
import be.autoservplus.support.GaragesDeTest;
import be.autoservplus.vente.domain.Panier;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contenu du PDF d'un devis, lu dans le PDF. Le {@code MessageSource} est le vrai : une
 * clé manquante dans une langue s'y verrait.
 */
@DisplayName("GenerateurPdfDevis")
class GenerateurPdfDevisTest {

    private static final LocalDate JOUR = LocalDate.of(2026, 11, 24);

    private final GenerateurPdfDevis generateur =
            new GenerateurPdfDevis(messagesReels(), GaragesDeTest.bruxellois());

    private static ResourceBundleMessageSource messagesReels() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("i18n/messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        return source;
    }

    private static Devis devis(Langue langue) {
        Utilisateur marie = new Utilisateur("marie@exemple.be", "$2a$12$h", "Dupont", "Marie",
                TypeUtilisateur.MEMBRE);
        Categorie categorie = new Categorie("PIECES", "Pièces", TypeCategorie.PIECE);
        Piece filtre = new Piece(categorie, "F-1", "Filtre à huile", new BigDecimal("9.99"));
        filtre.setTauxTva(new BigDecimal("21"));
        filtre.setQuantiteStock(10);
        Panier panier = new Panier(marie);
        panier.ajouterPiece(filtre, 2);
        Vehicule golf = new Vehicule(marie, "1-ABC-123", "Volkswagen", "Golf", Motorisation.DIESEL);
        return Devis.depuisLePanier("DEV-2026-0007", panier, golf,
                Instant.parse("2026-11-24T09:30:00Z"), JOUR, langue);
    }

    private static String texteDu(byte[] pdf) throws IOException {
        PdfReader lecteur = new PdfReader(pdf);
        try {
            StringBuilder texte = new StringBuilder();
            PdfTextExtractor extracteur = new PdfTextExtractor(lecteur);
            for (int page = 1; page <= lecteur.getNumberOfPages(); page++) {
                texte.append(extracteur.getTextFromPage(page)).append('\n');
            }
            return texte.toString().replaceAll("\\s+", " ");
        } finally {
            lecteur.close();
        }
    }

    @Test
    @DisplayName("porte le numéro, la date d'émission, la validité, le client et le véhicule")
    void identification() throws Exception {
        String texte = texteDu(generateur.engendrer(devis(Langue.fr), JOUR));

        assertThat(texte).contains("DEVIS", "DEV-2026-0007", "24 novembre 2026", "24 décembre 2026",
                "Marie Dupont", "Volkswagen Golf", "1-ABC-123");
    }

    @Test
    @DisplayName("porte l'identité du garage et ses coordonnées")
    void identiteDuGarage() throws Exception {
        String texte = texteDu(generateur.engendrer(devis(Langue.fr), JOUR));

        assertThat(texte).contains("Garage de test SRL", "0123.456.789", "BE0123456789",
                "+32 2 000 00 00", "garage@exemple.be");
    }

    @Test
    @DisplayName("porte les lignes au prix taxe comprise, les totaux et la mention de validité")
    void lignesTotauxEtMention() throws Exception {
        String texte = texteDu(generateur.engendrer(devis(Langue.fr), JOUR));

        assertThat(texte).contains("Filtre à huile", "12,09", "24,18",
                "Prix en vigueur à la date d'émission, valables 30 jours sous réserve de disponibilité.",
                "Ce devis n'est ni une commande ni une facture.");
    }

    @Test
    @DisplayName("s'écrit en néerlandais pour un document en néerlandais")
    void neerlandais() throws Exception {
        String texte = texteDu(generateur.engendrer(devis(Langue.nl), JOUR));

        assertThat(texte).contains("OFFERTE", "Geldig tot", "24 december 2026",
                "Prijzen van kracht op de uitgiftedatum, 30 dagen geldig");
    }
}
