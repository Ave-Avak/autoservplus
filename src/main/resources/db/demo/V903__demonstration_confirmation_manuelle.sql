-- Démonstration en confirmation manuelle.
--
-- V48 active la confirmation automatique sur toute base. La démonstration garde le mode
-- manuel pour montrer une demande en attente au back-office, avec son échéance de
-- réponse et la relance au garage. Les demandes de V900 n'ont pas d'échéance : elles
-- restent en attente et n'expirent pas. Le dump publié est à régénérer pour porter ce
-- réglage.
UPDATE parametre_atelier SET confirmation_automatique = FALSE;
