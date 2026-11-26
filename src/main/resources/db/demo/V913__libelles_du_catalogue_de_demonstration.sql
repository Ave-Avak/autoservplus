-- Catégorie de prestations « Pneumatique » renommée « Pneus » dans les trois langues, le
-- mot que le client cherche. Graine seule : une base réelle nomme ses catégories au
-- back-office, la donnée de référence de V10 et V67 n'est pas touchée.

UPDATE categorie
SET libelle = 'Pneus', libelle_nl = 'Banden', libelle_en = 'Tyres', updated_by = 'demo'
WHERE code = 'PNEUMATIQUE';
