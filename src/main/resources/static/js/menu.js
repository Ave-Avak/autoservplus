/*
 * Menu repliable de l en-tete, motif « Disclosure Navigation » (WAI-ARIA Authoring
 * Practices Guide) : un bouton, aria-expanded pour l etat, aria-controls pour le panneau.
 *
 * Amelioration progressive. Le bouton nait avec l attribut hidden : sans ce script, il
 * reste cache et le panneau s affiche en entier. Le script revele le bouton et replie
 * le panneau ; la feuille de style ne masque un panneau replie que sous la rupture
 * (40em), au-dessus le menu reste deplie quel que soit l etat du bouton.
 *
 * Aucune chaine ici : le libelle du bouton vient du gabarit, donc de l i18n. Fichier
 * externe, sans script en ligne : la politique de securite (script-src 'self') reste
 * inchangee.
 */
document.querySelectorAll('.bouton-menu[aria-controls]').forEach(function (bouton) {
    const panneau = document.getElementById(bouton.getAttribute('aria-controls'));
    if (!panneau) {
        return;
    }

    function deplier(ouvert) {
        bouton.setAttribute('aria-expanded', String(ouvert));
        panneau.classList.toggle('est-replie', !ouvert);
    }

    deplier(false);
    bouton.hidden = false;

    bouton.addEventListener('click', function () {
        deplier(bouton.getAttribute('aria-expanded') !== 'true');
    });

    // Echap referme le menu et rend le focus au bouton, depuis le bouton comme depuis
    // un lien du panneau. Au-dessus de la rupture le bouton n est pas affiche
    // (offsetParent nul) : rien a refermer.
    bouton.closest('nav').addEventListener('keydown', function (evenement) {
        if (evenement.key === 'Escape' && bouton.getAttribute('aria-expanded') === 'true'
                && bouton.offsetParent !== null) {
            deplier(false);
            bouton.focus();
        }
    });
});
