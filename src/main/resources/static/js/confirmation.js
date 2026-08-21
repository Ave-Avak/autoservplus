/*
 * Confirmation avant une action destructive.
 *
 * Le message affiche est lu depuis data-message si present, sinon compose a partir de
 * data-designation pour le message historique de suppression de vehicule. Les chaines
 * transitent par des attributs data-* plutot que d etre insere directement dans un
 * gestionnaire d evenement : une chaine issue de la base et injectee dans du
 * JavaScript ouvrirait une faille d injection de code.
 *
 * Cette confirmation est un confort d interface, pas une protection : l action est en
 * POST et le service verifie les autorisations et la coherence metier.
 */
document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('.formulaire-suppression').forEach(function (formulaire) {
        formulaire.addEventListener('submit', function (evenement) {
            const designation = formulaire.dataset.designation || 'cet élément';
            const message = formulaire.dataset.message || ('Retirer ' + designation + ' de votre parc ?');
            if (!window.confirm(message)) {
                evenement.preventDefault();
            }
        });
    });
});