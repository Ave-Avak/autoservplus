package be.autoservplus.identite.domain;

/**
 * Nature du compte, et source unique de l autorite Spring Security.
 *
 * <p>{@code UtilisateurDetailsService} fait {@code .roles(typeUtilisateur.name())} :
 * chaque valeur produit donc exactement une autorite, {@code ROLE_} suivi du nom.
 * Ce n est <b>pas</b> un discriminant JPA — l entite ne declare ni
 * {@code @Inheritance} ni {@code @DiscriminatorColumn} — de sorte qu ajouter une
 * valeur n ajoute aucune classe.</p>
 *
 * <p><b>Le super-administrateur n herite pas ici, mais par la hierarchie.</b> Une
 * enumeration Java ne se derive pas ; c est le bean {@code RoleHierarchy} de
 * {@code SecuriteConfig} qui fait que {@code ROLE_SUPER_ADMINISTRATEUR} implique
 * {@code ROLE_ADMINISTRATEUR}. Les treize services annotes
 * {@code hasRole('ADMINISTRATEUR')} n ont donc pas a connaitre ce role.</p>
 */
public enum TypeUtilisateur {

    MEMBRE,

    ADMINISTRATEUR,

    /**
     * Administrateur qui peut en outre creer, suspendre et reactiver des comptes
     * administrateurs (CdC 5.2.3). Ces pouvoirs sont exclusifs : un administrateur
     * ordinaire ne peut agir sur aucun autre compte privilegie, pas meme le sien.
     */
    SUPER_ADMINISTRATEUR;

    /**
     * Le compte relève-t-il du back-office ?
     *
     * <p>Vrai pour les deux natures privilegiees. Ce predicat existe parce que la
     * question « est-ce un administrateur ? » se pose au domaine, ou la hierarchie
     * Spring Security n a pas cours : elle ne vaut que pour l autorisation.</p>
     */
    public boolean estPrivilegie() {
        return this == ADMINISTRATEUR || this == SUPER_ADMINISTRATEUR;
    }
}
