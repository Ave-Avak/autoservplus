package be.autoservplus.vente.web;

import be.autoservplus.vente.service.CgvNonAccepteesException;
import be.autoservplus.vente.service.CommandeService;
import be.autoservplus.vente.service.PaiementImpossibleException;
import be.autoservplus.vente.service.PaiementService;
import be.autoservplus.vente.service.PanierService;
import be.autoservplus.vente.service.PanierVideException;
import be.autoservplus.vente.service.PieceInactiveException;
import be.autoservplus.vente.service.StockInsuffisantException;
import be.autoservplus.vente.web.dto.ConfirmationCommandeVue;
import be.autoservplus.vente.web.dto.PanierVue;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

/**
 * Recapitulatif et validation de commande (F14, sans paiement), sous /commande.
 *
 * <p>Le POST porte l acceptation des CGV (revalidee cote serveur — l attribut
 * {@code required} de la case n est qu un confort) et capture l adresse IP de la
 * requete pour la preuve contractuelle : l IP ne transite ni par l URL ni par les
 * journaux, elle part au service et ne vit qu en colonne de preuve. Le bouton de
 * validation porte la mention « Commande avec obligation de paiement »
 * (art. VI.45 CDE, cle i18n {@code commande.bouton}).</p>
 *
 * <p>Refus metier : le recapitulatif est reaffiche avec le message sous la zone
 * concernee (CGV ou lignes). Succes : PRG vers la page de confirmation, adressee
 * par la reference publique de la commande. {@code RessourceIntrouvableException}
 * n est pas catchee — commande d autrui ou inconnue : 404.</p>
 */
@Controller
@RequestMapping("/commande")
public class CommandeController {

    private final CommandeService service;
    private final PanierService paniers;
    private final PaiementService paiements;
    private final MessageSource messages;

    public CommandeController(CommandeService service, PanierService paniers,
                              PaiementService paiements, MessageSource messages) {
        this.service = service;
        this.paniers = paniers;
        this.paiements = paiements;
        this.messages = messages;
    }

    @GetMapping
    public String recapitulatif(@AuthenticationPrincipal UserDetails membre,
                                Model modele,
                                RedirectAttributes redirection) {
        PanierVue panier = paniers.panierDuMembre(membre.getUsername());
        if (panier.estVide()) {
            redirection.addFlashAttribute("erreur", msg("commande.erreur.panier-vide"));
            return "redirect:/panier";
        }
        modele.addAttribute("titre", msg("commande.recap.titre"));
        modele.addAttribute("panier", panier);
        return "vente/recapitulatif";
    }

    @PostMapping
    public String valider(@AuthenticationPrincipal UserDetails membre,
                          @RequestParam(defaultValue = "false") boolean cgv,
                          @RequestParam(defaultValue = "false") boolean renonciationVi53,
                          HttpServletRequest requete,
                          Model modele,
                          RedirectAttributes redirection) {
        try {
            ConfirmationCommandeVue confirmation = service.passerCommande(
                    membre.getUsername(), cgv, renonciationVi53, requete.getRemoteAddr());
            return "redirect:/commande/" + confirmation.reference() + "/confirmation";
        } catch (CgvNonAccepteesException e) {
            return recapAvecErreur(membre, modele, "erreurCgv", msg("commande.erreur.cgv"));
        } catch (StockInsuffisantException e) {
            return recapAvecErreur(membre, modele, "erreurLignes",
                    msg("commande.erreur.stock", e.getLibelle(), e.getQuantiteDisponible()));
        } catch (PieceInactiveException e) {
            return recapAvecErreur(membre, modele, "erreurLignes",
                    msg("commande.erreur.piece-inactive", e.getLibelle()));
        } catch (PanierVideException e) {
            redirection.addFlashAttribute("erreur", msg("commande.erreur.panier-vide"));
            return "redirect:/panier";
        }
    }

    /**
     * Depart vers la page de paiement du prestataire (F14). L URL de redirection
     * vient de la passerelle — factice hors production, Mollie en production.
     */
    @PostMapping("/{reference}/payer")
    public String payer(@AuthenticationPrincipal UserDetails membre,
                        @PathVariable UUID reference,
                        RedirectAttributes redirection) {
        try {
            return "redirect:" + paiements.initierPaiement(reference, membre.getUsername());
        } catch (PaiementImpossibleException e) {
            redirection.addFlashAttribute("erreur", msg("commande.erreur.paiement-impossible"));
            return "redirect:/commande/" + reference + "/confirmation";
        }
    }

    /**
     * Retour du membre depuis la page du prestataire.
     *
     * <p><b>Rien n est conclu de la seule visite de cette adresse.</b> Le prestataire
     * y renvoie le membre quelle que soit l issue, abandon compris, et l URL est
     * ouvrable par quiconque la connait : le service relit le statut chez le
     * prestataire avant de repondre. Le message affiche decoule de l etat constate, pas
     * du fait d etre revenu.</p>
     *
     * <p>La session survit au detour : le prestataire ramene le navigateur par une
     * navigation GET de premier niveau, que le cookie {@code SameSite=Lax} accompagne.
     * Elle ne survivrait pas a un POST de retour — raison de plus pour que la
     * notification serveur a serveur reste le chemin nominal quand elle est
     * possible.</p>
     */
    @GetMapping("/{reference}/retour")
    public String retourDePaiement(@AuthenticationPrincipal UserDetails membre,
                                   @PathVariable UUID reference,
                                   RedirectAttributes redirection) {
        boolean payee = paiements.constaterRetour(reference, membre.getUsername());
        if (payee) {
            redirection.addFlashAttribute("succes", msg("commande.retour.paye"));
        } else {
            // Ni echec ni succes affirme : le paiement peut encore aboutir, notamment
            // par virement. Annoncer un echec pousserait a payer une seconde fois.
            redirection.addFlashAttribute("erreur", msg("commande.retour.en-attente"));
        }
        return "redirect:/commande/" + reference + "/confirmation";
    }

    @GetMapping("/{reference}/confirmation")
    public String confirmation(@AuthenticationPrincipal UserDetails membre,
                               @PathVariable UUID reference,
                               Model modele) {
        ConfirmationCommandeVue vue = service.confirmation(reference, membre.getUsername());
        modele.addAttribute("titre", msg("commande.confirmation.titre"));
        modele.addAttribute("commande", vue);
        return "vente/commande-confirmee";
    }

    /** Reaffiche le recapitulatif avec le message sous la zone concernee. */
    private String recapAvecErreur(UserDetails membre, Model modele,
                                   String attribut, String message) {
        modele.addAttribute("titre", msg("commande.recap.titre"));
        modele.addAttribute("panier", paniers.panierDuMembre(membre.getUsername()));
        modele.addAttribute(attribut, message);
        return "vente/recapitulatif";
    }

    private String msg(String cle, Object... arguments) {
        return messages.getMessage(cle, arguments, LocaleContextHolder.getLocale());
    }
}
