/*
 * Confirmation avant une action destructive.
 *
 * Le message est lu dans data-message, rédigé par le gabarit dans la langue de la page.
 * Le script n'en compose aucun : une phrase écrite ici resterait en français. Les
 * chaînes transitent par des attributs data-* plutôt que d'être insérées dans un
 * gestionnaire d'événement : une chaîne issue de la base et injectée dans du
 * JavaScript ouvrirait une faille d'injection de code.
 *
 * Cette confirmation est un confort d'interface, pas une protection : l'action est en
 * POST et le service vérifie les autorisations et la cohérence métier.
 */
document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('.formulaire-suppression').forEach(function (formulaire) {
        formulaire.addEventListener('submit', function (evenement) {
            const message = formulaire.dataset.message;
            if (message && !window.confirm(message)) {
                evenement.preventDefault();
            }
        });
    });
});