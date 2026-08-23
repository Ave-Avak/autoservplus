-- =====================================================================================
-- Seconde origine d une intervention : une commande de services payee (F12-b).
--
-- 1) CE QUE LE SOCLE PERMETTAIT DEJA
--
--    intervention.rdv_id est NULLABLE depuis le socle, sans aucun CHECK l exigeant, et
--    l entite le documente : « une intervention peut naitre d une entree directe au
--    garage. En V1, ce constructeur n en cree que depuis un RDV. » La seconde origine
--    etait donc PREVUE par conception ; il ne s agit pas d affaiblir un invariant mais
--    d exercer une possibilite laissee ouverte.
--
--    Ce qui manquait, c est le LIEN : rien ne rattachait une intervention a la commande
--    qui l a payee. F30 ne pouvait donc pas repondre « ce service a-t-il ete pleinement
--    execute ? », et la renonciation VI.53 restait sans effet (divergence 11).
--
-- 2) POURQUOI num_nonnulls(...) <= 1 ET NON = 1
--
--    Une intervention a AU PLUS une origine, jamais deux : elle vient d un rendez-vous
--    honore, ou d une commande de services payee. Les deux a la fois n aurait aucun
--    sens — on ne saurait pas quel document facture quoi.
--
--    Mais ZERO origine reste admis, et volontairement : c est l « entree directe au
--    garage » que le socle prevoit deja (un client qui se presente sans rendez-vous ni
--    commande en ligne). Exiger = 1 interdirait ce cas et casserait une possibilite
--    documentee, pour un gain nul.
--
-- 3) ON DELETE SET NULL, COMME POUR LE RDV
--
--    Meme raisonnement que fk_intervention_rdv : l intervention est le dossier
--    d atelier, elle survit a la disparition de ce qui l a declenchee. Un RESTRICT
--    empecherait toute suppression de commande ; un CASCADE detruirait un dossier de
--    travail reellement effectue. SET NULL conserve le dossier en perdant le lien.
--
-- 4) AUCUNE TABLE AJOUTEE
--
--    33 tables metier avant, 33 apres. Une colonne, une FK, un CHECK, un index partiel.
-- =====================================================================================

ALTER TABLE intervention
    ADD COLUMN commande_id BIGINT;

ALTER TABLE intervention
    ADD CONSTRAINT fk_intervention_commande
        FOREIGN KEY (commande_id) REFERENCES commande (id)
            ON UPDATE CASCADE ON DELETE SET NULL;

ALTER TABLE intervention
    ADD CONSTRAINT ck_intervention_origine_unique
        CHECK (num_nonnulls(rdv_id, commande_id) <= 1);

-- Meme patron que les autres index partiels du schema : seules les interventions
-- issues d une commande sont indexees, celles issues d un RDV n alourdissent rien.
CREATE INDEX ix_intervention_commande ON intervention (commande_id)
    WHERE commande_id IS NOT NULL;

COMMENT ON COLUMN intervention.commande_id IS
    'Commande de services payee dont cette intervention execute une ligne (F12-b). '
        'Exclusif avec rdv_id ; les deux nuls = entree directe au garage. Sert a F30 '
        'pour savoir si un service sous renonciation VI.53 est pleinement execute.';
