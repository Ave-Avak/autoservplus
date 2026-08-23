package be.autoservplus.rgpd.repository;

import be.autoservplus.identite.domain.Utilisateur;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Balayage des colonnes d audit lors de la suppression d un compte (F23).
 *
 * <p><b>Pourquoi ce balayage existe.</b> {@code JpaAuditingConfig} alimente
 * {@code created_by} et {@code updated_by} avec {@code auth.getName()}, c est-a-dire
 * l adresse de courriel en clair. Anonymiser la seule table {@code utilisateur}
 * laisserait donc l adresse reelle, lisible, dans une dizaine de tables : vehicule,
 * rdv, panier, ligne_panier, commande, consentement, paiement, demande_annulation.
 * Un effacement qui laisse l identifiant de connexion en clair n en est pas un.</p>
 *
 * <p><b>Pourquoi une fonction en base et non une boucle en Java.</b> Le balayage
 * touche soixante-trois colonnes ; le faire depuis Java demanderait soit autant de
 * requetes, soit du SQL assemble cote applicatif. La fonction {@code
 * fn_anonymiser_traces_audit} (V28) le fait en une transaction, sur une liste
 * <b>enumeree</b> par {@code fn_tables_traces_audit()}.</p>
 *
 * <p><b>Liste enumeree, pas calculee.</b> Une premiere version la derivait
 * d {@code information_schema} au moment de l appel. C etait commode et
 * indefendable : un effacement legal doit pouvoir enoncer exactement quelles
 * colonnes de quelles tables sont ecrasees, et une liste calculee au runtime ne
 * s enonce pas — elle depend de l etat de la base a cet instant. Le risque que la
 * liste figee se perime est repris par {@code SchemaIT}, qui la confronte au schema
 * reel et fait <b>echouer la build</b> si une colonne d audit n y figure pas : une
 * table ajoutee demain casse le test au lieu de fuiter en silence.</p>
 *
 * <p><b>Ordre d appel impose</b> : l anonymisation de l entite doit avoir ete
 * <b>ecrite</b> (flush) avant ce balayage. Sinon l audit JPA pose
 * {@code updated_by} = adresse du membre au flush suivant, apres le passage du
 * balayage, et l adresse reapparait sur la ligne meme qu on vient de vider. Le
 * service s en charge par {@code saveAndFlush} ; c est aussi ce que verifie le test
 * d integration, sans quoi l erreur serait invisible a l oeil nu.</p>
 */
public interface TracesAuditRepository extends Repository<Utilisateur, Long> {

    /**
     * Remplace l adresse par le jeton anonyme dans toutes les colonnes d audit.
     *
     * @return le nombre de colonnes reellement modifiees, journalise comme preuve
     *         que le balayage a bien porte
     */
    @Query(value = "SELECT fn_anonymiser_traces_audit(:ancienne, :jeton)", nativeQuery = true)
    Integer anonymiser(@Param("ancienne") String ancienneAdresse, @Param("jeton") String jeton);
}
