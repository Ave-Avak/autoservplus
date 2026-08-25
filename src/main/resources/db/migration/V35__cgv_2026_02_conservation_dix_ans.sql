-- =====================================================================================
-- Publication de CGV-2026-02 : article 9, conservation portee de sept a dix ans (F24).
--
-- 1) LE FAIT JURIDIQUE
--
--    L article 9 des conditions generales annoncait que les factures sont conservees
--    SEPT ans. La loi du 20 novembre 2022 (M.B. 30 novembre 2022) a porte ce delai a
--    DIX ans pour les taxes devenues exigibles a partir du 1er janvier 2023 ; il court
--    a compter du 1er janvier de l annee qui suit celle de la facture. L article 60 du
--    Code de la TVA reste la reference : c est son contenu qui a change, pas sa
--    numerotation.
--
-- 2) POURQUOI UNE NOUVELLE VERSION ET NON UNE CORRECTION
--
--    C est le cas d usage pour lequel F24 a ete construit, exerce pour la premiere
--    fois. Retoucher CGV-2026-01 aurait reecrit un texte que des membres ont accepte :
--    leur preuve continuerait de porter « CGV-2026-01 » et designerait desormais une
--    redaction differente de celle qu ils ont eue sous les yeux. C est exactement le
--    defaut d avant F24 — une preuve qui dit QU ON a accepte sans pouvoir dire QUOI —
--    et la garde de non-derive (TexteDocumentGeleIT) le refuse en cassant la build.
--
--    Le texte de 2026-01 reste donc intact, octet pour octet, avec son empreinte. Ce
--    qui change est son etat : actif = false le retire du jeu resolvable SANS le
--    supprimer. Une preuve qui le designe continue de le resoudre, et
--    /documents/cgv/CGV-2026-01 continue d en servir le texte dans les trois langues.
--    C est la difference entre archiver et effacer.
--
-- 3) CE QUI CHANGE EXACTEMENT
--
--    Une seule clause, un seul mot par langue : « sept ans » devient « dix ans »,
--    « zeven jaar » devient « tien jaar », « seven years » devient « ten years ». Les
--    vingt-neuf autres clauses sont reprises a l identique — verifie ligne a ligne
--    contre V33 avant redaction de cette migration : trente lignes de part et d autre,
--    une seule differente dans chacune des trois langues.
--
--    Le contenu et l empreinte sont produits par la METHODE DE V33, reproduite sans
--    ecart : cles de TypeDocumentVersionne.CGV dans l ordre, resolues par le meme
--    MessageSource que les gabarits — donc MessageFormat pour la seule cle a arguments
--    (legal.cgv.art7.corps, qui recoit le delai de retractation) et texte brut pour
--    toutes les autres —, jointes par un saut de ligne, empreinte SHA-256 hexadecimale
--    du resultat encode en UTF-8.
--
--    Assemblage par concat_ws(chr(10), ...) et non litteral multiligne, pour la meme
--    raison qu en V33 : un litteral embarquerait les fins de ligne DU FICHIER, et un
--    checkout en CRLF gelerait un texte different d un checkout en LF — deux empreintes
--    pour un meme document.
--
-- 4) V35 ET NON V34
--
--    V34 est prise : elle numerote le jeu de demonstration de db/demo. Deux migrations
--    de meme version se disputeraient le rang sous le profil demo, ou les deux
--    emplacements sont charges ensemble.
--
-- 5) AUCUNE TOUCHE AU SCHEMA
--
--    34 tables metier avant, 34 apres. Cette migration n insere et ne met a jour que
--    des lignes.
--
-- 6) CE QUI N EST PAS FAIT ICI, ET QUI RESTE UNE DETTE
--
--    Aucun re-consentement n est declenche. Un membre dont la preuve porte CGV-2026-01
--    n est pas sollicite ; sa prochaine commande enregistrera CGV-2026-02, parce que
--    CommandeService fige la version en vigueur au moment ou il accepte. La table rend
--    la comparaison possible — c est une requete — mais aucun flux ne la fait. Point
--    inscrit au registre de dette, deja ouvert avant cette migration.
-- =====================================================================================

INSERT INTO version_document
    (type_document, version, langue, date_effet, contenu, empreinte, actif, created_by, updated_by)
