package be.autoservplus.identite.service;

import be.autoservplus.common.exception.RegleMetierException;

/**
 * Refus d une demande de changement d adresse (CdC 5.2.2).
 *
 * <p><b>Pourquoi un type dedie plutot qu un code RM.</b> Aucune regle du cahier des
 * charges ne porte ces six refus, et {@link RegleMetierException} interdit de
 * fabriquer un pseudo-code : la tracabilite « RM = exigence du CdC » ne vaut que
 * parce qu elle renvoie a une exigence reelle. Le constructeur sans code est fait
 * pour ce cas.</p>
 *
 * <p>Restait a donner au controleur de quoi <b>traduire</b> le refus, l ecran devant
 * suivre la langue de session (F6). C est le role de {@link #getCleMessage()} : une
 * cle i18n <b>nommee comme telle</b>, au lieu d un code de tracabilite detourne en
 * cle de traduction. Le message herite, lui, reste la phrase francaise affichable
 * qu exige le contrat — celle qui part aux journaux et que lirait tout appelant non
 * web.</p>
 *
 * <p>Le controleur y gagne : il traduit la cle que le refus porte, au lieu de tenir
 * une table de correspondance que chaque refus nouveau obligeait a completer — et
 * dont l oubli passait inapercu jusqu a l affichage.</p>
 */
public class ChangementEmailRefuseException extends RegleMetierException {

    private final transient String cleMessage;

    /**
     * @param cleMessage cle i18n du texte a afficher
     * @param message    phrase francaise affichable sans retouche, pour les journaux
     *                   et les appelants non web
     */
    public ChangementEmailRefuseException(String cleMessage, String message) {
        super(message);
        this.cleMessage = cleMessage;
    }

    public String getCleMessage() {
        return cleMessage;
    }
}
