package be.autoservplus.pilotage.repository;

import be.autoservplus.pilotage.service.dto.LigneClassement;
import be.autoservplus.pilotage.service.dto.MontantCategorie;
import be.autoservplus.pilotage.service.dto.MontantPeriode;
import be.autoservplus.pilotage.service.dto.RepartitionStatut;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agregations du tableau de bord (BL-1).
 *
 * <p><b>Un composant a {@link EntityManager} plutot qu une interface Spring Data</b> :
 * ces requetes traversent plusieurs agregats (factures, rendez-vous, lignes de panier)
 * et n appartiennent a aucune entite racine. Les accrocher a un
 * {@code JpaRepository<Facture, Long>} laisserait croire qu elles font partie du
 * cycle de vie de la facture.</p>
 *
 * <p>Tout est calcule <b>en base</b>, jamais en memoire : le tableau de bord doit
 * rester constant en cout quand le volume grandit, et charger toutes les factures
 * d un exercice pour en faire la somme cote Java ne tiendrait pas.</p>
 */
@Repository
public class IndicateursRepository {

    @PersistenceContext
    private EntityManager em;

    /**
     * Chiffre d affaires facture sur une periode, HTVA et TVAC.
     *
     * <p>Assis sur les <b>factures</b> et non sur les commandes payees : la facture
     * porte le montant legal, fige a l emission, et c est elle qui fait foi devant
     * l administration. Une commande remboursee garde son montant. C est l avoir qui
     * la contre-passe, et il est compte a part.</p>
     */
    public MontantPeriode chiffreAffaireFacture(Instant debut, Instant fin) {
        Object[] ligne = (Object[]) em.createQuery("""
                        SELECT COALESCE(SUM(f.montantHtva), 0), COALESCE(SUM(f.montantTvac), 0), COUNT(f)
                        FROM Facture f
                        WHERE f.dateEmission >= :debut AND f.dateEmission < :fin
                        """)
                .setParameter("debut", debut)
                .setParameter("fin", fin)
                .getSingleResult();
        return new MontantPeriode((BigDecimal) ligne[0], (BigDecimal) ligne[1], (Long) ligne[2]);
    }

    /** Montant des avoirs emis sur la periode, qui vient en deduction du CA. */
    public MontantPeriode avoirsEmis(Instant debut, Instant fin) {
        Object[] ligne = (Object[]) em.createQuery("""
                        SELECT COALESCE(SUM(a.montantHtva), 0), COALESCE(SUM(a.montantTvac), 0), COUNT(a)
                        FROM Avoir a
                        WHERE a.dateEmission >= :debut AND a.dateEmission < :fin
                        """)
                .setParameter("debut", debut)
                .setParameter("fin", fin)
                .getSingleResult();
        return new MontantPeriode((BigDecimal) ligne[0], (BigDecimal) ligne[1], (Long) ligne[2]);
    }

    /** Rendez-vous de la période, par statut. Un rendez-vous compte au jour de son créneau. */
    @SuppressWarnings("unchecked")
    public List<RepartitionStatut> rendezVousParStatut(Instant debut, Instant fin) {
        return em.createQuery("""
                        SELECT new be.autoservplus.pilotage.service.dto.RepartitionStatut(
                            CAST(r.statut AS string), COUNT(r))
                        FROM Rdv r
                        WHERE r.debut >= :debut AND r.debut < :fin
                        GROUP BY r.statut
                        ORDER BY COUNT(r) DESC
                        """)
                .setParameter("debut", debut)
                .setParameter("fin", fin)
                .getResultList();
    }

