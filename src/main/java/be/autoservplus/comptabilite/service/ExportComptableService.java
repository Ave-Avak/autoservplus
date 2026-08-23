package be.autoservplus.comptabilite.service;

import be.autoservplus.comptabilite.repository.ExportComptableRepository;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.reservation.repository.ParametreAtelierRepository;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Export comptable au format CSV (BL-3).
 *
 * <p><b>Complete l export RGPD, ne le remplace pas.</b> Celui de F22 sert un droit de
 * la personne : il est declenche par le membre, ne contient que ses propres donnees,
 * et sort en JSON structure pour la portabilite (art. 20). Celui-ci sert le garage :
 * il est declenche par l administrateur, porte toutes les pieces d une periode, et
 * sort en CSV parce que le destinataire est un tableur comptable. Meme mot, deux
 * finalites, deux bases legales — d ou deux dispositifs.</p>
 *
 * <p>{@code @PreAuthorize} de classe en defense en profondeur : la protection d URL
 * {@code /admin/**} filtre deja le role, le service refuse en second. Ces fichiers
 * contiennent le nom et le chiffre d affaires de tous les clients.</p>
 *
 * <p>Bornes de periode converties dans le fuseau de l atelier, et {@code jusqua} rendu
 * <b>inclusif</b> : un comptable qui demande « du 1er au 31 » attend le 31 compris.</p>
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasRole('ADMINISTRATEUR')")
public class ExportComptableService {

    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ExportComptableRepository pieces;
    private final ParametreAtelierRepository parametres;
    private final MessageSource messages;

    public ExportComptableService(ExportComptableRepository pieces,
                                  ParametreAtelierRepository parametres,
                                  MessageSource messages) {
        this.pieces = pieces;
        this.parametres = parametres;
        this.messages = messages;
    }

    /** Journal des factures emises sur la periode. */
    public String facturesEnCsv(LocalDate depuis, LocalDate jusqua) {
        ZoneId zone = zone();
        RedacteurCsv csv = new RedacteurCsv(
                entete("numero"), entete("date"), entete("client"), entete("commande"),
                entete("htva"), entete("tva"), entete("tvac"), entete("taux"));

        pieces.facturesEmises(borneBasse(depuis, zone), borneHaute(jusqua, zone))
                .forEach(f -> csv.ligne(
                        f.getNumero(),
                        jour(f.getDateEmission(), zone),
                        nom(f.getMembre()),
                        f.getCommande() == null ? null : f.getCommande().getNumero(),
                        f.getMontantHtva(), f.getMontantTva(), f.getMontantTvac(),
                        f.getTauxTvaApplique()));
        return csv.texte();
    }

    /** Journal des commandes conclues sur la periode, annulations comprises. */
    public String commandesEnCsv(LocalDate depuis, LocalDate jusqua) {
        ZoneId zone = zone();
        RedacteurCsv csv = new RedacteurCsv(
                entete("numero"), entete("date"), entete("client"), entete("statut"),
                entete("htva"), entete("tva"), entete("tvac"), entete("paiement"));

        pieces.commandesConclues(borneBasse(depuis, zone), borneHaute(jusqua, zone))
                .forEach(c -> csv.ligne(
                        c.getNumero(),
                        jour(c.getDateCommande(), zone),
                        nom(c.getMembre()),
                        message("commande.statut." + c.getStatut().name()),
                        c.getMontantHtva(), c.getMontantTva(), c.getMontantTvac(),
                        c.getDatePaiement() == null ? null : jour(c.getDatePaiement(), zone)));
        return csv.texte();
    }

    /**
     * Nom du client tel qu il figure sur la piece.
     *
     * <p>Un compte anonymise (F23) porte deja un prenom et un nom neutralises : la
     * ligne comptable reste, le titulaire n est plus identifiable. C est exactement
     * l effet recherche, et rien n est a filtrer ici.</p>
     */
    private static String nom(Utilisateur membre) {
        return "%s %s".formatted(membre.getPrenom(), membre.getNom());
    }

    private ZoneId zone() {
        return parametres.courants().zone();
    }

    private static Instant borneBasse(LocalDate depuis, ZoneId zone) {
        return depuis.atStartOfDay(zone).toInstant();
    }

    private static Instant borneHaute(LocalDate jusqua, ZoneId zone) {
        return jusqua.plusDays(1).atStartOfDay(zone).toInstant();
    }

    private static String jour(Instant instant, ZoneId zone) {
        return JOUR.format(instant.atZone(zone).toLocalDate());
    }

    private String entete(String suffixe) {
        return message("admin.export.colonne." + suffixe);
    }

    private String message(String cle) {
        return messages.getMessage(cle, null, LocaleContextHolder.getLocale());
    }
}
