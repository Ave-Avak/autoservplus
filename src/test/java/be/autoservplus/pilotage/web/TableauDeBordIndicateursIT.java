package be.autoservplus.pilotage.web;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.Traductions;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PieceRepository;
import be.autoservplus.identite.domain.StatutUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.reservation.repository.ParametreAtelierRepository;
import be.autoservplus.support.MembresDeTest;
import be.autoservplus.support.SocleIntegration;
import be.autoservplus.vente.domain.StatutPaiement;
import be.autoservplus.vente.service.PanierService;
import be.autoservplus.vente.service.PrestatairePaiementFictif;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Indicateurs du tableau de bord (A25) sur une activité connue, de l'écran à l'export.
 *
 * <p>La base de la classe ne porte que les migrations : aucune facture, aucun
 * rendez-vous, aucun membre. Le test y sème une journée d'activité, aujourd'hui, et lit
 * les chiffres qu'il doit retrouver.</p>
 *
 * <ul>
 *   <li>deux commandes payées de deux pièces à 19,99 €, donc deux factures de 39,98 €
 *       hors TVA et 48,38 € TVA comprise.</li>
 *   <li>six rendez-vous : confirmé, honoré, annulé par le membre, annulé par le garage,
 *       absent, refusé.</li>
 *   <li>trois nouveaux clients : l'acheteur, un compte vérifié aujourd'hui, un compte
 *       inséré vérifié sans date. Un compte non vérifié et un compte vérifié il y a
 *       quarante jours n'en sont pas.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Indicateurs du tableau de bord (intégration)")
class TableauDeBordIndicateursIT extends SocleIntegration {

    private static final String ADMIN = "pilotage-admin@exemple.be";
    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private CategorieRepository categories;
    @Autowired private PieceRepository pieces;
    @Autowired private PanierService panierService;
    @Autowired private PrestatairePaiementFictif prestataireFictif;
    @Autowired private TransactionTemplate transactions;
    @Autowired private ParametreAtelierRepository parametres;

    private LocalDate aujourdhui;