    /**
     * Minutes de rendez-vous non annules sur la periode, tous postes confondus.
     *
     * <p>Les statuts REFUSE, ANNULE et EXPIRE sont exclus : un créneau libéré n'a occupé
     * personne. ABSENT est <b>conserve</b> : le poste est bien reste bloque, le client
     * ne s est simplement pas presente, et masquer ce temps ferait croire a une
     * capacite disponible qui ne l etait pas.</p>
     */
    public long minutesReservees(Instant debut, Instant fin) {
        // Requete native : HQL ne sait pas convertir un intervalle en minutes.
        // FUNCTION('EXTRACT', EPOCH FROM ...) n est pas du HQL valide : l analyseur
        // s arrete sur le FROM interne. Le projet est PostgreSQL seul (contrainte
        // d exclusion btree_gist, index partiels), descendre en SQL ici ne ferme
        // aucune porte. Le filtre deleted_at reproduit le @SQLRestriction de Rdv,
        // que le natif court-circuite.
        Number minutes = (Number) em.createNativeQuery("""
                        SELECT COALESCE(SUM(EXTRACT(EPOCH FROM (fin - debut)) / 60), 0)
                        FROM rdv
                        WHERE debut >= :debut AND debut < :fin
                          AND statut NOT IN ('ANNULE', 'REFUSE', 'EXPIRE')
                          AND deleted_at IS NULL
                        """)
                .setParameter("debut", debut)
                .setParameter("fin", fin)
                .getSingleResult();
        return minutes == null ? 0L : minutes.longValue();
    }

    /**
     * Prestations les plus realisees sur la periode, par quantite.
     *
     * <p><b>Lu sur les lignes d intervention, pas sur le panier.</b> C est la seule
     * source possible : {@code ligne_panier.service_id} existe au schema mais reste
     * volontairement non mappe tant que F12 (services au panier) n est pas livre.
     * Aucune prestation ne transite donc par la vente en ligne. Le classement porte sur
     * ce que l atelier a reellement execute.</p>
     *
     * <p>Borne sur {@code finReelle} : une prestation compte quand elle est faite, pas
     * quand elle est planifiee. Les interventions en cours n y figurent pas.</p>
     */
    @SuppressWarnings("unchecked")
    public List<LigneClassement> topPrestations(Instant debut, Instant fin, int limite) {
        return em.createQuery("""
                        SELECT new be.autoservplus.pilotage.service.dto.LigneClassement(
                            l.libelleFige, SUM(l.quantite), SUM(l.prixUnitaireHtva * l.quantite))
                        FROM LigneIntervention l
                        JOIN l.intervention i
                        WHERE l.prestation IS NOT NULL
                          AND i.finReelle >= :debut AND i.finReelle < :fin
                        GROUP BY l.libelleFige
                        ORDER BY SUM(l.quantite) DESC
                        """)
                .setParameter("debut", debut)
                .setParameter("fin", fin)
                .setMaxResults(limite)
                .getResultList();
    }

    /**
     * Pieces les plus vendues en ligne sur la periode, par quantite.
     *
     * <p><b>Ventes en ligne seulement</b> : les pieces posees par l atelier passent par
     * {@code ligne_intervention} et ne sont pas comptees ici. Melanger les deux sources
     * dans un meme classement additionnerait des chiffres qui ne se facturent pas de la
     * meme facon : le garage lit ici ce que sa boutique a vendu.</p>
     *
     * <p>Même assiette que {@link #chiffreAffaireParCategorie} : les lignes des commandes
     * facturées pendant la période. Comptées à la date de commande, une commande annulée
     * ou jamais payée entrait au classement, et son total dépassait le chiffre
     * d'affaires de sa catégorie.</p>
     */
    @SuppressWarnings("unchecked")
    public List<LigneClassement> topPieces(Instant debut, Instant fin, int limite) {
        return em.createQuery("""
                        SELECT new be.autoservplus.pilotage.service.dto.LigneClassement(
                            l.libelleFige, SUM(l.quantite), SUM(l.prixUnitaireHtva * l.quantite))
                        FROM LignePanier l
                        JOIN l.commande c
                        WHERE l.piece IS NOT NULL
                          AND EXISTS (SELECT 1 FROM Facture f
                                      WHERE f.commande = c
                                        AND f.dateEmission >= :debut AND f.dateEmission < :fin)
                        GROUP BY l.libelleFige
                        ORDER BY SUM(l.quantite) DESC
                        """)
                .setParameter("debut", debut)
                .setParameter("fin", fin)
                .setMaxResults(limite)
                .getResultList();
    }

