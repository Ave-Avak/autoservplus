-- Articles de la graine rangés dans la catégorie où le client les cherche : le joint et
-- le bouchon de vidange ne sont pas des filtres, les chaînes à neige vont avec l'hiver.

UPDATE piece
SET categorie_id = (SELECT id FROM categorie WHERE code = 'P_MOTEUR'), updated_by = 'demo'
WHERE reference_fabricant IN ('BB-P005', 'BB-P006');

UPDATE piece
SET categorie_id = (SELECT id FROM categorie WHERE code = 'A_HIVER'), updated_by = 'demo'
WHERE reference_fabricant = 'BB-A025';
