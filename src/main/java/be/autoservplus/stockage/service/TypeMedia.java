package be.autoservplus.stockage.service;

import java.util.Arrays;
import java.util.Optional;

/**
 * Types de fichiers admis a l upload, reconnus a leurs <b>octets d en-tete</b>
 * (fondation upload).
 *
 * <p><b>Liste blanche, jamais liste noire.</b> Enumerer ce qui est interdit laisse
 * passer tout ce qu on n a pas prevu ; enumerer ce qui est permis fait l inverse. Un
 * format ajoute plus tard doit l etre ici, explicitement.</p>
 *
 * <p><b>Le type est deduit du contenu, pas de ce que le client annonce.</b>
 * L extension du nom de fichier et l en-tete {@code Content-Type} sont tous deux
 * choisis par l appelant : un script deguise en {@code .jpg} passerait n importe quel
 * controle fonde sur eux. Les premiers octets, eux, sont ceux du fichier reel.</p>
 *
 * <p><b>Le SVG est volontairement absent</b> malgre son statut d image : c est du XML
 * qui peut porter du script, et un SVG servi en {@code image/svg+xml} sur notre propre
 * domaine s executerait dans le contexte du site. Les trois formats retenus sont des
 * formats binaires inertes.</p>
 */
public enum TypeMedia {

    JPEG("image/jpeg", ".jpg", new int[]{0xFF, 0xD8, 0xFF}),
    PNG("image/png", ".png", new int[]{0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}),
    /** WebP : conteneur RIFF, la signature complete est verifiee par {@link #correspond}. */
    WEBP("image/webp", ".webp", new int[]{0x52, 0x49, 0x46, 0x46});

    private final String typeMime;
    private final String extension;
    private final int[] signature;

    TypeMedia(String typeMime, String extension, int[] signature) {
        this.typeMime = typeMime;
        this.extension = extension;
        this.signature = signature;
    }

    /** Nombre d octets a lire pour trancher, tous formats confondus. */
    public static int octetsNecessaires() {
        return 12;
    }

    /** Liste lisible des formats admis, pour les messages d erreur et l attribut accept. */
    public static String libelleDesTypesAdmis() {
        return String.join(", ", Arrays.stream(values()).map(t -> t.extension).toList());
    }

    public static String typesMimeAdmis() {
        return String.join(",", Arrays.stream(values()).map(t -> t.typeMime).toList());
    }

    /** Reconnait le format d apres les premiers octets, ou rien si aucun ne correspond. */
    public static Optional<TypeMedia> reconnaitre(byte[] entete) {
        return Arrays.stream(values()).filter(type -> type.correspond(entete)).findFirst();
    }

    private boolean correspond(byte[] entete) {
        if (entete == null || entete.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((entete[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        // RIFF n identifie qu un conteneur : AVI et WAV le portent aussi. Le marqueur
        // WEBP, aux octets 8 a 11, est ce qui distingue reellement une image.
        if (this == WEBP) {
            return entete.length >= 12
                    && entete[8] == 'W' && entete[9] == 'E' && entete[10] == 'B' && entete[11] == 'P';
        }
        return true;
    }

    public String typeMime() {
        return typeMime;
    }

    public String extension() {
        return extension;
    }
}