    /**
     * Commandes conclues mais non encore encaissees, avec le montant en jeu.
     *
     * <p><b>Et non « factures impayees ».</b> En V1 une facture n est emise que sur
     * {@code CommandePayeeEvent} et {@code date_echeance} est laissee nulle : toute
     * facture existante est donc payee par construction, un compteur de factures
     * impayees vaudrait toujours zero. Ce qui reste reellement a encaisser, ce sont
     * les commandes en attente de paiement : c est cela qui est affiche.</p>
     */
    public MontantPeriode commandesAEncaisser() {
        Object[] ligne = (Object[]) em.createQuery("""
                        SELECT COALESCE(SUM(c.montantHtva), 0), COALESCE(SUM(c.montantTvac), 0), COUNT(c)
                        FROM Commande c
                        WHERE c.statut = be.autoservplus.vente.domain.StatutCommande.EN_ATTENTE_PAIEMENT
                        """)
                .getSingleResult();
        return new MontantPeriode((BigDecimal) ligne[0], (BigDecimal) ligne[1], (Long) ligne[2]);
    }

    /**
     * Rendez-vous annulés de la période, partagés entre le membre et le garage.
     *
     * <p>Le garage ne peut annuler qu'avec un motif, que {@code Rdv.annulerParLeGarage}
     * écrit dans {@code motifRefus}. Le membre annule sans motif. Le motif présent ou
     * absent sur un rendez-vous annulé dit donc qui a annulé, sans colonne de plus.</p>
     *
     * @return deux nombres : annulations par le membre, puis par le garage
     */
    public long[] annulationsMembreEtGarage(Instant debut, Instant fin) {
        Object[] ligne = (Object[]) em.createQuery("""
                        SELECT COALESCE(SUM(CASE WHEN r.motifRefus IS NULL THEN 1 ELSE 0 END), 0),
                               COALESCE(SUM(CASE WHEN r.motifRefus IS NULL THEN 0 ELSE 1 END), 0)
                        FROM Rdv r
                        WHERE r.statut = be.autoservplus.reservation.domain.StatutRdv.ANNULE
                          AND r.debut >= :debut AND r.debut < :fin
                        """)
                .setParameter("debut", debut)
                .setParameter("fin", fin)
                .getSingleResult();
        return new long[]{((Number) ligne[0]).longValue(), ((Number) ligne[1]).longValue()};
    }

    /**
     * Nouveaux clients : comptes de membre dont l'adresse a été vérifiée pendant la
     * période.
     *
     * <p>Une inscription jamais confirmée n'est pas un client. La date est celle de la
     * vérification (V63). Un compte vérifié avant V63, ou inséré vérifié par une graine,
     * n'en porte pas : il est daté de sa création, seule date connue. Un compte supprimé
     * depuis n'est plus compté, son adresse n'étant plus vérifiée après l'anonymisation.</p>
     */
    public long nouveauxClients(Instant debut, Instant fin) {
        // Requête native : COALESCE mêle une colonne de l'entité et une colonne d'audit
        // héritée, et le filtre deleted_at reproduit le @SQLRestriction que le natif
        // court-circuite.
        Number nombre = (Number) em.createNativeQuery("""
                        SELECT COUNT(*)
                        FROM utilisateur
                        WHERE type_utilisateur = 'MEMBRE'
                          AND email_verifie
                          AND deleted_at IS NULL
                          AND COALESCE(email_verifie_le, created_at) >= :debut
                          AND COALESCE(email_verifie_le, created_at) < :fin
                        """)
                .setParameter("debut", debut)
                .setParameter("fin", fin)
                .getSingleResult();
        return nombre.longValue();
    }

