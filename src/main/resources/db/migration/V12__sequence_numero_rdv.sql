-- Numerotation fonctionnelle des rendez-vous, format RDV-AAAA-NNNN.
-- Une sequence garantit l atomicite : deux demandes simultanees ne peuvent pas
-- obtenir le meme numero, ce qu un COUNT(*) + 1 ne garantit pas.
-- La sequence n est pas remise a zero chaque annee : le numero reste unique et
-- lisible, et l annee indique simplement la date de la demande.
CREATE SEQUENCE rdv_numero_seq START WITH 1 INCREMENT BY 1;