VALUES ('CGV', 'CGV-2026-02', 'fr',
        TIMESTAMPTZ '2026-08-25 00:00:00+02',
        concat_ws(chr(10),
            'Conditions générales de vente',
            'Les présentes conditions régissent la vente de pièces et de prestations d''atelier par l''intermédiaire de la plateforme AutoServ+.',
            'Article 1 — Vendeur',
            'Le vendeur est le garage identifié ci-dessous, inscrit à la Banque-Carrefour des Entreprises et assujetti à la taxe sur la valeur ajoutée.',
            'Article 2 — Champ d''application et acceptation',
            'Toute commande passée sur la plateforme suppose l''acceptation préalable et expresse des présentes conditions. Cette acceptation est recueillie par une case à cocher au récapitulatif de commande ; elle est horodatée et conservée avec la version du document acceptée et l''adresse IP utilisée.',
            'Article 3 — Prix et taxe sur la valeur ajoutée',
            'Les prix sont indiqués en euros, hors taxe et taxe comprise. Les taux appliqués sont les taux belges de 0, 6, 12 et 21 %. Le prix, le libellé et le taux de taxe d''un article sont figés au moment où il est ajouté au panier : une modification ultérieure du catalogue reste sans effet sur un panier déjà constitué.',
            'Article 4 — Commande',
            'La commande se forme en trois temps : constitution du panier, récapitulatif reprenant le détail des lignes et des montants, puis validation. Le bouton de validation indique expressément que la commande oblige au paiement. Un panier ne peut contenir que des pièces ou que des prestations, jamais les deux à la fois.',
            'Article 5 — Paiement',
            'Le paiement s''effectue en ligne auprès d''un prestataire de services de paiement. Aucune donnée de carte n''est collectée ni conservée par le garage. La commande n''est réputée payée qu''après confirmation du statut par le prestataire.',
            'Article 6 — Retrait et exécution',
            'Les pièces commandées sont retirées au garage ; aucune expédition n''est proposée. L''achat en ligne d''une prestation ne réserve pas de créneau : le garage ouvre le dossier d''atelier et convient de la date avec le client.',
            'Les biens commandés (pièces, accessoires) sont retirés sur place, à l''adresse du garage indiquée à l''article 1 ; aucune expédition n''est proposée. Les prestations de service sont exécutées dans l''atelier du garage, à la date convenue lors de la réservation ou, à défaut, dans un délai raisonnable communiqué au client. La date d''exécution prévue est rappelée dans le récapitulatif de commande et dans l''e-mail de confirmation, qui constitue le support durable de l''information précontractuelle (article VI.45 §7 du Code de droit économique).',
            'Article 7 — Droit de rétractation',
            'Le consommateur dispose d''un délai de 14 jours pour se rétracter, sans avoir à motiver sa décision. Ce délai court à compter de la conclusion de la commande. La demande se dépose depuis l''espace membre ; le garage l''examine et notifie sa décision. En cas d''acceptation, une note de crédit est émise et le remboursement intervient par le même moyen de paiement que celui employé lors de l''achat.',
            'Le consommateur peut exercer son droit de rétractation sans avoir à motiver sa décision et sans pénalité (article VI.47 du Code de droit économique). Pour l''exercer, il notifie sa décision au garage au moyen d''une déclaration dénuée d''ambiguïté (courrier postal ou courrier électronique aux coordonnées de l''article 1), ou en utilisant le formulaire type de rétractation mis à sa disposition, téléchargeable et accessible par un lien permanent dans son espace membre. Le garage rembourse la totalité des sommes versées dans les quatorze jours suivant la réception de la rétractation, en utilisant le même moyen de paiement que celui employé lors de la commande, sauf accord exprès du consommateur pour un autre moyen. Ce droit ne s''applique pas dans les cas d''exclusion prévus à l''article VI.53 du Code de droit économique, notamment celui décrit à l''article 8 des présentes (prestations pleinement exécutées avec accord préalable exprès).',
            'Article 8 — Prestations pleinement exécutées',
            'Lorsque le client demande l''exécution d''une prestation avant la fin du délai de rétractation, il lui est proposé de reconnaître expressément qu''il perdra ce droit une fois la prestation pleinement exécutée. Cette renonciation est facultative, n''est jamais pré-cochée, et sa preuve est conservée. Tant que la prestation n''est pas pleinement exécutée, le droit de rétractation subsiste.',
            'Article 9 — Facture',
            'Une facture est émise pour toute commande payée et mise à disposition au format PDF dans l''espace membre. Les factures sont numérotées de manière continue et sans interruption, par exercice. Elles sont conservées dix ans, conformément à la législation relative à la taxe sur la valeur ajoutée.',
            'Article 10 — Garantie légale de conformité',
            'Pour les biens vendus au consommateur, la garantie légale de conformité de deux ans est applicable (articles 1649bis et suivants de l''ancien Code civil, désormais intégrés au livre 5 du nouveau Code civil). Cette garantie est due par le vendeur, c''est-à-dire le garage. Pour les prestations de service (entretien, réparation, diagnostic, etc.), le garage est tenu d''une obligation de conformité de droit commun : la prestation doit être conforme à ce qui a été convenu ; cette obligation n''est pas assortie d''une durée fixe mais reste encadrée par les règles de la prescription. La présente garantie légale s''applique indépendamment de toute garantie commerciale éventuelle.',
            'Article 11 — Responsabilité',
            'Le garage est responsable de la bonne exécution des prestations vendues, dans les conditions du droit commun (livre 6 du Code civil, entré en vigueur le 1er janvier 2025) et des présentes conditions. Les informations publiées sur le site sont fournies à titre indicatif ; le garage s''efforce de les tenir exactes et à jour sans garantir leur exhaustivité ou l''absence d''erreur. La responsabilité du garage ne peut en aucun cas être exclue ou limitée en cas de faute lourde, de dol, d''atteinte à la vie ou à l''intégrité physique, ni pour les obligations légales impératives — notamment la garantie légale de conformité et la sécurité des produits et services. Le garage n''assume aucune responsabilité quant au contenu des sites tiers accessibles par des liens, sur lesquels il n''exerce aucun contrôle.',
            'Article 12 — Données personnelles',
            'Le traitement des données personnelles est décrit dans la politique de confidentialité, accessible depuis chaque page du site.',
            'Article 13 — Droit applicable et litiges',
            'Les présentes conditions sont régies par le droit belge. En cas de litige, les parties rechercheront une solution amiable avant toute action judiciaire. Le consommateur est informé de l''existence du Service de médiation pour le consommateur (Boulevard du Roi Albert II 8, 1000 Bruxelles — mediationconsommateur.be) et, pour les achats en ligne, de la plateforme européenne de règlement en ligne des litiges (ec.europa.eu/consumers/odr). À défaut de règlement amiable, les cours et tribunaux belges sont compétents ; le consommateur conserve le bénéfice des règles protectrices lui permettant, le cas échéant, de saisir la juridiction de son lieu de domicile.'),
        '5f850ad3f585526cbcf7a00dd96a4be439d0e588c34eb6ca837537f0d6e0163c', TRUE, 'systeme', 'systeme');

