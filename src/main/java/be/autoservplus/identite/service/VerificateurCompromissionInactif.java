package be.autoservplus.identite.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

/**
 * Implementation de repli : ne consulte aucun service et ne refuse rien.
 *
 * <p>Active par {@code autoservplus.securite.compromission.activee=false}, valeur
 * opposee a celle qui arme {@link VerificateurHaveIBeenPwned} : l une des deux
 * implementations est donc toujours presente. Une condition symetrique plutot qu un
 * {@code @ConditionalOnMissingBean}, dont l ordre d evaluation n est pas defini entre
 * beans scannes. La liste embarquee reste seule en vigueur.</p>
 *
 * <p>Elle <b>annonce</b> son entree en service au demarrage. Un repli muet laisserait
 * croire que la seconde couche protege alors qu elle est absente : c est la meme
 * erreur que {@code CourrielConsole} avait faite avant qu on la lui retire.</p>
 */
@Service
@ConditionalOnProperty(name = "autoservplus.securite.compromission.activee",
        havingValue = "false")
public class VerificateurCompromissionInactif implements VerificateurCompromission {

    private static final Logger JOURNAL =
            LoggerFactory.getLogger(VerificateurCompromissionInactif.class);

    @PostConstruct
    void annoncer() {
        JOURNAL.warn("Verification des mots de passe compromis INACTIVE : aucun service"
                + " n est configure. Seule la liste embarquee refuse un mot de passe.");
    }

    @Override
    public boolean estCompromis(String motDePasse) {
        return false;
    }
}
