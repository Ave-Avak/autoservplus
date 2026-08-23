package be.autoservplus.journal.service;

import be.autoservplus.journal.repository.JournalRepository;
import be.autoservplus.journal.service.dto.EntreeJournal;
import be.autoservplus.reservation.repository.ParametreAtelierRepository;
import be.autoservplus.reservation.service.support.FormatageRdv;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Journal d audit du garage (BL-7), en <b>consultation seule</b>.
 *
 * <p>Rassemble les deux traces historisees du projet : les modifications de catalogue
 * (V25, une ligne par champ reellement change) et les transitions d intervention
 * (V23, append-only, ecrites dans la transaction de chaque transition). Ni l une ni
 * l autre n est modifiable depuis l application, et ce service n expose aucune
 * ecriture — un journal qu on peut retoucher ne prouve rien.</p>
 *
 * <p>{@code @PreAuthorize} de classe en defense en profondeur : la protection d URL
 * {@code /admin/**} filtre deja le role, le service refuse en second. Le journal
 * nomme des personnes et decrit des decisions internes.</p>
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasRole('ADMINISTRATEUR')")
public class JournalService {

    /**
     * Plafond de lignes rendues. Le journal se consulte par filtre, pas par defilement
     * infini : sans borne, la page grossirait avec l historique jusqu a devenir
     * inutilisable, et c est le filtre qui doit repondre a « que s est-il passe ».
     */
    public static final int LIMITE = 200;

    private final JournalRepository journal;
    private final ParametreAtelierRepository parametres;
    private final MessageSource messages;

    public JournalService(JournalRepository journal, ParametreAtelierRepository parametres,
                          MessageSource messages) {
        this.journal = journal;
        this.parametres = parametres;
        this.messages = messages;
    }

    /**
     * Entrees correspondant aux filtres, de la plus recente a la plus ancienne.
     *
     * <p>Les bornes de date sont fournies en jours locaux et converties dans le fuseau
     * de l atelier ; {@code jusqua} est rendu <b>inclusif</b> en avancant d un jour, ce
     * que l utilisateur attend d un champ « jusqu au ».</p>
     */
    public List<EntreeJournal> rechercher(String type, String acteur,
                                          LocalDate depuis, LocalDate jusqua) {
        ZoneId zone = parametres.courants().zone();
        Instant borneBasse = depuis == null ? null : depuis.atStartOfDay(zone).toInstant();
        Instant borneHaute = jusqua == null ? null : jusqua.plusDays(1).atStartOfDay(zone).toInstant();

        return journal.rechercher(type, acteur, borneBasse, borneHaute, LIMITE).stream()
                .map(ligne -> versEntree(ligne, zone))
                .toList();
    }

    private EntreeJournal versEntree(Object[] ligne, ZoneId zone) {
        Instant horodatage = ((Timestamp) ligne[0]).toInstant();
        String type = (String) ligne[1];
        String prenom = (String) ligne[2];
        String nom = (String) ligne[3];
        String cible = (String) ligne[4];
        String champ = (String) ligne[5];
        String avant = (String) ligne[6];
        String apres = (String) ligne[7];
        String motif = (String) ligne[8];

        return new EntreeJournal(
                FormatageRdv.jourLisible(horodatage, zone),
                type,
                prenom == null ? null : "%s %s".formatted(prenom, nom),
                cible,
                detail(type, champ, avant, apres, motif));
    }

    /**
     * Compose la colonne « ce qui a change ».
     *
     * <p>Une valeur absente est rendue par un libelle traduit et non par une chaine
     * vide : « (vide) → 45,00 » se lit, « → 45,00 » ressemble a un bug d affichage.</p>
     */
    private String detail(String type, String champ, String avant, String apres, String motif) {
        String vide = messages.getMessage("admin.journal.valeur-vide", null,
                LocaleContextHolder.getLocale());
        String transition = "%s → %s".formatted(
                avant == null || avant.isBlank() ? vide : avant,
                apres == null || apres.isBlank() ? vide : apres);

        if (EntreeJournal.TYPE_CATALOGUE.equals(type)) {
            return "%s : %s".formatted(champ, transition);
        }
        return motif == null || motif.isBlank() ? transition : "%s — %s".formatted(transition, motif);
    }
}
