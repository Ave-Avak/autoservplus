-- =====================================================================================
-- Renonciation au droit de retractation pour un service pleinement execute
-- (F12, article VI.53 du Code de droit economique).
--
-- 1) POURQUOI DEUX SUPPORTS, ET NON UN SEUL
--
--    La renonciation doit servir DEUX usages qui n ont ni le meme cycle de vie ni le
--    meme lecteur, et les confondre casserait l un ou l autre.
--
--    LA PREUVE va dans consentement, la ou vivent deja celle des CGV (F14) et celles
--    des cookies (F25) : append-only, horodatee, avec version du texte et adresse IP.
--    C est ce qu on montre a l Inspection economique ou a un juge. Un audit doit
--    pouvoir dire « toutes nos preuves de consentement sont dans cette table » ; y
--    faire une exception pour VI.53 obligerait a regarder a deux endroits, et une
--    preuve qu on oublie de consulter ne vaut pas mieux qu une preuve absente.
--
--    L ETAT METIER va sur commande, parce que consentement N A AUCUNE FK VERS
--    COMMANDE — ses colonnes sont utilisateur_id, type_document, version_acceptee,
--    accorde, date_consentement, adresse_ip. Or F30 doit repondre, POUR UNE COMMANDE
--    DONNEE, « celle-ci porte-t-elle une renonciation ? ». Rapprocher une ligne de
--    consentement d une commande par proximite d horodatage ne serait pas probant, et
--    deviendrait faux des qu un membre commande deux fois dans la meme minute.
--
--    Ce n est donc pas une duplication : l une est une trace juridique, l autre une
--    donnee de decision. La regle a tenir dans le code est simple et vaut d etre
--    enoncee — ON DECIDE SUR L ETAT, ON PROUVE PAR LA TRACE. Les deux sont ecrites
--    dans la MEME transaction que la commande : jamais d etat sans preuve, jamais de
--    preuve sans etat.
--
-- 2) POURQUOI accorde = false S ECRIT AUSSI
--
--    La case n est pas pre-cochee et n a pas a etre cochee : le client reste libre de
--    conserver son droit de retractation. Ne rien ecrire quand il refuse rendrait
--    l absence de ligne ambigue — a-t-il refuse, ou la question ne lui a-t-elle
--    jamais ete posee ? C est exactement le raisonnement deja tenu pour les cookies
--    en V29, et il vaut ici : ce qui se prouve, c est que la question A ETE POSEE.
--
-- 3) POURQUOI UN DEFAUT A false, ET NON NULL
--
--    NULL laisserait un troisieme etat — « on ne sait pas » — qu aucun code ne
--    saurait traiter au moment de decider d une retractation. false dit ce qui est
--    vrai de toute commande anterieure a F12 comme de toute commande de pieces :
--    aucune renonciation n a ete consentie, donc le droit de retractation s applique
--    pleinement. Le defaut le plus protecteur pour le client est aussi le seul qui se
--    lise sans ambiguite.
--
-- 4) AUCUNE TABLE AJOUTEE
--
--    33 tables metier avant, 33 apres. Une colonne et une valeur de CHECK.
-- =====================================================================================

-- Le CHECK enumere les types admis : il faut le remplacer, on ne peut pas l etendre.
ALTER TABLE consentement
    DROP CONSTRAINT ck_consentement_type;

ALTER TABLE consentement
    ADD CONSTRAINT ck_consentement_type
        CHECK (type_document IN ('CGV',
                                 'POLITIQUE_CONFIDENTIALITE',
                                 'COOKIES',
                                 'NEWSLETTER',
                                 'COOKIES_ANALYTIQUE',
                                 'COOKIES_MARKETING',
                                 'RENONCIATION_RETRACTATION'));

COMMENT ON COLUMN consentement.type_document IS
    'Document ou finalite auquel se rapporte le consentement. RENONCIATION_RETRACTATION : '
        'renonciation VI.53 pour un service pleinement execute (F12) — la PREUVE ; '
        'l etat lu par F30 est commande.renonciation_vi53.';

ALTER TABLE commande
    ADD COLUMN renonciation_vi53 BOOLEAN NOT NULL DEFAULT false;

COMMENT ON COLUMN commande.renonciation_vi53 IS
    'Le client a renonce a son droit de retractation pour execution immediate du '
        'service (art. VI.53 CDE, F12). ETAT lu par F30 pour decider ; la preuve '
        'horodatee est la ligne consentement de type RENONCIATION_RETRACTATION. '
        'false pour toute commande de pieces et toute commande anterieure a F12.';