INSERT INTO version_document
    (type_document, version, langue, date_effet, contenu, empreinte, actif, created_by, updated_by)
VALUES ('CGV', 'CGV-2026-02', 'nl',
        TIMESTAMPTZ '2026-08-25 00:00:00+02',
        concat_ws(chr(10),
            'Algemene verkoopvoorwaarden',
            'Deze voorwaarden zijn van toepassing op de verkoop van onderdelen en werkplaatsdiensten via het platform AutoServ+.',
            'Artikel 1 — Verkoper',
            'De verkoper is de hieronder vermelde garage, ingeschreven bij de Kruispuntbank van Ondernemingen en btw-plichtig.',
            'Artikel 2 — Toepassingsgebied en aanvaarding',
            'Elke bestelling op het platform veronderstelt de voorafgaande en uitdrukkelijke aanvaarding van deze voorwaarden. Die aanvaarding wordt verzameld via een aankruisvakje op het besteloverzicht; zij wordt met tijdstempel bewaard, samen met de aanvaarde versie van het document en het gebruikte IP-adres.',
            'Artikel 3 — Prijzen en belasting over de toegevoegde waarde',
            'De prijzen worden vermeld in euro, exclusief en inclusief belasting. De toegepaste tarieven zijn de Belgische tarieven van 0, 6, 12 en 21 %. De prijs, de omschrijving en het belastingtarief van een artikel worden vastgelegd op het ogenblik waarop het aan het winkelmandje wordt toegevoegd: een latere wijziging van de catalogus heeft geen invloed op een reeds samengesteld mandje.',
            'Artikel 4 — Bestelling',
            'De bestelling komt in drie stappen tot stand: samenstelling van het mandje, overzicht met de details van de lijnen en de bedragen, en vervolgens bevestiging. De bevestigingsknop vermeldt uitdrukkelijk dat de bestelling een betalingsverplichting inhoudt. Een mandje kan uitsluitend onderdelen of uitsluitend diensten bevatten, nooit beide tegelijk.',
            'Artikel 5 — Betaling',
            'De betaling verloopt online via een betaaldienstaanbieder. Er worden door de garage geen kaartgegevens verzameld of bewaard. De bestelling geldt pas als betaald na bevestiging van de status door de dienstverlener.',
            'Artikel 6 — Afhaling en uitvoering',
            'Bestelde onderdelen worden in de garage afgehaald; er wordt geen verzending aangeboden. De online aankoop van een dienst reserveert geen tijdslot: de garage opent het werkplaatsdossier en spreekt de datum af met de klant.',
            'De bestelde goederen (onderdelen, accessoires) worden ter plaatse afgehaald, op het adres van de garage vermeld in artikel 1; er wordt geen verzending aangeboden. De diensten worden uitgevoerd in de werkplaats van de garage, op de bij de reservatie afgesproken datum of, bij gebreke daarvan, binnen een redelijke termijn die aan de klant wordt meegedeeld. De voorziene uitvoeringsdatum wordt herhaald in het besteloverzicht en in de bevestigingsmail, die de duurzame gegevensdrager van de precontractuele informatie vormt (artikel VI.45 §7 van het Wetboek van economisch recht).',
            'Artikel 7 — Herroepingsrecht',
            'De consument beschikt over een termijn van 14 dagen om zich te herroepen, zonder opgave van reden. Deze termijn loopt vanaf het sluiten van de bestelling. De aanvraag wordt ingediend vanuit de ledenruimte; de garage onderzoekt ze en deelt haar beslissing mee. Bij aanvaarding wordt een creditnota opgesteld en gebeurt de terugbetaling via hetzelfde betaalmiddel als bij de aankoop.',
            'De consument kan zijn herroepingsrecht uitoefenen zonder opgave van reden en zonder boete (artikel VI.47 van het Wetboek van economisch recht). Om het uit te oefenen, deelt hij zijn beslissing aan de garage mee door middel van een ondubbelzinnige verklaring (brief of e-mail naar de contactgegevens van artikel 1), of met het modelformulier voor herroeping dat hem ter beschikking wordt gesteld, downloadbaar en toegankelijk via een permanente link in zijn ledenruimte. De garage betaalt alle gestorte bedragen terug binnen veertien dagen na ontvangst van de herroeping, met hetzelfde betaalmiddel als dat van de bestelling, tenzij de consument uitdrukkelijk met een ander middel instemt. Dit recht geldt niet in de uitsluitingsgevallen bepaald in artikel VI.53 van het Wetboek van economisch recht, met name het geval beschreven in artikel 8 van deze voorwaarden (volledig uitgevoerde diensten met voorafgaande uitdrukkelijke instemming).',
            'Artikel 8 — Volledig uitgevoerde diensten',
            'Wanneer de klant vraagt dat een dienst wordt uitgevoerd vóór het verstrijken van de herroepingstermijn, wordt hem voorgesteld uitdrukkelijk te erkennen dat hij dat recht verliest zodra de dienst volledig is uitgevoerd. Die afstand is facultatief, wordt nooit vooraf aangevinkt, en het bewijs ervan wordt bewaard. Zolang de dienst niet volledig is uitgevoerd, blijft het herroepingsrecht bestaan.',
            'Artikel 9 — Factuur',
            'Voor elke betaalde bestelling wordt een factuur opgesteld en in pdf-formaat ter beschikking gesteld in de ledenruimte. De facturen worden per boekjaar doorlopend en zonder onderbreking genummerd. Zij worden tien jaar bewaard, overeenkomstig de btw-wetgeving.',
            'Artikel 10 — Wettelijke conformiteitsgarantie',
            'Voor goederen die aan de consument worden verkocht, geldt de wettelijke conformiteitsgarantie van twee jaar (artikelen 1649bis en volgende van het oud Burgerlijk Wetboek, thans opgenomen in boek 5 van het nieuw Burgerlijk Wetboek). Deze garantie is verschuldigd door de verkoper, dat wil zeggen de garage. Voor de diensten (onderhoud, herstelling, diagnose, enz.) rust op de garage een gemeenrechtelijke conformiteitsverplichting: de dienst moet overeenstemmen met wat is overeengekomen; aan die verplichting is geen vaste duur verbonden, maar zij blijft omkaderd door de verjaringsregels. Deze wettelijke garantie geldt ongeacht enige commerciële waarborg.',
            'Artikel 11 — Aansprakelijkheid',
            'De garage is aansprakelijk voor de goede uitvoering van de verkochte prestaties, onder de voorwaarden van het gemeen recht (boek 6 van het Burgerlijk Wetboek, in werking getreden op 1 januari 2025) en van deze voorwaarden. De op de site gepubliceerde informatie wordt ter indicatie verstrekt; de garage streeft ernaar ze juist en actueel te houden zonder de volledigheid ervan of de afwezigheid van fouten te waarborgen. De aansprakelijkheid van de garage kan in geen geval worden uitgesloten of beperkt in geval van zware fout, bedrog, aantasting van het leven of de lichamelijke integriteit, noch voor de dwingende wettelijke verplichtingen — met name de wettelijke conformiteitsgarantie en de veiligheid van producten en diensten. De garage draagt geen enkele aansprakelijkheid voor de inhoud van sites van derden die via links toegankelijk zijn en waarover zij geen enkele controle uitoefent.',
            'Artikel 12 — Persoonsgegevens',
            'De verwerking van persoonsgegevens wordt beschreven in het privacybeleid, dat vanaf elke pagina van de site toegankelijk is.',
            'Artikel 13 — Toepasselijk recht en geschillen',
            'Deze voorwaarden worden beheerst door het Belgisch recht. Bij een geschil zoeken de partijen een minnelijke oplossing vóór elke gerechtelijke stap. De consument wordt ingelicht over het bestaan van de Consumentenombudsdienst (Koning Albert II-laan 8, 1000 Brussel — consumentenombudsdienst.be) en, voor onlineaankopen, over het Europese platform voor onlinegeschillenbeslechting (ec.europa.eu/consumers/odr). Bij gebrek aan een minnelijke regeling zijn de Belgische hoven en rechtbanken bevoegd; de consument behoudt het voordeel van de beschermende regels die hem in voorkomend geval toelaten de rechtbank van zijn woonplaats te vatten.'),
        '70a5805d6834e303916ca12bb3f4281319b37e067946b124d3892dfb00413313', TRUE, 'systeme', 'systeme');

