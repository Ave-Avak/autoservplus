-- =====================================================================================
-- Jetons des liens de courriel stockés par leur seule empreinte, et jeton de
-- réinitialisation séparé du jeton de vérification.
--
-- 1) POURQUOI
--
--    jeton_verification et jeton_changement_email étaient cherchés par leur valeur
--    brute : lire la base suffisait à suivre un lien pendant sa validité. Le jeton de
--    définition (V41) était déjà stocké en SHA-256, les trois autres le rejoignent.
--    La réinitialisation écrivait de plus dans jeton_verification : un lien de
--    vérification (24 h) ouvrait le formulaire de nouveau mot de passe, et un lien de
--    réinitialisation confirmait une adresse. Elle reçoit ses propres colonnes.
--
-- 2) EMPREINTE
--
--    encode(sha256(convert_to(x, 'UTF8')), 'hex') : SHA-256 du texte en UTF-8, rendu en
--    hexadécimal minuscule. C'est exactement JetonLien.empreinte côté Java, ce qui garde
--    valable un lien parti avant la migration. 64 caractères, la taille des colonnes.
--
-- 3) LES JETONS EN ATTENTE DANS jeton_verification
--
--    La colonne portait deux usages, que rien ne distinguait sauf le compte. Critère
--    retenu : sur un compte dont l'adresse est vérifiée (email_verifie), le jeton ne
--    peut être qu'une réinitialisation. Il est déplacé, haché, vers la nouvelle colonne
--    avec son échéance. Sur un compte non vérifié, c'est une vérification (la
--    réinitialisation d'un tel compte envoyait un rappel de vérification) : il est
--    haché en place. Un jeton sans échéance était déjà tenu pour expiré, il est effacé.
-- =====================================================================================

ALTER TABLE utilisateur
    ADD COLUMN jeton_reinitialisation_hache      VARCHAR(64),
    ADD COLUMN jeton_reinitialisation_expiration TIMESTAMPTZ;

ALTER TABLE utilisateur ADD CONSTRAINT ck_utilisateur_jeton_reinitialisation
    CHECK (num_nonnulls(jeton_reinitialisation_hache,
                        jeton_reinitialisation_expiration) IN (0, 2));

CREATE UNIQUE INDEX uq_utilisateur_jeton_reinitialisation
    ON utilisateur (jeton_reinitialisation_hache)
    WHERE jeton_reinitialisation_hache IS NOT NULL;

-- Réinitialisations en attente, sur les comptes vérifiés.
UPDATE utilisateur
   SET jeton_reinitialisation_hache      = encode(sha256(convert_to(jeton_verification, 'UTF8')), 'hex'),
       jeton_reinitialisation_expiration = jeton_expiration,
       jeton_verification                = NULL,
       jeton_expiration                  = NULL
 WHERE email_verifie
   AND jeton_verification IS NOT NULL
   AND jeton_expiration IS NOT NULL;

UPDATE utilisateur
   SET jeton_verification = NULL,
       jeton_expiration   = NULL
 WHERE jeton_verification IS NOT NULL
   AND (email_verifie OR jeton_expiration IS NULL);

-- Vérifications en attente, hachées en place.
UPDATE utilisateur
   SET jeton_verification = encode(sha256(convert_to(jeton_verification, 'UTF8')), 'hex')
 WHERE jeton_verification IS NOT NULL;

-- Changements d'adresse en attente, hachés en place.
UPDATE utilisateur
   SET jeton_changement_email = encode(sha256(convert_to(jeton_changement_email, 'UTF8')), 'hex')
 WHERE jeton_changement_email IS NOT NULL;

-- Le nom dit désormais ce que la colonne contient, comme jeton_definition_hache.
ALTER TABLE utilisateur RENAME COLUMN jeton_verification TO jeton_verification_hache;
ALTER TABLE utilisateur RENAME COLUMN jeton_changement_email TO jeton_changement_email_hache;

COMMENT ON COLUMN utilisateur.jeton_verification_hache IS
    'Empreinte SHA-256 du jeton de vérification d''adresse. Le jeton en clair ne vit que dans le lien.';
COMMENT ON COLUMN utilisateur.jeton_changement_email_hache IS
    'Empreinte SHA-256 du jeton de changement d''adresse. Le jeton en clair ne vit que dans le lien.';
COMMENT ON COLUMN utilisateur.jeton_reinitialisation_hache IS
    'Empreinte SHA-256 du jeton de réinitialisation du mot de passe, valable une heure, à usage unique.';

-- =====================================================================================
-- Réinitialisation consignée au journal de sécurité du compte.
--
-- V42 n'est pas modifiée. La contrainte est supprimée puis recréée, PostgreSQL ne
-- sachant pas élargir un CHECK en place.
-- =====================================================================================

ALTER TABLE evenement_securite_compte DROP CONSTRAINT ck_evenement_securite_type;

ALTER TABLE evenement_securite_compte ADD CONSTRAINT ck_evenement_securite_type
    CHECK (type IN ('MFA_ACTIVE', 'MFA_DESACTIVE', 'SECOND_FACTEUR_ECHEC',
                    'SECOND_FACTEUR_VERROUILLAGE', 'CODE_SECOURS_CONSOMME',
                    'CODES_SECOURS_REGENERES', 'MFA_REINITIALISE',
                    'MOT_DE_PASSE_DEFINI', 'MOT_DE_PASSE_CHANGE',
                    'ADMINISTRATEUR_CREE', 'LIEN_DEFINITION_ENVOYE',
                    'CONNEXION_VERROUILLAGE', 'MOT_DE_PASSE_REINITIALISE'));

-- =====================================================================================
-- Commentaire de table posé par V40, qui renvoyait à un document hors du dépôt.
-- =====================================================================================

COMMENT ON TABLE historique_statut_utilisateur IS
    'Journal append-only des suspensions et réactivations de comptes (CdC 5.2.3). '
    'Les colonnes updated_at / updated_by y sont inertes, comme sur les autres tables '
    'append-only du schéma : uniformité assumée, la même forme pour toutes les tables.';