    @BeforeAll
    void semer() throws Exception {
        ZoneId zone = transactions.execute(statut -> parametres.courants().zone());
        aujourdhui = LocalDate.now(zone);
        Instant minuit = aujourdhui.atStartOfDay(zone).toInstant();

        Utilisateur acheteur = transactions.execute(statut -> {
            Utilisateur nouveau = MembresDeTest.membre("pilotage-acheteur@exemple.be", "{noop}inutilise",
                    "Dupont", "Marie");
            nouveau.confirmerAdresseEmail();
            nouveau.setStatut(StatutUtilisateur.ACTIF);
            return utilisateurs.saveAndFlush(nouveau);
        });
        Piece piece = transactions.execute(statut -> {
            Categorie filtration = new Categorie("IT-PIL-CAT", "Filtration du test", TypeCategorie.PIECE);
            filtration.traduire("Testfiltratie", null);
            Categorie categorie = categories.save(filtration);
            Piece nouvelle = new Piece(categorie, "IT-PIL-1", "Filtre du test", new BigDecimal("19.99"));
            nouvelle.setQuantiteStock(50);
            nouvelle.traduire(new Traductions("Testfilter", null, null, null));
            return pieces.save(nouvelle);
        });
        commandePayee(acheteur, piece);
        // La seconde commande est conclue en néerlandais : ses lignes se figent sous un
        // autre libellé que celles de la première.
        jdbc.update("UPDATE utilisateur SET langue = 'nl' WHERE email = ?", acheteur.getEmail());
        commandePayee(acheteur, piece);
        // Ni l'une ni l'autre n'est facturée : elles n'entrent dans aucune vente.
        commander(acheteur, piece);
        UUID annulee = commander(acheteur, piece);
        jdbc.update("UPDATE commande SET statut = 'ANNULEE', motif_annulation = 'TIMEOUT_PAIEMENT', "
                + "date_annulation = now() WHERE reference = ?", annulee);

        membre("pilotage-verifie@exemple.be", true, Timestamp.from(minuit.plusSeconds(60)), Timestamp.from(minuit.minusSeconds(86_400L * 3)));
        membre("pilotage-sans-date@exemple.be", true, null, Timestamp.from(minuit.plusSeconds(120)));
        membre("pilotage-non-verifie@exemple.be", false, null, Timestamp.from(minuit.plusSeconds(180)));
        membre("pilotage-ancien@exemple.be", true, Timestamp.from(minuit.minusSeconds(86_400L * 40)), Timestamp.from(minuit.plusSeconds(240)));

        Long membreId = jdbc.queryForObject("SELECT id FROM utilisateur WHERE email = 'pilotage-acheteur@exemple.be'", Long.class);
        Long poste = jdbc.queryForObject("SELECT id FROM poste_atelier WHERE actif ORDER BY id LIMIT 1", Long.class);
        jdbc.update("INSERT INTO vehicule (membre_id, plaque, marque, modele, motorisation) VALUES (?, '1-PIL-001', 'Volkswagen', 'Golf', 'ESSENCE')", membreId);
        Long vehicule = jdbc.queryForObject("SELECT id FROM vehicule WHERE plaque = '1-PIL-001'", Long.class);
        String[][] rendezVous = {
                {"CONFIRME", null}, {"HONORE", null}, {"ANNULE", null},
                {"ANNULE", "Pont en panne"}, {"ABSENT", null}, {"REFUSE", "Prestation non assurée"}};
        for (int i = 0; i < rendezVous.length; i++) {
            Instant debut = minuit.plusSeconds(3600L * (8 + i));
            jdbc.update("""
                    INSERT INTO rdv (numero, membre_id, vehicule_id, poste_id, statut, motif_refus, debut, fin)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, "RDV-PIL-" + i, membreId, vehicule, poste, rendezVous[i][0], rendezVous[i][1],
                    Timestamp.from(debut), Timestamp.from(debut.plusSeconds(1800)));
        }
    }

    private void membre(String email, boolean verifie, Timestamp verifieLe, Timestamp creeLe) {
        jdbc.update("""
                INSERT INTO utilisateur (type_utilisateur, email, mot_de_passe_hache, nom, prenom, statut,
                                         email_verifie, email_verifie_le, created_at)
                VALUES ('MEMBRE', ?, '{noop}inutilise', 'Test', 'Pilotage', ?, ?, ?, ?)
                """, email, verifie ? "ACTIF" : "EN_ATTENTE_VALIDATION", verifie, verifieLe, creeLe);
    }

    /** Panier, commande, paiement confirmé : la commande ressort payée et facturée. */
    private void commandePayee(Utilisateur acheteur, Piece piece) throws Exception {
        UUID reference = commander(acheteur, piece);
        String paiement = mvc.perform(post("/commande/{ref}/payer", reference)
                        .with(user(acheteur.getEmail())).with(csrf()))
                .andReturn().getResponse().getRedirectedUrl();
        String referencePrestataire = paiement.substring(paiement.lastIndexOf('/') + 1);
        prestataireFictif.programmerStatut(referencePrestataire, StatutPaiement.REUSSI);
        mvc.perform(post("/webhooks/paiement").with(anonymous()).param("id", referencePrestataire))
                .andExpect(status().isOk());
    }

    /** Panier puis commande de deux pièces, laissée en attente de paiement. */
    private UUID commander(Utilisateur acheteur, Piece piece) throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                acheteur.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_MEMBRE"))));
        try {
            panierService.ajouterPiece(acheteur.getEmail(), piece.getReference(), 2);
        } finally {
            SecurityContextHolder.clearContext();
        }
        String redirection = mvc.perform(post("/commande").with(user(acheteur.getEmail()))
                        .with(csrf()).param("cgv", "true"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        return UUID.fromString(redirection.replace("/commande/", "").replace("/confirmation", ""));
    }

    private String ecran(String adresse, Locale langue) throws Exception {
        return mvc.perform(get(adresse).with(user(ADMIN).roles("ADMINISTRATEUR")).locale(langue))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("mois en cours : chiffre d'affaires HTVA et TVAC, panier moyen, nouveaux clients")
    void indicateursDuMois() throws Exception {
        String page = ecran("/admin", Locale.FRENCH);

        assertThat(page).contains("79,96 €").contains("96,76 €");
        assertThat(page).contains("id=\"indicateur-panier\">39,98 €<");
        assertThat(page).contains("id=\"indicateur-clients\">3<");
        assertThat(page).contains("id=\"indicateur-rendez-vous\">6<");
        assertThat(page).contains("aria-current=\"true\">Ce mois<");
    }

    @Test
    @DisplayName("taux d'annulation : deux annulés sur six, le membre et le garage distingués, absence et refus à part")
    void annulations() throws Exception {
        String page = ecran("/admin?periode=jour", Locale.FRENCH);

        assertThat(page).contains("id=\"indicateur-annulation\">33 %<");
        assertThat(page).contains("2 annulés sur 6 rendez-vous : 1 par le membre, 1 par le garage.");
        assertThat(page).contains("Comptés à part du taux : 1 absence, 1 demande refusée par le garage.");
        assertThat(page).contains("Annulé : 2");
        assertThat(page).contains("aria-current=\"true\">Aujourd&#39;hui<");
    }

    @Test
    @DisplayName("pièces les plus vendues : les commandes facturées, ni l'impayée ni l'annulée")
    void piecesVenduesFacturees() throws Exception {
        String page = ecran("/admin", Locale.FRENCH);

        // Quatre commandes de deux pièces, deux facturées : quatre pièces, le montant
        // du chiffre d'affaires de leur catégorie.
        assertThat(page).contains("Filtre du test : 4 vendues, 79,96 € HTVA");
        assertThat(page).contains("1 commande en attente, soit 48,38 € TVAC");
        assertThat(page).contains("Prestations les plus réalisées");
        assertThat(page).doesNotContain("(s)");
    }

    @Test
    @DisplayName("une pièce vendue en français et en néerlandais tient un seul rang, nommée dans la langue de l'écran")
    void classementParArticle() throws Exception {
        assertThat(jdbc.queryForList("""
                SELECT DISTINCT l.libelle_fige FROM ligne_panier l
                JOIN facture f ON f.commande_id = l.commande_id
                JOIN piece p ON p.id = l.piece_id WHERE p.reference_fabricant = 'IT-PIL-1'
                ORDER BY 1""", String.class))
                .as("deux libellés figés pour la même pièce")
                .containsExactly("Filtre du test", "Testfilter");

        String neerlandais = ecran("/admin", new Locale("nl"));
        assertThat(neerlandais).contains("Testfilter").doesNotContain("Filtre du test");
        assertThat(neerlandais).contains("Testfiltratie").doesNotContain("Filtration du test");

        // Sans traduction anglaise, le libellé français.
        String anglais = ecran("/admin", Locale.ENGLISH);
        assertThat(anglais).contains("Filtre du test").doesNotContain("Testfilter");
        assertThat(anglais).contains("Filtration du test");

        String csv = new String(mvc.perform(get("/admin/tableau-de-bord/indicateurs.csv").param("periode", "mois")
                        .with(user(ADMIN).roles("ADMINISTRATEUR")).locale(new Locale("nl")))
                .andReturn().getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(csv).contains(";Testfilter;4\r\n").contains(";Testfiltratie;79,96\r\n");
    }

    @Test
    @DisplayName("courbe et histogramme en SVG, chacun doublé de son tableau de valeurs")
    void graphiques() throws Exception {
        String page = ecran("/admin", Locale.FRENCH);

        assertThat(page).contains("id=\"courbe-chiffre\"").contains("role=\"img\"");
        assertThat(page).containsPattern("<polyline class=\"graphique-ligne\" points=\"64\\.0,212\\.0 [0-9., ]+ 624\\.0,52\\.1\"");
        assertThat(page).contains("id=\"histogramme-categories\"").contains("Filtration du test : 79,96 €");
        assertThat(page).containsPattern("<rect class=\"graphique-barre\" x=\"0\" y=\"22\\.0\" width=\"640\\.0\" height=\"14\"");
        assertThat(page).contains("<td>" + aujourdhui.format(JOUR) + "</td>");
        assertThat(page).contains("<td>Filtration du test</td>");
        assertThat(page).as("aucun style en ligne ni script : la CSP les refuserait")
                .doesNotContain(" style=\"").doesNotContain("<script>");
    }

    @Test
    @DisplayName("une période sans activité : zéros, taux et panier dits non calculables, courbe conservée")
    void periodeVide() throws Exception {
        String page = ecran("/admin?du=2000-01-01&au=2000-01-31", Locale.FRENCH);

        assertThat(page).contains("Du 01/01/2000 au 31/01/2000");
        assertThat(page).contains("id=\"indicateur-clients\">0<");
        assertThat(page).contains("id=\"indicateur-annulation\">Non calculable, aucun rendez-vous<");
        assertThat(page).contains("id=\"indicateur-panier\">Aucune commande payée<");
        assertThat(page).contains("Aucune vente facturée sur la période.");
        assertThat(page).doesNotContain("id=\"histogramme-categories\"");
        assertThat(page).as("la courbe des trente jours ne suit pas la période").contains("id=\"courbe-chiffre\"");
        assertThat(page).contains("value=\"2000-01-01\"").as("aucun raccourci n'est actif").doesNotContain("filtre-actif");
        assertThat(page).contains("/admin/tableau-de-bord/indicateurs.csv?du=2000-01-01&amp;au=2000-01-31");
    }

    @Test
    @DisplayName("période inversée : message, indicateurs à zéro")
    void periodeInversee() throws Exception {
        String page = ecran("/admin?du=2026-02-01&au=2026-01-01", Locale.FRENCH);

        assertThat(page).contains("La date de début est postérieure à la date de fin");
        assertThat(page).contains("id=\"indicateur-rendez-vous\">0<");
    }

    @Test
    @DisplayName("une date mal formée répond 400")
    void dateMalFormee() throws Exception {
        mvc.perform(get("/admin").param("du", "pas-une-date").with(user(ADMIN).roles("ADMINISTRATEUR")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("l'écran se rend en néerlandais et en anglais")
    void autresLangues() throws Exception {
        String neerlandais = ecran("/admin", new Locale("nl"));
        assertThat(neerlandais).contains("Nieuwe klanten").contains("Deze maand").contains("79,96 €");
        assertThat(neerlandais).contains("2 geannuleerd op 6 afspraken: 1 door het lid, 1 door de garage.");
        assertThat(neerlandais).contains("1 afwezigheid, 1 aanvraag geweigerd door de garage.");
        assertThat(neerlandais).contains("1 bestelling in afwachting");
        assertThat(neerlandais).doesNotContain("Indicateurs").doesNotContain("Nouveaux clients");

        String anglais = ecran("/admin", Locale.ENGLISH);
        assertThat(anglais).contains("New customers").contains("79.96 €").contains("This month");
        assertThat(anglais).contains("1 no-show, 1 request declined by the garage.");
        assertThat(anglais).contains("1 order pending");
    }

    @Test
    @DisplayName("export des indicateurs : les chiffres de l'écran, libellés dans la langue de l'administrateur")
    void exportIndicateurs() throws Exception {
        MvcResult reponse = mvc.perform(get("/admin/tableau-de-bord/indicateurs.csv").param("periode", "mois")
                        .with(user(ADMIN).roles("ADMINISTRATEUR")).locale(Locale.FRENCH))
                .andExpect(status().isOk()).andReturn();
        String csv = new String(reponse.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);

        assertThat(reponse.getResponse().getContentType()).startsWith("text/csv");
        assertThat(reponse.getResponse().getHeader("Content-Disposition")).contains("attachment").contains("tableau-de-bord-");
        assertThat(csv).startsWith("﻿Rubrique;Indicateur;Valeur\r\n");
        assertThat(csv).contains(";CA facturé HTVA;79,96\r\n").contains(";CA facturé TVAC;96,76\r\n");
        assertThat(csv).contains(";Nouveaux clients;3\r\n");
        assertThat(csv).contains(";Panier moyen HTVA, hors avoirs;39,98\r\n");
        assertThat(csv).contains(";Taux d'annulation des rendez-vous (%);33\r\n");
        assertThat(csv).contains(";Rendez-vous annulés par le garage;1\r\n");
        assertThat(csv).contains("Chiffre d'affaires par catégorie;Filtration du test;79,96\r\n");
        assertThat(csv).contains("Pièces les plus vendues;Filtre du test;4\r\n");

        String neerlandais = new String(mvc.perform(get("/admin/tableau-de-bord/indicateurs.csv")
                        .with(user(ADMIN).roles("ADMINISTRATEUR")).locale(new Locale("nl")))
                .andReturn().getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(neerlandais).startsWith("﻿Rubriek;Indicator;Waarde\r\n").contains(";Nieuwe klanten;3\r\n");
    }

    @Test
    @DisplayName("export quotidien : trente jours, le dernier étant aujourd'hui avec son chiffre")
    void exportQuotidien() throws Exception {
        String csv = new String(mvc.perform(get("/admin/tableau-de-bord/chiffre-quotidien.csv")
                        .with(user(ADMIN).roles("ADMINISTRATEUR")).locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);

        String[] lignes = csv.split("\r\n");
        assertThat(lignes).hasSize(31);
        assertThat(lignes[0]).isEqualTo("﻿Jour;CA facturé HTVA");
        assertThat(lignes[1]).isEqualTo(aujourdhui.minusDays(29).format(JOUR) + ";0");
        assertThat(lignes[30]).isEqualTo(aujourdhui.format(JOUR) + ";79,96");
    }

    @Test
    @DisplayName("les exports sont refusés à un membre")
    void exportsReserves() throws Exception {
        for (String adresse : List.of("/admin/tableau-de-bord/indicateurs.csv", "/admin/tableau-de-bord/chiffre-quotidien.csv")) {
            mvc.perform(get(adresse).with(user("membre@exemple.be").roles("MEMBRE")))
                    .andExpect(status().isForbidden());
        }
    }
}
