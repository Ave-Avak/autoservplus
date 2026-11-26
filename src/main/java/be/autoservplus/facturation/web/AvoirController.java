package be.autoservplus.facturation.web;

import be.autoservplus.common.debit.Plafonne;
import be.autoservplus.facturation.domain.Avoir;
import be.autoservplus.facturation.service.AvoirService;
import be.autoservplus.facturation.service.PdfAvoirService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Téléchargement de la note de crédit PDF d'une rétractation validée (F30).
 *
 * <p>Jumeau de {@code FactureController}, et volontairement identique dans ses
 * gardes : la note de crédit est adressée par sa référence publique et
 * l'appartenance est vérifiée au service. L'avoir d'autrui remonte en <b>404, jamais
 * en 403</b>. Un 403 confirmerait à un tiers que cette note de crédit existe, alors
 * qu'il s'agit d'un document nominatif. Et il apprendrait au passage que le
 * titulaire a exercé son droit de rétractation.</p>
 *
 * <p>L'identité vient du contexte de sécurité, jamais d'un paramètre de requête :
 * sans cela, changer un identifiant dans l'URL suffirait à télécharger le document
 * de n'importe qui.</p>
 *
 * <p>{@code RessourceIntrouvableException} porte déjà
 * {@code @ResponseStatus(NOT_FOUND)} : ce contrôleur n'a rien à catcher pour rendre
 * un 404 propre. Il sert des octets ou rien.</p>
 */
@Controller
@RequestMapping("/avoirs")
public class AvoirController {

    private final AvoirService avoirs;
    private final PdfAvoirService pdf;

    public AvoirController(AvoirService avoirs, PdfAvoirService pdf) {
        this.avoirs = avoirs;
        this.pdf = pdf;
    }

    @Plafonne("pdf")
    @GetMapping("/{reference}/pdf")
    public ResponseEntity<byte[]> telecharger(@AuthenticationPrincipal UserDetails membre,
                                              @PathVariable UUID reference) {
        Avoir avoir = avoirs.pourMembre(reference, membre.getUsername());
        byte[] document = pdf.pdfDe(avoir);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(pdf.nomDeFichier(avoir), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                // Document nominatif : aucun cache partagé ne doit le conserver.
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(document);
    }
}