INSERT INTO version_document
    (type_document, version, langue, date_effet, contenu, empreinte, actif, created_by, updated_by)
VALUES ('CGV', 'CGV-2026-02', 'en',
        TIMESTAMPTZ '2026-08-25 00:00:00+02',
        concat_ws(chr(10),
            'Terms and conditions of sale',
            'These terms govern the sale of parts and workshop services through the AutoServ+ platform.',
            'Article 1 — Seller',
            'The seller is the garage identified below, registered with the Crossroads Bank for Enterprises and liable for value added tax.',
            'Article 2 — Scope and acceptance',
            'Every order placed on the platform requires the prior and express acceptance of these terms. That acceptance is collected through a checkbox on the order summary; it is time-stamped and stored together with the version of the document accepted and the IP address used.',
            'Article 3 — Prices and value added tax',
            'Prices are shown in euro, excluding and including tax. The rates applied are the Belgian rates of 0, 6, 12 and 21 %. The price, description and tax rate of an item are frozen at the moment it is added to the basket: a later change to the catalogue has no effect on a basket already assembled.',
            'Article 4 — Order',
            'An order is formed in three steps: building the basket, a summary showing each line and each amount, then confirmation. The confirmation button states expressly that the order carries an obligation to pay. A basket may contain either parts or services, never both at once.',
            'Article 5 — Payment',
            'Payment is made online through a payment service provider. No card data is collected or stored by the garage. An order is treated as paid only after the provider has confirmed its status.',
            'Article 6 — Collection and performance',
            'Ordered parts are collected at the garage; no shipping is offered. Buying a service online does not book a time slot: the garage opens the workshop file and agrees a date with the customer.',
            'Goods ordered (parts, accessories) are collected on site, at the garage address given in Article 1; no shipping is offered. Services are performed in the garage workshop, on the date agreed when booking or, failing that, within a reasonable period notified to the customer. The expected performance date is repeated in the order summary and in the confirmation e-mail, which constitutes the durable medium for the pre-contractual information (Article VI.45 §7 of the Code of Economic Law).',
            'Article 7 — Right of withdrawal',
            'The consumer has 14 days to withdraw, without having to give any reason. That period runs from the conclusion of the order. The request is submitted from the member area; the garage examines it and notifies its decision. If accepted, a credit note is issued and the refund is made using the same means of payment as the purchase.',
            'The consumer may exercise the right of withdrawal without having to give any reason and without penalty (Article VI.47 of the Code of Economic Law). To do so, they notify the garage of their decision by an unambiguous statement (letter or e-mail to the contact details in Article 1), or by using the model withdrawal form made available to them, downloadable and reachable through a permanent link in their member area. The garage refunds all sums paid within fourteen days of receiving the withdrawal, using the same means of payment as the one used for the order, unless the consumer expressly agrees to another means. This right does not apply in the cases of exclusion set out in Article VI.53 of the Code of Economic Law, in particular the one described in Article 8 of these terms (services fully performed with prior express agreement).',
            'Article 8 — Fully performed services',
            'Where the customer asks for a service to be performed before the withdrawal period ends, they are offered the option of expressly acknowledging that they will lose that right once the service has been fully performed. This waiver is optional, is never pre-ticked, and evidence of it is retained. As long as the service has not been fully performed, the right of withdrawal remains.',
            'Article 9 — Invoice',
            'An invoice is issued for every paid order and made available as a PDF in the member area. Invoices are numbered continuously and without gaps, per financial year. They are kept for ten years, in accordance with value added tax legislation.',
            'Article 10 — Legal guarantee of conformity',
            'For goods sold to a consumer, the two-year legal guarantee of conformity applies (Articles 1649bis et seq. of the former Civil Code, now incorporated into Book 5 of the new Civil Code). This guarantee is owed by the seller, that is to say the garage. For services (servicing, repair, diagnosis, etc.), the garage is bound by an ordinary-law obligation of conformity: the service must match what was agreed; that obligation carries no fixed duration but remains framed by the rules on limitation periods. This legal guarantee applies irrespective of any commercial warranty.',
            'Article 11 — Liability',
            'The garage is liable for the proper performance of the services sold, under the conditions of the general law (Book 6 of the Civil Code, in force since 1 January 2025) and of these terms. The information published on the site is provided for guidance; the garage endeavours to keep it accurate and up to date without guaranteeing that it is exhaustive or free of error. The liability of the garage may in no case be excluded or limited in the event of gross negligence, fraud, harm to life or physical integrity, nor for mandatory legal obligations — in particular the legal guarantee of conformity and the safety of products and services. The garage assumes no liability for the content of third-party sites reachable through links, over which it exercises no control.',
            'Article 12 — Personal data',
            'The processing of personal data is described in the privacy policy, which is reachable from every page of the site.',
            'Article 13 — Applicable law and disputes',
            'These terms are governed by Belgian law. In the event of a dispute, the parties will seek an amicable solution before any legal action. The consumer is informed of the existence of the Consumer Mediation Service (Boulevard du Roi Albert II 8, 1000 Brussels — mediationconsommateur.be) and, for online purchases, of the European online dispute resolution platform (ec.europa.eu/consumers/odr). Failing an amicable settlement, the Belgian courts have jurisdiction; the consumer retains the benefit of the protective rules allowing them, where applicable, to bring proceedings before the court of their place of domicile.'),
        'a91a1c38867fd7ac316d04da60cc3c5474ceec17a00e7d40d3a5088ccbbb064c', TRUE, 'systeme', 'systeme');

-- CGV-2026-01 quitte le jeu resolvable sans quitter la table : les lignes, leur
-- contenu et leur empreinte restent inchanges. Un UPDATE de actif seulement — toute
-- retouche du contenu invaliderait les preuves qui le designent.
UPDATE version_document
SET actif      = FALSE,
    updated_at = now(),
    updated_by = 'systeme'
WHERE type_document = 'CGV'
  AND version = 'CGV-2026-01';
