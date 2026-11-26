-- Les six prestations de l'accueil de démonstration, celles qu'un client vient le plus
-- souvent faire chiffrer. Rien sur une base dont le catalogue n'est pas celui de la graine.

UPDATE service SET ordre_vitrine = CASE code
        WHEN 'VIDANGE_STD' THEN 1
        WHEN 'REVISION_ANNUELLE' THEN 2
        WHEN 'PLAQUETTES_AV' THEN 3
        WHEN 'RECHARGE_CLIM' THEN 4
        WHEN 'MONTAGE_PNEU' THEN 5
        WHEN 'DIAG_ELEC' THEN 6
    END
WHERE code IN ('VIDANGE_STD', 'REVISION_ANNUELLE', 'PLAQUETTES_AV', 'RECHARGE_CLIM', 'MONTAGE_PNEU', 'DIAG_ELEC');
