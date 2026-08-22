-- =====================================================================================
-- Aligne le marquage des lignes sur le dictionnaire de donnees (Livrable 09) :
-- un unique champ nullable `accord_membre` remplace le couple validee/refusee.
--
-- V20 avait introduit deux booleens en assumant l'ecart au dictionnaire. L'ecart
-- n'etait pas justifie : il coutait un CHECK pour interdire un etat absurde
-- (validee ET refusee) que le modele du dictionnaire rend INEXPRIMABLE, et il
-- cassait la tracabilite code <-> livrable, critere d'evaluation du TFE. On
-- corrige donc vers le dictionnaire, sans reecrire V20 : Flyway est forward-only,
-- l'historique doit montrer la correction, pas la dissimuler.
--
-- Encodage cible, porte par le COUPLE (ajoutee_en_cours, accord_membre) :
--   (false, NULL)  ligne du rendez-vous : le devis initial, accepte a la
--                  reservation. Hors dispositif RM-15 — on ne demande pas un
--                  accord sur ce que le membre a deja commande.
--   (true,  NULL)  ajout du garage en attente de la reponse du membre.
--   (true,  TRUE)  ajout accepte : entre dans le total facturable.
--   (true,  FALSE) ajout refuse : CONSERVE au dossier comme trace du defaut
--                  constate, EXCLU du total, non execute.
--
-- Trois etats sur un champ a trois valeurs, la ou deux booleens en decrivaient
-- quatre dont un interdit. Le NULL n'est pas ici une absence de donnee mais une
-- valeur metier — « pas de reponse » — d'ou l'absence volontaire de DEFAULT :
-- un DEFAULT donnerait a l'insertion une reponse que le membre n'a pas donnee.
--
-- Le backfill lit les deux booleens dans l'ordre de leur priorite metier. Il
-- reste correct meme sur la combinaison (false, false) laissee par une mise en
-- attente : elle retombe sur NULL, qui est bien « en attente ».
-- =====================================================================================

ALTER TABLE ligne_intervention ADD COLUMN accord_membre BOOLEAN;

UPDATE ligne_intervention
SET accord_membre = CASE
        WHEN NOT ajoutee_en_cours THEN NULL   -- devis initial : hors RM-15
        WHEN refusee              THEN FALSE  -- le membre a ecarte la ligne
        WHEN validee              THEN TRUE   -- ajout acquis (accord, ou sous le seuil)
        ELSE NULL                             -- ni validee ni refusee : en attente
    END;

ALTER TABLE ligne_intervention DROP CONSTRAINT ck_ligne_interv_validation;
ALTER TABLE ligne_intervention
    DROP COLUMN validee,
    DROP COLUMN refusee;

-- Durcissement que le couple de booleens ne savait pas exprimer : une ligne du
-- devis initial ne peut porter aucun accord, puisqu'on ne lui en a jamais demande.
-- Le CHECK rend la premiere ligne de l'encodage impossible a violer, y compris par
-- un acces direct a la base. Ajout par rapport au dictionnaire, pas divergence :
-- il interdit un etat que le dictionnaire ne prevoit pas, il n'en autorise aucun.
ALTER TABLE ligne_intervention
    ADD CONSTRAINT ck_ligne_interv_accord
    CHECK (ajoutee_en_cours OR accord_membre IS NULL);

COMMENT ON COLUMN ligne_intervention.accord_membre IS
    'Reponse du membre sur une ligne ajoutee en cours d intervention (RM-15). '
    'NULL : aucune reponse — ligne du devis initial si ajoutee_en_cours vaut false, '
    'ajout en attente de validation sinon. TRUE : accepte, entre dans le total '
    'facturable. FALSE : refuse, conserve au dossier comme trace du defaut constate, '
    'exclu du total, non execute.';
