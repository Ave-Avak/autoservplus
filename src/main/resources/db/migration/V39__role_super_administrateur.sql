-- =====================================================================================
-- Role super-administrateur (CdC 5.2.3).
--
-- 1) UNE VALEUR D ENUMERATION, PAS UNE COLONNE DE PLUS
--
--    type_utilisateur n est PAS un discriminant JPA malgre ce que dit le Javadoc de
--    l entite : ni @Inheritance ni @DiscriminatorColumn, seulement une colonne
--    @Enumerated(STRING). Ajouter une valeur ne cree donc aucune hierarchie de
--    classes, et l autorite Spring Security en decoule sans code supplementaire —
--    UtilisateurDetailsService fait .roles(typeUtilisateur.name()).
--
--    L alternative — un booleen super_admin a cote du type — aurait laisse un compte
--    porter un role ET un drapeau, soit deux sources pour une meme question, et
--    imposerait de composer les autorites a la main.
--
-- 2) AUCUNE LIGNE EXISTANTE N EST TOUCHEE
--
--    Le CHECK est elargi, jamais resserre : toute ligne valide avant l est apres. Le
--    compte administrateur seme par V10 reste ADMINISTRATEUR. Le premier
--    super-administrateur naitra de la configuration d exploitation, pas d ici — un
--    compte privilegie ne doit pas exister sans que l exploitant l ait voulu, et un
--    mot de passe en dur dans une migration est ce que le registre proscrit deja pour
--    le compte de demonstration.
--
-- 3) POURQUOI PAS DE CONTRAINTE « AU MOINS UN SUPER-ADMINISTRATEUR »
--
--    La regle « il doit rester un super-administrateur actif » est une invariante de
--    POPULATION : elle porte sur le resultat d un COUNT, qu aucun CHECK de ligne ne
--    peut exprimer. Un trigger le pourrait, au prix d un comptage a chaque ecriture
--    sur la table des comptes. Elle est donc portee par le service, sous verrou, et
--    le deploiement initial doit pouvoir partir de zero super-administrateur — ce
--    qu un trigger interdirait.
-- =====================================================================================

ALTER TABLE utilisateur
    DROP CONSTRAINT ck_utilisateur_type;

ALTER TABLE utilisateur
    ADD CONSTRAINT ck_utilisateur_type
        CHECK (type_utilisateur IN ('MEMBRE', 'ADMINISTRATEUR', 'SUPER_ADMINISTRATEUR'));

COMMENT ON COLUMN utilisateur.type_utilisateur IS
    'MEMBRE, ADMINISTRATEUR ou SUPER_ADMINISTRATEUR. Le super-administrateur est un '
    'administrateur qui peut en outre creer, suspendre et reactiver les comptes '
    'administrateurs : la hierarchie Spring Security fait que ROLE_SUPER_ADMINISTRATEUR '
    'implique ROLE_ADMINISTRATEUR.';
