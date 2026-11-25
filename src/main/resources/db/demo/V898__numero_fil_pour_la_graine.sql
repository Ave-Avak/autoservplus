-- =====================================================================================
-- Numéro des fils de discussion de la graine de démonstration, et d'elle seule (Q-H-28).
--
-- V900, écrite avant V71 et déjà appliquée, donc intouchable, insère un fil sans numéro.
-- Sur une base demo neuve, elle passe après V71 et échouerait sur son NOT NULL. Ce
-- trigger attribue le numéro quand l'insertion n'en fournit pas, par le compteur annuel
-- que l'application emploie (compteur_fil), année prise à la création du fil au fuseau
-- de l'atelier. Sur une base où V900 est déjà jouée, V71 a numéroté le fil, et ce
-- trigger n'agit plus.
--
-- Même procédé que V899 : il vit dans db/demo, chargé sous le seul profil demo, et
-- jamais dans le schéma de production, où un fil ouvert sans numéro doit échouer sur le
-- NOT NULL. L'application le fournit toujours (MessagerieService.ouvrir).
-- =====================================================================================

CREATE FUNCTION fn_conversation_numero_graine() RETURNS TRIGGER AS $$
DECLARE
    v_exercice SMALLINT;
    v_rang     INTEGER;
BEGIN
    SELECT EXTRACT(YEAR FROM NEW.created_at AT TIME ZONE p.fuseau_horaire)
    INTO v_exercice
    FROM parametre_atelier p
    WHERE p.id = 1;

    INSERT INTO compteur_fil (exercice, dernier_numero) VALUES (v_exercice, 1)
    ON CONFLICT (exercice) DO UPDATE SET dernier_numero = compteur_fil.dernier_numero + 1
    RETURNING dernier_numero INTO v_rang;

    NEW.numero := 'FIL-' || v_exercice || '-' || lpad(v_rang::TEXT, greatest(4, length(v_rang::TEXT)), '0');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_conversation_numero_graine
    BEFORE INSERT ON conversation
    FOR EACH ROW
    WHEN (NEW.numero IS NULL)
    EXECUTE FUNCTION fn_conversation_numero_graine();

COMMENT ON TRIGGER tg_conversation_numero_graine ON conversation IS
    'Profil demo seulement : numérote les fils insérés sans numéro par la graine V900.';
