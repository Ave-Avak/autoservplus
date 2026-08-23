package be.autoservplus.messagerie.domain;

/**
 * Cote d ou part un message, aligne sur le CHECK {@code ck_message_role} du socle V7.
 *
 * <p>Deux valeurs et deux seulement : la messagerie relie un membre au <b>garage</b>,
 * pas des membres entre eux. Le role n est pas deduit du type de compte de
 * l expediteur mais pose a l ecriture — un administrateur qui serait aussi client
 * ecrit tantot pour lui, tantot pour le garage, et c est le fil qui doit le dire.</p>
 */
public enum RoleExpediteur {

    MEMBRE,
    ADMINISTRATEUR
}
