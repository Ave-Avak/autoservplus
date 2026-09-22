package be.autoservplus.identite.service;

import be.autoservplus.common.exception.RefusTraduit;

/**
 * Refus d une demande de changement d adresse (CdC 5.2.2).
 *
 * <p>Type dedie pour que le controleur n attrape que ces refus-la : une
 * {@link RefusTraduit} venue d ailleurs dans la meme pile ne doit pas s afficher sur
 * le formulaire d adresse comme si elle le concernait. Le porteur de cle, lui, est
 * commun — voir {@link RefusTraduit} pour le motif.</p>
 */
public class ChangementEmailRefuseException extends RefusTraduit {

    public ChangementEmailRefuseException(String cleMessage, String message) {
        super(cleMessage, message);
    }
}