    /**
     * Commandes payées pendant la période, assiette du panier moyen.
     *
     * <p>Bornée sur la date de paiement : une commande remboursée depuis la garde, elle a
     * bien été passée et payée à ce montant. Une commande jamais payée n'en a pas.</p>
     */
    public MontantPeriode commandesPayees(Instant debut, Instant fin) {
        Object[] ligne = (Object[]) em.createQuery("""
                        SELECT COALESCE(SUM(c.montantHtva), 0), COALESCE(SUM(c.montantTvac), 0), COUNT(c)
                        FROM Commande c
                        WHERE c.datePaiement >= :debut AND c.datePaiement < :fin
                        """)
                .setParameter("debut", debut)
                .setParameter("fin", fin)
                .getSingleResult();
        return new MontantPeriode((BigDecimal) ligne[0], (BigDecimal) ligne[1], (Long) ligne[2]);
    }

    /**
     * Chiffre d'affaires facturé hors TVA par catégorie du catalogue, du plus fort au
     * plus faible.
     *
     * <p>Les lignes des commandes facturées pendant la période, rangées par la catégorie
     * de leur pièce ou de leur prestation. Requête native : une ligne porte l'une ou
     * l'autre, et la catégorie se lit par un COALESCE sur deux jointures externes. Un
     * article retiré du catalogue garde sa catégorie, le natif ne filtrant pas
     * {@code deleted_at}.</p>
     */
    @SuppressWarnings("unchecked")
    public List<MontantCategorie> chiffreAffaireParCategorie(Instant debut, Instant fin) {
        List<Object[]> lignes = em.createNativeQuery("""
                        SELECT cat.libelle, SUM(l.prix_unitaire_htva * l.quantite) AS htva
                        FROM facture f
                        JOIN ligne_panier l ON l.commande_id = f.commande_id
                        LEFT JOIN piece p ON p.id = l.piece_id
                        LEFT JOIN service s ON s.id = l.service_id
                        JOIN categorie cat ON cat.id = COALESCE(p.categorie_id, s.categorie_id)
                        WHERE f.date_emission >= :debut AND f.date_emission < :fin
                        GROUP BY cat.libelle
                        ORDER BY htva DESC, cat.libelle
                        """)
                .setParameter("debut", debut)
                .setParameter("fin", fin)
                .getResultList();
        return lignes.stream()
                .map(ligne -> new MontantCategorie((String) ligne[0], (BigDecimal) ligne[1]))
                .toList();
    }

    /**
     * Chiffre d'affaires facturé hors TVA par jour de l'atelier. Seuls les jours qui ont
     * une facture y figurent, l'appelant complète les autres.
     *
     * <p>Le jour se calcule en base, dans le fuseau de l'atelier : une facture émise à
     * 23 h 30 à Bruxelles appartient à ce jour-là, pas au lendemain du serveur en UTC.</p>
     */
    @SuppressWarnings("unchecked")
    public Map<LocalDate, BigDecimal> chiffreAffaireParJour(Instant debut, Instant fin, ZoneId zone) {
        List<Object[]> lignes = em.createNativeQuery("""
                        SELECT CAST(date_emission AT TIME ZONE :zone AS date) AS jour, SUM(montant_htva)
                        FROM facture
                        WHERE date_emission >= :debut AND date_emission < :fin
                        GROUP BY jour
                        ORDER BY jour
                        """)
                .setParameter("zone", zone.getId())
                .setParameter("debut", debut)
                .setParameter("fin", fin)
                .getResultList();
        Map<LocalDate, BigDecimal> parJour = new LinkedHashMap<>();
        for (Object[] ligne : lignes) {
            LocalDate jour = ligne[0] instanceof LocalDate date ? date : ((java.sql.Date) ligne[0]).toLocalDate();
            parJour.put(jour, (BigDecimal) ligne[1]);
        }
        return parJour;
    }
}
