-- =====================================================================================
-- Complément de l'instantané client pour la graine de démonstration, et pour elle seule.
--
-- V900, écrite avant V61 et déjà appliquée, donc intouchable, insère une facture sans
-- les colonnes de l'instantané (client_*, langue_document). Sur une base demo neuve,
-- elle passe après V61 et échouerait sur leur NOT NULL. Ce trigger recopie le compte
-- quand l'insertion ne fournit pas l'instantané, ce qui revient à le figer à
-- l'émission.
--
-- Il vit dans db/demo, chargé sous le seul profil demo, et jamais dans le schéma de
-- production : là, une émission qui oublierait l'instantané doit échouer sur le NOT NULL
-- au lieu d'être complétée en silence depuis le compte vif. L'application le fournit
-- toujours (Facture.pourCommande).
-- =====================================================================================

CREATE FUNCTION fn_facture_instantane_graine() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.client_prenom IS NULL THEN
        SELECT u.prenom, u.nom, u.rue, u.numero_rue, u.code_postal, u.localite, u.pays, u.langue
        INTO NEW.client_prenom, NEW.client_nom, NEW.client_rue, NEW.client_numero_rue,
             NEW.client_code_postal, NEW.client_localite, NEW.client_pays, NEW.langue_document
        FROM utilisateur u
        WHERE u.id = NEW.membre_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_facture_instantane_graine
    BEFORE INSERT ON facture
    FOR EACH ROW EXECUTE FUNCTION fn_facture_instantane_graine();

COMMENT ON TRIGGER tg_facture_instantane_graine ON facture IS
    'Profil demo seulement : complète l''instantané client des factures de la graine V900.';
