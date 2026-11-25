package be.autoservplus.qualite;

import be.autoservplus.common.ReserveAuxTests;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.asm.AnnotationVisitor;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.Handle;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;
import org.springframework.asm.Type;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Une surcharge marquée {@link ReserveAuxTests} applique un choix par défaut que l'application
 * doit poser elle-même (choix des pièces, langue du document). Le test lit le bytecode de
 * {@code target/classes} et refuse tout appel vers une telle surcharge, référence de méthode
 * comprise.
 *
 * <p>Bytecode plutôt que texte source : un appel se reconnaît à sa signature exacte, que la
 * lecture du source ne distingue pas d'une surcharge voisine au même nom. La bibliothèque ASM
 * est celle que Spring embarque déjà, aucune dépendance n'est ajoutée.</p>
 */
@DisplayName("Surcharges réservées aux tests")
class SurchargesReserveesAuxTestsTest {

    private static final Path CLASSES = Path.of("target/classes");
    private static final Path CLASSES_DE_TEST = Path.of("target/test-classes");
    private static final String ANNOTATION = Type.getDescriptor(ReserveAuxTests.class);

    @Test
    @DisplayName("aucun appel depuis le code de l'application")
    void aucunAppelDepuisSrcMain() throws IOException {
        Set<String> reservees = surchargesReservees();

        assertThat(appelsVers(reservees, CLASSES)).isEmpty();
    }

    @Test
    @DisplayName("les surcharges attendues sont bien marquées")
    void surchargesMarquees() throws IOException {
        // Sans ce contrôle, une rétention changée ou une annotation perdue rendrait le test
        // précédent vide, donc vert.
        assertThat(surchargesReservees()).contains(
                "be/autoservplus/reservation/domain/Rdv.<init>",
                "be/autoservplus/vente/domain/Panier.ajouterService",
                "be/autoservplus/intervention/domain/Intervention.ajouterLigneMainOeuvre",
                "be/autoservplus/reservation/service/RdvService.reserver",
                "be/autoservplus/vente/service/PanierService.ajouterService",
                "be/autoservplus/intervention/service/InterventionService.ajouterLigneMainOeuvre");
    }

    @Test
    @DisplayName("le contrôle voit les appels, ceux des tests en témoignent")
    void detecteLesAppels() throws IOException {
        assertThat(appelsVers(surchargesReservees(), CLASSES_DE_TEST)).isNotEmpty();
    }

    /** Membres marqués, sous la forme {@code propriétaire.nom} suivie de leur descripteur. */
    private static Set<String> surchargesReservees() throws IOException {
        Set<String> reservees = new HashSet<>();
        for (Path fichier : classes(CLASSES)) {
            lire(fichier, new ClassVisitor(Opcodes.ASM9) {
                private String proprietaire;

                @Override
                public void visit(int version, int acces, String nom, String signature, String parent,
                                  String[] interfaces) {
                    proprietaire = nom;
                }

                @Override
                public MethodVisitor visitMethod(int acces, String nom, String descripteur, String signature,
                                                 String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public AnnotationVisitor visitAnnotation(String type, boolean visible) {
                            if (ANNOTATION.equals(type)) {
                                reservees.add(proprietaire + "." + nom + descripteur);
                            }
                            return null;
                        }
                    };
                }
            });
        }
        // Forme sans descripteur aussi, pour que surchargesMarquees se lise sans signatures.
        Set<String> avecNoms = new HashSet<>(reservees);
        reservees.forEach(membre -> avecNoms.add(membre.substring(0, membre.indexOf('('))));
        return avecNoms;
    }

    private static List<String> appelsVers(Set<String> reservees, Path racine) throws IOException {
        List<String> appels = new ArrayList<>();
        for (Path fichier : classes(racine)) {
            lire(fichier, new ClassVisitor(Opcodes.ASM9) {
                private String appelant;

                @Override
                public void visit(int version, int acces, String nom, String signature, String parent,
                                  String[] interfaces) {
                    appelant = nom;
                }

                @Override
                public MethodVisitor visitMethod(int acces, String nomMethode, String descripteurMethode,
                                                 String signature, String[] exceptions) {
                    String depuis = appelant + "." + nomMethode;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(int opcode, String proprietaire, String nom, String descripteur,
                                                    boolean surInterface) {
                            noter(proprietaire + "." + nom + descripteur);
                        }

                        @Override
                        public void visitInvokeDynamicInsn(String nom, String descripteur, Handle amorce,
                                                           Object... arguments) {
                            for (Object argument : arguments) {
                                if (argument instanceof Handle cible) {
                                    noter(cible.getOwner() + "." + cible.getName() + cible.getDesc());
                                }
                            }
                        }

                        private void noter(String cible) {
                            if (reservees.contains(cible)) {
                                appels.add(depuis + " appelle " + cible);
                            }
                        }
                    };
                }
            });
        }
        return appels;
    }

    private static List<Path> classes(Path racine) throws IOException {
        assertThat(racine).as("compilé avant les tests").isDirectory();
        try (Stream<Path> fichiers = Files.walk(racine)) {
            return fichiers.filter(chemin -> chemin.toString().endsWith(".class")).toList();
        }
    }

    private static void lire(Path fichier, ClassVisitor visiteur) throws IOException {
        try (InputStream flux = Files.newInputStream(fichier)) {
            new ClassReader(flux).accept(visiteur, ClassReader.SKIP_FRAMES);
        }
    }
}
