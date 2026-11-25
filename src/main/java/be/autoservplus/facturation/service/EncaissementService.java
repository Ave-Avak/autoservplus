package be.autoservplus.facturation.service;

import be.autoservplus.common.temps.FuseauGarage;
import be.autoservplus.facturation.domain.Encaissement;
import be.autoservplus.facturation.domain.EncaissementRefuseException;
import be.autoservplus.facturation.domain.Facture;
import be.autoservplus.facturation.domain.MoyenEncaissement;
import be.autoservplus.facturation.repository.AvoirRepository;
import be.autoservplus.facturation.repository.EncaissementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Encaissement d'une facture d'atelier au comptoir (RM-20 révisée, Q-H-43 à Q-H-46).
 *
 * <p>Les règles de l'encaissement lui-même (montant, dates, plafond des espèces) vivent
 * dans {@link Encaissement}. Ce service porte ce qui dépend d'autres lignes : une facture de
 * commande se paie en ligne, une facture annulée par une note de crédit ne s'encaisse plus,
 * et une facture déjà réglée ne l'est pas deux fois. Deux enregistrements simultanés passent
 * tous deux ce dernier contrôle : l'index {@code uq_encaissement_facture_actif} refuse alors
 * le second, traduit en même refus.</p>
 */
@Service
@Transactional(readOnly = true)
public class EncaissementService {

    private static final Logger log = LoggerFactory.getLogger(EncaissementService.class);

    private final EncaissementRepository encaissements;
    private final AvoirRepository avoirs;
    private final FuseauGarage fuseau;
    private final Clock horloge;
    private final BigDecimal plafondEspeces;

    public EncaissementService(EncaissementRepository encaissements, AvoirRepository avoirs,
                               FuseauGarage fuseau, Clock horloge,
                               @Value("${autoservplus.facturation.plafond-especes}") BigDecimal plafondEspeces) {
        this.encaissements = encaissements;
        this.avoirs = avoirs;
        this.fuseau = fuseau;
        this.horloge = horloge;
        this.plafondEspeces = plafondEspeces;
    }

    /**
     * Enregistre le règlement de la facture, pour son total TVAC.
     *
     * @throws EncaissementRefuseException facture de commande, annulée, déjà réglée, ou
     *         refus du domaine
     */
    @Transactional
    public Encaissement enregistrer(Facture facture, MoyenEncaissement moyen, LocalDate date) {
        if (!facture.estDAtelier()) {
            throw EncaissementRefuseException.pasDeFactureDAtelier();
        }
        if (avoirs.findByFacture(facture).isPresent()) {
            throw EncaissementRefuseException.factureAnnulee();
        }
        if (encaissements.findByFactureAndAnnuleLeIsNull(facture).isPresent()) {
            throw EncaissementRefuseException.dejaReglee();
        }
        Encaissement encaissement = Encaissement.enregistrer(facture, moyen, facture.getMontantTvac(), date,
                fuseau.jour(facture.getDateEmission()), fuseau.jour(horloge.instant()), plafondEspeces);
        try {
            encaissements.saveAndFlush(encaissement);
        } catch (DataIntegrityViolationException collision) {
            throw EncaissementRefuseException.dejaReglee();
        }
        log.info("Facture {} reglee le {} ({}, {} EUR TVAC).", facture.getNumero(), date, moyen,
                encaissement.getMontant());
        return encaissement;
    }

    /**
     * Annule l'encaissement non annulé de la facture, avec un motif obligatoire : la facture
     * redevient à régler (Q-H-43).
     *
     * @param auteur adresse de l'administrateur, lue du contexte de sécurité par l'appelant
     */
    @Transactional
    public Encaissement annuler(Facture facture, String motif, String auteur) {
        Encaissement encaissement = encaissements.findByFactureAndAnnuleLeIsNull(facture)
                .orElseThrow(EncaissementRefuseException::aucunEncaissement);
        encaissement.annuler(motif, auteur, horloge.instant());
        log.info("Encaissement de la facture {} annule.", facture.getNumero());
        return encaissement;
    }

    /** Encaissement non annulé de la facture, s'il y en a un. */
    public Optional<Encaissement> actifDe(Facture facture) {
        return encaissements.findByFactureAndAnnuleLeIsNull(facture);
    }

    public BigDecimal plafondEspeces() {
        return plafondEspeces;
    }
}
