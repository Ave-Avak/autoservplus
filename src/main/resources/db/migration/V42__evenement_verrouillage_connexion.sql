-- =====================================================================================
-- Verrouillage par mot de passe consigne au journal de securite du compte.
--
-- Le verrouillage apres cinq mots de passe faux (TentativesConnexionService) ne
-- laissait qu une ligne de journal applicatif : ni la fiche de l exploitant ni le
-- titulaire ne pouvaient le voir. Une seule valeur ajoutee, pour le PASSAGE en
-- verrouillage ; un mot de passe faux isole ne s ecrit pas — n importe qui peut saisir
-- une adresse connue, et le journal du titulaire se remplirait des gestes d un tiers.
--
-- V41 n est pas modifiee : une migration appliquee ne change plus. La contrainte est
-- supprimee puis recreee, PostgreSQL ne sachant pas elargir un CHECK en place.
-- =====================================================================================

ALTER TABLE evenement_securite_compte DROP CONSTRAINT ck_evenement_securite_type;

ALTER TABLE evenement_securite_compte ADD CONSTRAINT ck_evenement_securite_type
    CHECK (type IN ('MFA_ACTIVE', 'MFA_DESACTIVE', 'SECOND_FACTEUR_ECHEC',
                    'SECOND_FACTEUR_VERROUILLAGE', 'CODE_SECOURS_CONSOMME',
                    'CODES_SECOURS_REGENERES', 'MFA_REINITIALISE',
                    'MOT_DE_PASSE_DEFINI', 'MOT_DE_PASSE_CHANGE',
                    'ADMINISTRATEUR_CREE', 'LIEN_DEFINITION_ENVOYE',
                    'CONNEXION_VERROUILLAGE'));
