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
import java.time.OffsetDateTime;
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
        Instant horodatage = horodatageDe(ligne[0]);
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
     * Horodatage d une ligne de requete NATIVE, quel que soit le type rendu par le
     * pilote.
     *
     * <p>Le code castait en dur vers {@link Timestamp}. Le pilote PostgreSQL rend en
     * realite un {@link Instant} pour une colonne {@code TIMESTAMPTZ}, de sorte que
     * l ecran tombait en {@code ClassCastException} des qu une SEULE ligne existait.
     * Le defaut est reste invisible parce que les deux tables d historique n avaient
     * jamais ete alimentees hors des tests, et que ceux-ci verifient l execution des
     * quatre variantes de l UNION et le cas « aucune trace » — jamais la conversion
     * d une ligne reelle. Une requete native rend des {@code Object[]} : c est a
     * l appelant de ne rien presumer de leur type.</p>
     */
    private static Instant horodatageDe(Object valeur) {
        return switch (valeur) {
            case Instant instant -> instant;
            case Timestamp timestamp -> timestamp.toInstant();
            case OffsetDateTime decale -> decale.toInstant();
            case null -> throw new IllegalStateException(
                    "Horodatage nul dans le journal : la colonne est NOT NULL des deux cotes de l UNION.");
            default -> throw new IllegalStateException(
                    "Type d horodatage inattendu dans le journal : " + valeur.getClass().getName());
        };
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
