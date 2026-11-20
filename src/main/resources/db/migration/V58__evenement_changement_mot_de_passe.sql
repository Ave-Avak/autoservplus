-- =====================================================================================
-- Changement du mot de passe depuis le profil : session fermée après cinq mots de
-- passe actuels incorrects.
--
-- Une session volée suffirait sinon à essayer des mots de passe actuels sans limite
-- depuis l'écran de changement, puis à prendre le compte. Au cinquième échec, la
-- session est fermée et l'événement est consigné au journal de sécurité du compte,
-- sans verrouiller le compte : le titulaire n'a qu'à se reconnecter.
--
-- Le changement réussi réutilise MOT_DE_PASSE_CHANGE, déjà admis par V46.
-- La contrainte est supprimée puis recréée, PostgreSQL ne sachant pas élargir un CHECK
-- en place. V46 n'est pas modifiée.
-- =====================================================================================

ALTER TABLE evenement_securite_compte DROP CONSTRAINT ck_evenement_securite_type;

ALTER TABLE evenement_securite_compte ADD CONSTRAINT ck_evenement_securite_type
    CHECK (type IN ('MFA_ACTIVE', 'MFA_DESACTIVE', 'SECOND_FACTEUR_ECHEC',
                    'SECOND_FACTEUR_VERROUILLAGE', 'CODE_SECOURS_CONSOMME',
                    'CODES_SECOURS_REGENERES', 'MFA_REINITIALISE',
                    'MOT_DE_PASSE_DEFINI', 'MOT_DE_PASSE_CHANGE',
                    'ADMINISTRATEUR_CREE', 'LIEN_DEFINITION_ENVOYE',
                    'CONNEXION_VERROUILLAGE', 'MOT_DE_PASSE_REINITIALISE',
                    'CHANGEMENT_MOT_DE_PASSE_BLOQUE'));
