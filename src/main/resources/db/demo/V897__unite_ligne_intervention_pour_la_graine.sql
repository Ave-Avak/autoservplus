-- =====================================================================================
-- Unité des lignes d'intervention de la graine de démonstration, et d'elle seule (Q-H-40).
--
-- V900, écrite avant V73 et déjà appliquée, donc intouchable, insère des lignes
-- d'intervention sans unité. Sur une base demo neuve, elle passe après V73 et échouerait
-- sur son NOT NULL. Ce déclencheur pose l'unité quand l'insertion n'en fournit pas : PIECE
-- pour une ligne qui porte une pièce, FORFAIT sinon, la graine ne portant aucune prestation
-- à l'heure. Sur une base où V900 est déjà jouée, V73 a repris ses lignes, et ce
-- déclencheur n'agit plus.
--
-- Même procédé que V898 et V899 : il vit dans db/demo, chargé sous le seul profil demo, et
-- jamais dans le schéma de production, où une ligne sans unité doit échouer sur le NOT
-- NULL. L'application la fournit toujours (LigneIntervention).
-- =====================================================================================

CREATE FUNCTION fn_ligne_intervention_unite_graine() RETURNS TRIGGER AS $$
BEGIN
    NEW.unite := CASE WHEN NEW.piece_id IS NOT NULL THEN 'PIECE' ELSE 'FORFAIT' END;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_ligne_intervention_unite_graine
    BEFORE INSERT ON ligne_intervention
    FOR EACH ROW
    WHEN (NEW.unite IS NULL)
    EXECUTE FUNCTION fn_ligne_intervention_unite_graine();

COMMENT ON TRIGGER tg_ligne_intervention_unite_graine ON ligne_intervention IS
    'Profil demo seulement : pose l''unité des lignes insérées sans unité par la graine V900.';
