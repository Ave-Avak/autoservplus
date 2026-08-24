package be.autoservplus.communication.service;

/**
 * Fichier joint a un courriel transactionnel.
 *
 * <p>Le contenu voyage en <b>octets deja produits</b> : le module
 * {@code communication} ne sait pas fabriquer un calendrier, pas plus qu il ne sait
 * fabriquer une facture. C est ce qui lui evite de dependre du module
 * {@code reservation}, comme {@link DetailsRdvCourriel} lui evite de dependre de
 * l entite {@code Rdv}.</p>
 *
 * <p><b>Pourquoi une piece jointe ici, alors que la facture est un lien ?</b> Les
 * deux documents n ont pas la meme nature. Une facture est une piece comptable
 * nominative : la lier oblige a s authentifier pour la lire, et evite qu elle
 * dorme dans la boite aux lettres du destinataire pendant dix ans. Un fichier
 * d agenda n a de valeur qu au moment ou il arrive, il ne contient rien que le
 * membre ne sache deja, et surtout il ne rend service que joint : un lien de
 * telechargement ouvert dans un navigateur de telephone n atteint pas
 * l application de calendrier.</p>
 *
 * @param nomFichier nom propose au destinataire, extension comprise.
 * @param typeMime   type MIME declare (par exemple {@code text/calendar}).
 * @param contenu    octets du fichier.
 */
public record PieceJointeCourriel(String nomFichier, String typeMime, byte[] contenu) {

    /** Taille du fichier, la seule chose qu une implementation de developpement puisse journaliser utilement. */
    public int tailleOctets() {
        return contenu == null ? 0 : contenu.length;
    }
}
