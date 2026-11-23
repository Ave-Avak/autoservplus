package be.autoservplus.facturation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Compteur des factures d'un exercice comptable, garant de la numérotation
 * <b>continue</b> exigée par la loi belge (AR n°1, art. 5 : suite ininterrompue).
 *
 * <p>Une ligne par année civile, l'exercice servant de clé primaire : la
 * numérotation repart à 1 chaque année, l'année étant portée par le numéro lui-même.
 * L'incrément se fait <b>en table</b>, donc dans la transaction qui insère la
 * facture : si celle-ci échoue, l'incrément est annulé avec elle et le numéro
 * reste disponible. C'est précisément ce qu'une séquence PostgreSQL ne sait pas
 * faire, {@code nextval} est non transactionnel par conception et creuse un trou
 * à chaque rollback.</p>
 *
 * <p>Pas de suppression logique ni de {@code created_at} : ce n'est pas une entité
 * métier mais un registre technique, calqué sur {@code ParametreAtelier} (une seule
 * paire d'audit, {@code updated_*}).</p>
 */
@Entity
@Table(name = "compteur_facture")
@EntityListeners(AuditingEntityListener.class)
public class CompteurFacture {

    @Id
    @Column(name = "exercice", nullable = false, updatable = false)
    private short exercice;

    @Column(name = "dernier_numero", nullable = false)
    private int dernierNumero;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by", length = 120)
    private String updatedBy;

    protected CompteurFacture() {
        // requis par JPA
    }

    public CompteurFacture(short exercice) {
        this.exercice = exercice;
        this.dernierNumero = 0;
    }

    /**
     * Consomme le numéro suivant de l'exercice. Appelé sous verrou de ligne : deux
     * émissions concurrentes sont sérialisées, la seconde lit la valeur committée
     * par la première et ne peut donc pas la réattribuer.
     *
     * @return le numéro attribué, strictement croissant à partir de 1
     */
    public int consommerProchainNumero() {
        return ++dernierNumero;
    }

    public short getExercice() { return exercice; }
    public int getDernierNumero() { return dernierNumero; }

    @Override
    public boolean equals(Object autre) {
        if (this == autre) return true;
        if (!(autre instanceof CompteurFacture compteur)) return false;
        return exercice == compteur.exercice;
    }

    @Override
    public int hashCode() {
        return Short.hashCode(exercice);
    }
}
