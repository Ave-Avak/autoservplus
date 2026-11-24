package be.autoservplus.rgpd.web;

import be.autoservplus.rgpd.service.ExportDonneesService;
import be.autoservplus.rgpd.service.ExportTropRecentException;
import be.autoservplus.rgpd.service.ReauthentificationEchoueeException;
import be.autoservplus.rgpd.service.dto.FichierExport;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.Duration;
import java.util.Optional;

/**
 * Rubrique « Mes données personnelles » du compte (F22, droit d'accès,
 * article 15 RGPD) : explication du droit, confirmation du mot de passe,
 * téléchargement du fichier.
 *
 * <p>La zone exige une authentification ({@code anyRequest().authenticated()} de la
 * configuration de sécurité). Le service redouble par {@code @PreAuthorize}, et
 * l'identité vient du contexte de sécurité, jamais d'un paramètre de requête. Un
 * membre ne peut donc exporter que son propre dossier : il n'existe aucune URL
 * permettant de désigner un autre titulaire.
 *
 * <p><b>Refus par redirection codée.</b> Le POST rend soit un fichier
 * ({@code ResponseEntity}), soit une redirection vers l'écran : deux natures de
 * réponse que Spring MVC ne sait pas résoudre depuis un type de retour
 * {@code Object}. Le refus voyage donc en <b>code</b> dans l'URL
 * ({@code ?erreur=motdepasse}), que le GET retraduit en message i18n. Écart assumé
 * au patron PRG+flash des autres contrôleurs, imposé par la réponse binaire. Le
 * code ne divulgue rien et le délai restant est <b>recalculé</b> au GET plutôt que
 * transporté, ce qui évite d'afficher une échéance périmée.
 */
@Controller
@RequestMapping("/mes-donnees")
public class DonneesPersonnellesController {

    private static final String ERREUR_MOT_DE_PASSE = "motdepasse";
    private static final String ERREUR_LIMITE = "limite";

    private final ExportDonneesService service;
    private final MessageSource messages;

    public DonneesPersonnellesController(ExportDonneesService service, MessageSource messages) {
        this.service = service;
        this.messages = messages;
    }

    @GetMapping
    public String page(@AuthenticationPrincipal UserDetails membre,
                       @RequestParam(name = "erreur", required = false) String codeErreur,
                       Model modele) {
        Optional<Duration> attente = service.attenteRestante(membre.getUsername());

        modele.addAttribute("titre", msg("rgpd.donnees.titre"));
        modele.addAttribute("exportPossible", attente.isEmpty());
        // Confirmation de l'export précédent : le téléchargement ne rafraîchit pas
        // la page, le membre lit donc cette trace à sa visite suivante.
        attente.ifPresent(restant -> modele.addAttribute("message",
                msg("rgpd.donnees.message.recent", delaiLisible(restant))));
        messageErreur(codeErreur, attente)
                .ifPresent(erreur -> modele.addAttribute("erreur", erreur));
        return "rgpd/mes-donnees";
    }

    /**
     * Produit et sert le fichier, ou redirige avec un code de refus.
     *
     * <p>Une archive ZIP : le document structuré et les copies des photos qu'il
     * déclare. {@code Content-Disposition: attachment} et un nom de fichier daté : le
     * document est fait pour être archivé par la personne, pas affiché dans
     * l'onglet.
     */
    @PostMapping("/export")
    public ResponseEntity<byte[]> exporter(@AuthenticationPrincipal UserDetails membre,
                                           @RequestParam(name = "motDePasse", required = false)
                                           String motDePasse) {
        try {
            FichierExport fichier = service.exporter(membre.getUsername(), motDePasse);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/zip"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename(fichier.nom()).build().toString())
                    .body(fichier.contenu());
        } catch (ReauthentificationEchoueeException e) {
            return refus(ERREUR_MOT_DE_PASSE);
        } catch (ExportTropRecentException e) {
            return refus(ERREUR_LIMITE);
        }
    }

    /**
     * Redirection « 303 See Other » : la réponse à un POST n'est pas le document,
     * le rechargement de l'écran ne doit pas rejouer la tentative. Le chemin passe
     * par {@code ServletUriComponentsBuilder} pour rester valide sous un contexte
     * de déploiement non racine.
     */
    private ResponseEntity<byte[]> refus(String code) {
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .location(ServletUriComponentsBuilder.fromCurrentContextPath()
                        .path("/mes-donnees")
                        .queryParam("erreur", code)
                        .build().toUri())
                .build();
    }

    /**
     * Traduit un code de refus en message. Un code inconnu (l'URL est modifiable
     * à la main) ne produit aucun message plutôt qu'une page en erreur.
     *
     * <p>Le refus de quota n'est affiché que si l'échéance n'est pas déjà passée :
     * entre le POST et le GET, le délai a pu expirer, et annoncer une attente
     * révolue serait faux.
     */
    private Optional<String> messageErreur(String code, Optional<Duration> attente) {
        if (ERREUR_MOT_DE_PASSE.equals(code)) {
            return Optional.of(msg("rgpd.donnees.erreur.mot-de-passe"));
        }
        if (ERREUR_LIMITE.equals(code)) {
            return attente.map(restant ->
                    msg("rgpd.donnees.erreur.limite", delaiLisible(restant)));
        }
        return Optional.empty();
    }

    /**
     * Délai restant en heures et minutes. Une attente de moins d'une minute
     * s'annonce « 1 min » : « 0 min » se lirait comme « c'est possible
     * maintenant », alors que la garde refuse encore.
     */
    private String delaiLisible(Duration attente) {
        long heures = attente.toHours();
        long minutes = attente.toMinutesPart();
        return heures > 0
                ? msg("rgpd.donnees.delai.heures", heures, minutes)
                : msg("rgpd.donnees.delai.minutes", Math.max(1, minutes));
    }

    private String msg(String cle, Object... arguments) {
        return messages.getMessage(cle, arguments, LocaleContextHolder.getLocale());
    }
}
