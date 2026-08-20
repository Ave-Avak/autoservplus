/*
 * Confirmation avant une action destructive.
 *
 * La designation du vehicule transite par un attribut data-* plutot que d etre insere
 * directement dans un gestionnaire d evenement : une chaine issue de la base et injectee
 * dans du JavaScript ouvrirait une faille d injection de code.
 *
 * Cette confirmation est un confort d interface, pas une protection : la suppression est
 * en POST et le service verifie que le demandeur est bien le proprietaire.
 */
document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('.formulaire-suppression').forEach(function (formulaire) {
        formulaire.addEventListener('submit', function (evenement) {
            const designation = formulaire.dataset.designation || 'cet élément';
            if (!window.confirm('Retirer ' + designation + ' de votre parc ?')) {
                evenement.preventDefault();
            }
        });
    });
});