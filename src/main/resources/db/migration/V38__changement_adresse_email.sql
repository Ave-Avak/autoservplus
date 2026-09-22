-- =====================================================================================
-- Changement d adresse de courriel avec verification prealable (CdC 5.2.2, option A1).
--
-- 1) POURQUOI DES COLONNES DEDIEES, ET NON jeton_verification
--
--    Le socle V1 porte deja un couple jeton_verification / jeton_expiration. Il est
--    tentant de le reemployer ; ce serait un defaut de securite, pas une economie.
--
--    Ce couple sert DEJA a deux flux : l activation du compte (InscriptionService) et
--    la reinitialisation du mot de passe (MotDePasseService), qui l ecrasent l un
--    l autre. Y verser un troisieme usage aurait deux consequences.
--
--    D abord, toute demande de reinitialisation annulerait en silence un changement
--    d adresse en cours — et le formulaire de reinitialisation est PUBLIC : n importe
--    qui pourrait ainsi neutraliser la demande d un membre.
--
--    Ensuite et surtout, les jetons deviendraient interchangeables. Le lien de
--    confirmation part vers la NOUVELLE adresse, qui n est pas encore prouvee ; s il
--    etait accepte par /mot-de-passe/nouveau, son porteur fixerait le mot de passe du
--    compte. Le flux destine a verifier une adresse deviendrait un flux de prise de
--    controle. Deux usages differents exigent deux jetons differents.
--
-- 2) L INVARIANT DES TROIS COLONNES
--
--    Adresse en attente, jeton et echeance ne veulent rien dire isolement : une
--    adresse sans jeton ne serait jamais confirmable, un jeton sans adresse
--    n appliquerait rien. Le CHECK impose donc « les trois, ou aucune », comme
--    ck_photo_origine_unique impose « exactement une origine » — l invariant s enonce
--    au lieu de se deduire.
--
-- 3) UNICITE DE L ADRESSE EN ATTENTE
--
--    Sans elle, deux membres pourraient reserver la meme adresse et le second a
--    confirmer echouerait sur uq_utilisateur_email, au pire moment : apres avoir suivi
--    un lien recu par courriel. L index refuse la seconde DEMANDE plutot que la
--    seconde CONFIRMATION.
--
--    Il est partiel (WHERE ... IS NOT NULL) : la quasi-totalite des lignes n a aucune
--    demande en cours et n a pas a etre indexee. Il porte sur lower(...) parce que les
--    adresses sont comparees sans egard a la casse partout ailleurs dans le projet.
--
--    Il ne remplace PAS le controle applicatif contre uq_utilisateur_email : une
--    adresse peut etre libre a la demande et prise a la confirmation, par une
--    inscription intervenue entre les deux. Le service revalide donc au moment
--    d appliquer.
--
-- 4) RIEN A REPRENDRE
--
--    Colonnes nouvelles et nullables : aucune ligne existante n est touchee, aucune
--    valeur par defaut n est necessaire. Le changement d adresse commence a exister au
--    premier membre qui le demande.
-- =====================================================================================

ALTER TABLE utilisateur
    ADD COLUMN email_en_attente            VARCHAR(180),
    ADD COLUMN jeton_changement_email      VARCHAR(64),
    ADD COLUMN jeton_changement_expiration TIMESTAMPTZ;

ALTER TABLE utilisateur
    ADD CONSTRAINT ck_utilisateur_changement_email_complet
        CHECK (num_nonnulls(email_en_attente, jeton_changement_email,
                            jeton_changement_expiration) IN (0, 3));

CREATE UNIQUE INDEX uq_utilisateur_email_en_attente
    ON utilisateur (lower(email_en_attente))
    WHERE email_en_attente IS NOT NULL;

-- Lecture par jeton a la confirmation : meme patron partiel que ix_utilisateur_jeton.
CREATE INDEX ix_utilisateur_jeton_changement
    ON utilisateur (jeton_changement_email)
    WHERE jeton_changement_email IS NOT NULL;

COMMENT ON COLUMN utilisateur.email_en_attente IS
    'Nouvelle adresse demandee, non encore prouvee. L adresse en vigueur reste email '
    'jusqu a la confirmation : une adresse erronee ne doit jamais enfermer le membre '
    'hors de son compte.';
COMMENT ON COLUMN utilisateur.jeton_changement_email IS
    'Jeton du lien de confirmation, DISTINCT de jeton_verification : ce dernier ouvre '
    'aussi la reinitialisation du mot de passe.';
