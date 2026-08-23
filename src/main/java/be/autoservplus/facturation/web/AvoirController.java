package be.autoservplus.facturation.web;

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
 * Telechargement de la note de credit PDF d une retractation validee (F30).
 *
 * <p>Jumeau de {@code FactureController}, et volontairement identique dans ses
 * gardes : la note de credit est adressee par sa reference publique et
 * l appartenance est verifiee au service — l avoir d autrui remonte en <b>404, jamais
 * en 403</b>. Un 403 confirmerait a un tiers que cette note de credit existe, alors
 * qu il s agit d un document nominatif ; et il apprendrait au passage que le
 * titulaire a exerce son droit de retractation.</p>
 *
 * <p>L identite vient du contexte de securite, jamais d un parametre de requete :
 * sans cela, changer un identifiant dans l URL suffirait a telecharger le document
 * de n importe qui.</p>
 *
 * <p>{@code RessourceIntrouvableException} porte deja
 * {@code @ResponseStatus(NOT_FOUND)} : ce controleur n a rien a catcher pour rendre
 * un 404 propre — il sert des octets ou rien.</p>
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
                // Document nominatif : aucun cache partage ne doit le conserver.
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(document);
    }
}
