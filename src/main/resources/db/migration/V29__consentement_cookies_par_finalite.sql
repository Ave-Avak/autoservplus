-- =====================================================================================
-- Consentement aux cookies, trace par finalite (F25 — recommandations de l Autorite
-- de Protection des Donnees, P410-412 du cahier des charges).
--
-- 1) POURQUOI UNE PREUVE PAR FINALITE ET NON UNE LIGNE GLOBALE
--
--    Le socle V1 n admettait qu un type COOKIES, donc une seule ligne portant un
--    unique booleen accorde. Ce modele ne peut pas restituer le resultat de l ecran
--    « Personnaliser » impose par le cahier des charges : un visiteur qui accepte la
--    mesure d audience mais refuse le marketing produirait une ligne dont le booleen
--    ne dit ni l un ni l autre. La preuve serait alors inexploitable au moment meme
--    ou elle sert — devant l autorite de controle.
--
--    L article 4.11 du RGPD definit le consentement comme « specifique » : il se
--    donne finalite par finalite. La preuve suit donc la meme granularite, une ligne
--    par finalite optionnelle. Aucune colonne n est ajoutee : le type de document
--    designe deja ce a quoi l on consent, il suffit qu il designe la finalite.
--
-- 2) POURQUOI AUCUNE LIGNE POUR LES COOKIES STRICTEMENT NECESSAIRES
--
--    Les cookies techniques (session, jeton CSRF, et la preference cookies
--    elle-meme) ne relevent pas du consentement : ils sont exemptes car strictement
--    necessaires au service demande par l utilisateur. Enregistrer un « consentement »
--    pour eux serait faux sur le fond — on ne consent pas a ce qu on ne peut pas
--    refuser — et trompeur sur la forme, en laissant croire a un choix inexistant.
--    Aucun type COOKIES_NECESSAIRE n est donc cree.
--
-- 3) POURQUOI LA VALEUR COOKIES DU SOCLE EST CONSERVEE
--
--    F25 ne l emploie plus, mais la retirer du CHECK invaliderait toute ligne deja
--    ecrite avec cette valeur en demonstration ou en pre-production, sans rien
--    apporter en retour. Une valeur admise et inutilisee ne coute rien ; une
--    migration qui casse des donnees existantes, si.
--
-- 4) TABLE APPEND-ONLY, INCHANGEE
--
--    Aucun UPDATE n est introduit : chaque decision — premiere visite comme
--    modification ulterieure par « Gerer mes cookies » — ecrit de nouvelles lignes
--    horodatees. Une preuve que l on ecrase cesse d etre une preuve, et l historique
--    des choix successifs est precisement ce qu il faut pouvoir montrer.
-- =====================================================================================

ALTER TABLE consentement DROP CONSTRAINT ck_consentement_type;

ALTER TABLE consentement ADD CONSTRAINT ck_consentement_type
    CHECK (type_document IN ('CGV',
                             'POLITIQUE_CONFIDENTIALITE',
                             'COOKIES',
                             'NEWSLETTER',
                             'COOKIES_ANALYTIQUE',
                             'COOKIES_MARKETING'));

COMMENT ON COLUMN consentement.type_document IS
    'Objet du consentement. Pour les cookies, la finalite optionnelle elle-meme '
    '(COOKIES_ANALYTIQUE, COOKIES_MARKETING) : le consentement est specifique par '
    'finalite (art. 4.11 RGPD). Les cookies strictement necessaires sont exemptes et '
    'ne produisent aucune ligne.';
