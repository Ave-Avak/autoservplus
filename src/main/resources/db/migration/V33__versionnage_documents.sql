-- =====================================================================================
-- Versionnage des documents engageants (F24).
--
-- 1) CE QUI MANQUAIT, ET POURQUOI CE N EST PAS UN CONFORT
--
--    consentement.version_acceptee stocke depuis le socle un identifiant de version
--    — « CGV-2026-01 » — alimente par une CONSTANTE Java. Aucune table ne reliait cet
--    identifiant au TEXTE qu il designe. Une preuve d acceptation pointait donc un
--    numero sans texte reconstituable : on pouvait affirmer que le membre avait
--    accepte quelque chose, pas montrer QUOI.
--
--    Un consentement ne vaut que pour le texte effectivement presente au moment ou il
--    est donne. Tant que le texte vit dans des fichiers de messages modifiables sans
--    trace, changer une clause n a aucune consequence visible : les acceptations
--    anterieures continuent de porter le meme numero de version et paraissent couvrir
--    la nouvelle redaction. C est exactement l inverse de ce que le consentement doit
--    prouver.
--
-- 2) POURQUOI UNE LIGNE PAR LANGUE
--
--    Le texte engageant n existe pas en un seul exemplaire : il est servi en FR, NL et
--    EN (F6), et le membre neerlandophone a accepte le texte NEERLANDAIS. Geler une
--    seule langue rendrait sa preuve inexploitable — on lui opposerait un texte qu il
--    n a jamais lu. La cle d unicite est donc (type_document, version, langue) : une
--    version est un jeu de trois textes, publies ensemble et portant le meme numero.
--
-- 3) POURQUOI UNE EMPREINTE EN PLUS DU CONTENU
--
--    Le contenu seul se gele mais ne se surveille pas. L empreinte SHA-256 rend la
--    question « le texte affiche aujourd hui est-il encore celui de la version en
--    vigueur ? » decidable par une comparaison, et c est un test d integration qui la
--    pose a chaque build : modifier une clause sans publier de nouvelle version CASSE
--    LA BUILD. C est la seule facon d empecher la derive silencieuse — celle ou
--    personne ne ment, mais ou plus rien ne correspond.
--
--    Meme raisonnement que fn_tables_traces_audit() en V28 : la valeur de la garantie
--    ne vient pas du fait qu elle soit ecrite, mais du fait qu un test echoue quand
--    elle cesse d etre vraie.
--
-- 4) POURQUOI CES TROIS TYPES ET PAS D AUTRES
--
--    Le CHECK enumere exactement les documents pour lesquels une ligne consentement
--    est REELLEMENT ecrite aujourd hui :
--      - CGV                       : CommandeService, a la conversion du panier ;
--      - COOKIES                   : PreferencesCookiesService, pour les deux
--                                    finalites optionnelles (F25) — le document est
--                                    la politique cookies, les finalites en sont les
--                                    objets consentis ;
--      - RENONCIATION_RETRACTATION : CommandeService, VI.53 (F12).
--
--    POLITIQUE_CONFIDENTIALITE et NEWSLETTER figurent au CHECK de consentement mais
--    ne sont jamais ecrits : leur versionner un texte creerait une version qu aucune
--    preuve ne resout. Les mentions legales ne sont consenties par personne. Versionner
--    un document qu on ne fait pas accepter n apporte rien et laisse croire l inverse.
--
-- 5) CE QUI N EST PAS GELE, ET POURQUOI
--
--    L identite du garage (raison sociale, adresse, BCE, TVA, telephone) reste lue en
--    configuration a chaque rendu, JAMAIS recopiee ici. Un demenagement du garage ne
--    doit pas invalider les consentements deja recueillis : ce qui engage, ce sont les
--    clauses, pas les coordonnees. Meme raison pour le registre des traitements de la
--    politique de confidentialite, resolu par CatalogueTraitements — la source unique
--    partagee avec l export RGPD de l article 15 (F22) doit le rester.
--
-- 6) AUCUNE TOUCHE A consentement
--
--    F24 est strictement additif. version_acceptee reste figee sur chaque preuve, avec
--    la meme valeur qu avant ; elle designe desormais une ligne reelle. Les preuves
--    deja enregistrees resolvent, parce que le seed reprend les identifiants EXACTS
--    des constantes qu il remplace.
--
--    34 tables metier apres cette migration (33 avant).
-- =====================================================================================

CREATE TABLE version_document
(
    id            BIGSERIAL PRIMARY KEY,
    type_document VARCHAR(30)  NOT NULL,
    version       VARCHAR(20)  NOT NULL,
    langue        VARCHAR(2)   NOT NULL,
    date_effet    TIMESTAMPTZ  NOT NULL,
    contenu       TEXT         NOT NULL,
    empreinte     VARCHAR(64)  NOT NULL,
    actif         BOOLEAN      NOT NULL DEFAULT TRUE,

    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by    VARCHAR(120),
    updated_by    VARCHAR(120),

    CONSTRAINT uq_version_document UNIQUE (type_document, version, langue),

    CONSTRAINT ck_version_document_type
        CHECK (type_document IN ('CGV', 'COOKIES', 'RENONCIATION_RETRACTATION')),

    -- Ferme sur l ensemble des langues servies, comme ResolveurLangueSession le fait
    -- cote web : une version publiee dans une langue non servie serait un texte que
    -- personne ne peut avoir lu.
    CONSTRAINT ck_version_document_langue
        CHECK (langue IN ('fr', 'nl', 'en')),

    CONSTRAINT ck_version_document_contenu_non_vide
        CHECK (length(btrim(contenu)) > 0),

    -- VARCHAR et non CHAR : CHAR complete a droite par des espaces, et une empreinte
    -- comparee a une empreinte calculee ne tomberait alors jamais juste. La forme est
    -- verifiee par le CHECK plutot que par la longueur du type.
    CONSTRAINT ck_version_document_empreinte
        CHECK (empreinte ~ '^[0-9a-f]{64}$')
);

-- La resolution de la version en vigueur trie sur (date_effet DESC, id DESC) parmi les
-- lignes actives d un type : c est le seul acces chaud, il est fait a chaque ecriture
-- de consentement.
CREATE INDEX ix_version_document_en_vigueur
    ON version_document (type_document, actif, date_effet DESC, id DESC);

COMMENT ON TABLE version_document IS
    'Texte engageant GELE d une version de document, une ligne par langue (F24). '
        'Archive append-only : une nouvelle redaction s ajoute, elle ne se modifie pas. '
        'consentement.version_acceptee resout vers la colonne version.';

COMMENT ON COLUMN version_document.date_effet IS
    'Entree en vigueur. La version en vigueur est la plus recente dont date_effet est '
        'passee : une publication peut donc etre datee a l avance, ce que la pratique '
        'juridique exige quand un changement de conditions doit etre annonce.';

COMMENT ON COLUMN version_document.contenu IS
    'Texte tel qu il a ete presente, dans cette langue. N inclut PAS l identite du '
        'garage ni le registre des traitements, lus en configuration a chaque rendu : '
        'un changement d adresse n invalide pas un consentement.';

COMMENT ON COLUMN version_document.empreinte IS
    'SHA-256 hexadecimal du contenu. Compare a chaque build au texte reellement '
        'presente par l application : une clause modifiee sans nouvelle version casse '
        'la build au lieu de deriver en silence.';

COMMENT ON COLUMN version_document.actif IS
    'false retire la version du jeu resolvable sans la supprimer — une preuve qui la '
        'designe doit continuer a la resoudre.';

-- =====================================================================================
-- Amorcage : les versions courantes, avec les identifiants EXACTS des constantes
-- qu elles remplacent (CGV-2026-01, COOKIES-2026-01, VI53-2026-01). C est ce qui fait
-- resoudre les preuves DEJA enregistrees : sans cette egalite, F24 rendrait orphelin
-- tout consentement anterieur a sa propre livraison.
--
-- Le texte gele est celui que l application presente au 2026-08-24, TEL QUEL — les
-- onze clauses redigees par le lot legal comprises, et plus aucun marqueur
-- [A COMPLETER] : il n en subsiste aucun a cette date. On ne gele pas le texte qu on
-- aurait voulu montrer mais celui qui a ete montre : c est le seul dont une preuve
-- d acceptation puisse rendre compte. Toute reecriture ulterieure, qu elle comble un
-- blanc ou corrige une clause, produira une NOUVELLE version, jamais une retouche de
-- celle-ci — c est precisement ce que la garde de non-derive rend obligatoire.
--
-- Le texte est ECRIT ICI plutot que derive au demarrage depuis les fichiers de
-- messages : une archive qui se recalcule a chaque boot ne gele rien: elle figerait,
-- sous un numero ancien, le texte du jour ou la base a ete creee. Dans une migration,
-- il est immuable par la regle du projet — une migration committee ne se modifie pas.
--
-- Le contenu est assemble par concat_ws(chr(10), ...) et non ecrit en litteral
-- multiligne : un litteral multiligne embarque les fins de ligne DU FICHIER, donc un
-- checkout en CRLF gelerait un texte different d un checkout en LF — deux empreintes
-- pour un meme document. Le separateur est ici pose explicitement, et une clause par
-- ligne reste relisible en revue.
-- =====================================================================================

INSERT INTO version_document
    (type_document, version, langue, date_effet, contenu, empreinte, actif, created_by, updated_by)
VALUES ('CGV', 'CGV-2026-01', 'fr',
        TIMESTAMPTZ '2026-08-24 00:00:00+02',
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
            'Une facture est émise pour toute commande payée et mise à disposition au format PDF dans l''espace membre. Les factures sont numérotées de manière continue et sans interruption, par exercice. Elles sont conservées sept ans, conformément à la législation relative à la taxe sur la valeur ajoutée.',
            'Article 10 — Garantie légale de conformité',
            'Pour les biens vendus au consommateur, la garantie légale de conformité de deux ans est applicable (articles 1649bis et suivants de l''ancien Code civil, désormais intégrés au livre 5 du nouveau Code civil). Cette garantie est due par le vendeur, c''est-à-dire le garage. Pour les prestations de service (entretien, réparation, diagnostic, etc.), le garage est tenu d''une obligation de conformité de droit commun : la prestation doit être conforme à ce qui a été convenu ; cette obligation n''est pas assortie d''une durée fixe mais reste encadrée par les règles de la prescription. La présente garantie légale s''applique indépendamment de toute garantie commerciale éventuelle.',
            'Article 11 — Responsabilité',
            'Le garage est responsable de la bonne exécution des prestations vendues, dans les conditions du droit commun (livre 6 du Code civil, entré en vigueur le 1er janvier 2025) et des présentes conditions. Les informations publiées sur le site sont fournies à titre indicatif ; le garage s''efforce de les tenir exactes et à jour sans garantir leur exhaustivité ou l''absence d''erreur. La responsabilité du garage ne peut en aucun cas être exclue ou limitée en cas de faute lourde, de dol, d''atteinte à la vie ou à l''intégrité physique, ni pour les obligations légales impératives — notamment la garantie légale de conformité et la sécurité des produits et services. Le garage n''assume aucune responsabilité quant au contenu des sites tiers accessibles par des liens, sur lesquels il n''exerce aucun contrôle.',
            'Article 12 — Données personnelles',
            'Le traitement des données personnelles est décrit dans la politique de confidentialité, accessible depuis chaque page du site.',
            'Article 13 — Droit applicable et litiges',
            'Les présentes conditions sont régies par le droit belge. En cas de litige, les parties rechercheront une solution amiable avant toute action judiciaire. Le consommateur est informé de l''existence du Service de médiation pour le consommateur (Boulevard du Roi Albert II 8, 1000 Bruxelles — mediationconsommateur.be) et, pour les achats en ligne, de la plateforme européenne de règlement en ligne des litiges (ec.europa.eu/consumers/odr). À défaut de règlement amiable, les cours et tribunaux belges sont compétents ; le consommateur conserve le bénéfice des règles protectrices lui permettant, le cas échéant, de saisir la juridiction de son lieu de domicile.'),
        '5fb51347674b397f16267f2e0588a6c7f54634aa182c7e62ecf49b52f91d92af', TRUE, 'systeme', 'systeme');

INSERT INTO version_document
    (type_document, version, langue, date_effet, contenu, empreinte, actif, created_by, updated_by)
VALUES ('COOKIES', 'COOKIES-2026-01', 'fr',
        TIMESTAMPTZ '2026-08-24 00:00:00+02',
        concat_ws(chr(10),
            'Votre choix sur les cookies',
            'Ce site dépose des cookies strictement nécessaires à son fonctionnement. Les autres finalités ne sont activées qu''avec votre accord. Vous pouvez continuer à naviguer sans répondre.',
            'Strictement nécessaires',
            'Toujours actifs',
            'Maintenir votre session et votre panier, et protéger les formulaires contre la falsification de requête.',
            'Durée : la session, et six mois pour la mémorisation de ce choix.',
            'Mesure d''audience',
            'Autoriser la mesure d''audience',
            'Comprendre quelles pages sont consultées, afin d''améliorer le site.',
            'Durée : treize mois au maximum.',
            'Marketing',
            'Autoriser la publicité ciblée',
            'Vous proposer des offres du garage adaptées à ce que vous avez consulté.',
            'Durée : treize mois au maximum.',
            'À ce jour, AutoServ+ n''installe aucun cookie de mesure d''audience ni de marketing. Votre choix est enregistré et conditionnera leur chargement s''ils sont ajoutés.',
            'Votre choix est conservé six mois, puis la question vous est reposée.',
            'Vous pouvez modifier votre choix à tout moment ; il s''applique immédiatement. Les cookies strictement nécessaires ne peuvent pas être désactivés : sans eux, le site ne fonctionne plus.'),
        'bf9ba233cb33e346f06cc59f0eb2a169ad2c9ca0fce85fcb0ba82185e8348250', TRUE, 'systeme', 'systeme');

INSERT INTO version_document
    (type_document, version, langue, date_effet, contenu, empreinte, actif, created_by, updated_by)
VALUES ('RENONCIATION_RETRACTATION', 'VI53-2026-01', 'fr',
        TIMESTAMPTZ '2026-08-24 00:00:00+02',
        concat_ws(chr(10),
            'Je demande que la prestation soit exécutée immédiatement et je reconnais perdre mon droit de rétractation une fois qu''elle aura été pleinement exécutée.',
            'Case facultative. Si vous ne la cochez pas, vous conservez 14 jours pour vous rétracter ; la prestation ne sera alors pas exécutée avant ce délai, sauf accord contraire avec le garage.'),
        '85a677cadae8fa4198e9336fb308e088bff97310af6a28670a504a983839e09e', TRUE, 'systeme', 'systeme');

INSERT INTO version_document
    (type_document, version, langue, date_effet, contenu, empreinte, actif, created_by, updated_by)
VALUES ('CGV', 'CGV-2026-01', 'nl',
        TIMESTAMPTZ '2026-08-24 00:00:00+02',
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
            'Voor elke betaalde bestelling wordt een factuur opgesteld en in pdf-formaat ter beschikking gesteld in de ledenruimte. De facturen worden per boekjaar doorlopend en zonder onderbreking genummerd. Zij worden zeven jaar bewaard, overeenkomstig de btw-wetgeving.',
            'Artikel 10 — Wettelijke conformiteitsgarantie',
            'Voor goederen die aan de consument worden verkocht, geldt de wettelijke conformiteitsgarantie van twee jaar (artikelen 1649bis en volgende van het oud Burgerlijk Wetboek, thans opgenomen in boek 5 van het nieuw Burgerlijk Wetboek). Deze garantie is verschuldigd door de verkoper, dat wil zeggen de garage. Voor de diensten (onderhoud, herstelling, diagnose, enz.) rust op de garage een gemeenrechtelijke conformiteitsverplichting: de dienst moet overeenstemmen met wat is overeengekomen; aan die verplichting is geen vaste duur verbonden, maar zij blijft omkaderd door de verjaringsregels. Deze wettelijke garantie geldt ongeacht enige commerciële waarborg.',
            'Artikel 11 — Aansprakelijkheid',
            'De garage is aansprakelijk voor de goede uitvoering van de verkochte prestaties, onder de voorwaarden van het gemeen recht (boek 6 van het Burgerlijk Wetboek, in werking getreden op 1 januari 2025) en van deze voorwaarden. De op de site gepubliceerde informatie wordt ter indicatie verstrekt; de garage streeft ernaar ze juist en actueel te houden zonder de volledigheid ervan of de afwezigheid van fouten te waarborgen. De aansprakelijkheid van de garage kan in geen geval worden uitgesloten of beperkt in geval van zware fout, bedrog, aantasting van het leven of de lichamelijke integriteit, noch voor de dwingende wettelijke verplichtingen — met name de wettelijke conformiteitsgarantie en de veiligheid van producten en diensten. De garage draagt geen enkele aansprakelijkheid voor de inhoud van sites van derden die via links toegankelijk zijn en waarover zij geen enkele controle uitoefent.',
            'Artikel 12 — Persoonsgegevens',
            'De verwerking van persoonsgegevens wordt beschreven in het privacybeleid, dat vanaf elke pagina van de site toegankelijk is.',
            'Artikel 13 — Toepasselijk recht en geschillen',
            'Deze voorwaarden worden beheerst door het Belgisch recht. Bij een geschil zoeken de partijen een minnelijke oplossing vóór elke gerechtelijke stap. De consument wordt ingelicht over het bestaan van de Consumentenombudsdienst (Koning Albert II-laan 8, 1000 Brussel — consumentenombudsdienst.be) en, voor onlineaankopen, over het Europese platform voor onlinegeschillenbeslechting (ec.europa.eu/consumers/odr). Bij gebrek aan een minnelijke regeling zijn de Belgische hoven en rechtbanken bevoegd; de consument behoudt het voordeel van de beschermende regels die hem in voorkomend geval toelaten de rechtbank van zijn woonplaats te vatten.'),
        '80420a435c1da0c51056cae257bfcfa8219ae5a0060107f51f96be65df91700a', TRUE, 'systeme', 'systeme');

INSERT INTO version_document
    (type_document, version, langue, date_effet, contenu, empreinte, actif, created_by, updated_by)
VALUES ('COOKIES', 'COOKIES-2026-01', 'nl',
        TIMESTAMPTZ '2026-08-24 00:00:00+02',
        concat_ws(chr(10),
            'Uw keuze over cookies',
            'Deze site plaatst cookies die strikt noodzakelijk zijn voor de werking ervan. De andere doeleinden worden pas geactiveerd met uw toestemming. U kunt verder surfen zonder te antwoorden.',
            'Strikt noodzakelijk',
            'Altijd actief',
            'Uw sessie en uw winkelmandje behouden, en de formulieren beschermen tegen vervalsing van aanvragen.',
            'Duur: de sessie, en zes maanden voor het bewaren van deze keuze.',
            'Publieksmeting',
            'Publieksmeting toestaan',
            'Begrijpen welke pagina''s worden geraadpleegd, om de site te verbeteren.',
            'Duur: hoogstens dertien maanden.',
            'Marketing',
            'Gerichte reclame toestaan',
            'U aanbiedingen van de garage voorstellen die aansluiten bij wat u hebt geraadpleegd.',
            'Duur: hoogstens dertien maanden.',
            'Op dit ogenblik plaatst AutoServ+ geen enkele cookie voor publieksmeting of marketing. Uw keuze wordt bewaard en bepaalt of ze geladen worden zodra ze worden toegevoegd.',
            'Uw keuze wordt zes maanden bewaard, daarna wordt de vraag opnieuw gesteld.',
            'U kunt uw keuze op elk moment wijzigen; ze geldt onmiddellijk. Strikt noodzakelijke cookies kunnen niet worden uitgeschakeld: zonder hen werkt de site niet meer.'),
        '8d30d7dae769d62d7be3449ea54410b0c8f3a3cdd79ce2e3681bf2c94800a9e4', TRUE, 'systeme', 'systeme');

INSERT INTO version_document
    (type_document, version, langue, date_effet, contenu, empreinte, actif, created_by, updated_by)
VALUES ('RENONCIATION_RETRACTATION', 'VI53-2026-01', 'nl',
        TIMESTAMPTZ '2026-08-24 00:00:00+02',
        concat_ws(chr(10),
            'Ik vraag de onmiddellijke uitvoering van de dienst en erken dat ik mijn herroepingsrecht verlies zodra deze volledig is uitgevoerd.',
            'Optioneel. Vinkt u dit niet aan, dan behoudt u 14 dagen om te herroepen; de dienst wordt dan niet vóór die termijn uitgevoerd, tenzij anders overeengekomen met de garage.'),
        '2601dce86f0dc13f6116af44ce49dfb30732c4d43978d6a210c832566f20d4db', TRUE, 'systeme', 'systeme');

INSERT INTO version_document
    (type_document, version, langue, date_effet, contenu, empreinte, actif, created_by, updated_by)
VALUES ('CGV', 'CGV-2026-01', 'en',
        TIMESTAMPTZ '2026-08-24 00:00:00+02',
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
            'An invoice is issued for every paid order and made available as a PDF in the member area. Invoices are numbered continuously and without gaps, per financial year. They are kept for seven years, in accordance with value added tax legislation.',
            'Article 10 — Legal guarantee of conformity',
            'For goods sold to a consumer, the two-year legal guarantee of conformity applies (Articles 1649bis et seq. of the former Civil Code, now incorporated into Book 5 of the new Civil Code). This guarantee is owed by the seller, that is to say the garage. For services (servicing, repair, diagnosis, etc.), the garage is bound by an ordinary-law obligation of conformity: the service must match what was agreed; that obligation carries no fixed duration but remains framed by the rules on limitation periods. This legal guarantee applies irrespective of any commercial warranty.',
            'Article 11 — Liability',
            'The garage is liable for the proper performance of the services sold, under the conditions of the general law (Book 6 of the Civil Code, in force since 1 January 2025) and of these terms. The information published on the site is provided for guidance; the garage endeavours to keep it accurate and up to date without guaranteeing that it is exhaustive or free of error. The liability of the garage may in no case be excluded or limited in the event of gross negligence, fraud, harm to life or physical integrity, nor for mandatory legal obligations — in particular the legal guarantee of conformity and the safety of products and services. The garage assumes no liability for the content of third-party sites reachable through links, over which it exercises no control.',
            'Article 12 — Personal data',
            'The processing of personal data is described in the privacy policy, which is reachable from every page of the site.',
            'Article 13 — Applicable law and disputes',
            'These terms are governed by Belgian law. In the event of a dispute, the parties will seek an amicable solution before any legal action. The consumer is informed of the existence of the Consumer Mediation Service (Boulevard du Roi Albert II 8, 1000 Brussels — mediationconsommateur.be) and, for online purchases, of the European online dispute resolution platform (ec.europa.eu/consumers/odr). Failing an amicable settlement, the Belgian courts have jurisdiction; the consumer retains the benefit of the protective rules allowing them, where applicable, to bring proceedings before the court of their place of domicile.'),
        'e457e56ba594020e648d77ad0e5a552afd598209452dd3df71665794cd81efe6', TRUE, 'systeme', 'systeme');

INSERT INTO version_document
    (type_document, version, langue, date_effet, contenu, empreinte, actif, created_by, updated_by)
VALUES ('COOKIES', 'COOKIES-2026-01', 'en',
        TIMESTAMPTZ '2026-08-24 00:00:00+02',
        concat_ws(chr(10),
            'Your cookie choice',
            'This site sets cookies that are strictly necessary for it to work. Other purposes are only enabled with your agreement. You can keep browsing without answering.',
            'Strictly necessary',
            'Always active',
            'Keeping your session and your basket, and protecting forms against request forgery.',
            'Duration: the session, and six months for storing this choice.',
            'Audience measurement',
            'Allow audience measurement',
            'Understanding which pages are viewed, in order to improve the site.',
            'Duration: thirteen months at most.',
            'Marketing',
            'Allow targeted advertising',
            'Offering you garage deals matching what you have viewed.',
            'Duration: thirteen months at most.',
            'As of today, AutoServ+ sets no audience measurement or marketing cookie. Your choice is recorded and will govern their loading if they are added.',
            'Your choice is kept for six months, after which you will be asked again.',
            'You can change your choice at any time; it takes effect immediately. Strictly necessary cookies cannot be disabled: without them the site no longer works.'),
        '7b17cc85e719fd0f6f3b3a945ed974b00367dfb8706bab0be903746007fb17bf', TRUE, 'systeme', 'systeme');

INSERT INTO version_document
    (type_document, version, langue, date_effet, contenu, empreinte, actif, created_by, updated_by)
VALUES ('RENONCIATION_RETRACTATION', 'VI53-2026-01', 'en',
        TIMESTAMPTZ '2026-08-24 00:00:00+02',
        concat_ws(chr(10),
            'I request immediate performance of the service and acknowledge that I lose my right of withdrawal once it has been fully performed.',
            'Optional. If you leave it unticked, you keep 14 days to withdraw; the service will then not be performed before that period, unless otherwise agreed with the garage.'),
        '98bf89cbbc6e3814e644d10f7dce189df303e171c8d4fc2ffbbf6da694516c2b', TRUE, 'systeme', 'systeme');

-- =====================================================================================
-- Enregistrement au balayage d anonymisation (F23, V28).
--
-- version_document porte created_by / updated_by comme toute table du schema, et la
-- liste de fn_tables_traces_audit() est ENUMEREE, pas derivee du catalogue :
-- SchemaIT.listeDesTracesExhaustive casse la build sur toute colonne d audit non
-- declaree. La fonction est donc reecrite en entier avec les deux nouvelles entrees —
-- c est le prix assume de la liste explicite, et c est ce prix qui garantit qu aucune
-- trace ne fuit en silence.
--
-- En pratique ces colonnes valent 'systeme' : une version de document est publiee par
-- une migration, jamais par un membre. Les balayer n en reste pas moins juste — la
-- regle est l exhaustivite, pas la selection des tables qu on croit concernees.
-- =====================================================================================

CREATE OR REPLACE FUNCTION fn_tables_traces_audit()
    RETURNS TABLE (nom_table TEXT, nom_colonne TEXT) AS $$
    VALUES
        ('avis'::TEXT, 'created_by'::TEXT),
        ('avis', 'updated_by'),
        ('avoir', 'created_by'),
        ('avoir', 'updated_by'),
        ('categorie', 'created_by'),
        ('categorie', 'updated_by'),
        ('clef_api', 'created_by'),
        ('clef_api', 'updated_by'),
        ('commande', 'created_by'),
        ('commande', 'updated_by'),
        ('compteur_avoir', 'updated_by'),
        ('compteur_facture', 'updated_by'),
        ('consentement', 'created_by'),
        ('consentement', 'updated_by'),
        ('conversation', 'created_by'),
        ('conversation', 'updated_by'),
        ('demande_annulation', 'created_by'),
        ('demande_annulation', 'updated_by'),
        ('facture', 'created_by'),
        ('facture', 'updated_by'),
        ('historique_modification_catalogue', 'created_by'),
        ('historique_modification_catalogue', 'updated_by'),
        ('historique_statut_intervention', 'created_by'),
        ('historique_statut_intervention', 'updated_by'),
        ('indisponibilite', 'created_by'),
        ('indisponibilite', 'updated_by'),
        ('intervention', 'created_by'),
        ('intervention', 'updated_by'),
        ('ligne_intervention', 'created_by'),
        ('ligne_intervention', 'updated_by'),
        ('ligne_panier', 'created_by'),
        ('ligne_panier', 'updated_by'),
        ('message', 'created_by'),
        ('message', 'updated_by'),
        ('notification', 'created_by'),
        ('notification', 'updated_by'),
        ('paiement', 'created_by'),
        ('paiement', 'updated_by'),
        ('panier', 'created_by'),
        ('panier', 'updated_by'),
        ('parametre_atelier', 'updated_by'),
        ('photo', 'created_by'),
        ('photo', 'updated_by'),
        ('piece', 'created_by'),
        ('piece', 'updated_by'),
        ('place_parking', 'created_by'),
        ('place_parking', 'updated_by'),
        ('plage_ouverture', 'created_by'),
        ('plage_ouverture', 'updated_by'),
        ('poste_atelier', 'created_by'),
        ('poste_atelier', 'updated_by'),
        ('rdv', 'created_by'),
        ('rdv', 'updated_by'),
        ('rdv_service', 'created_by'),
        ('rdv_service', 'updated_by'),
        ('reservation_parking', 'created_by'),
        ('reservation_parking', 'updated_by'),
        ('service', 'created_by'),
        ('service', 'updated_by'),
        ('utilisateur', 'created_by'),
        ('utilisateur', 'updated_by'),
        ('vehicule', 'created_by'),
        ('vehicule', 'updated_by'),
        ('version_document', 'created_by'),
        ('version_document', 'updated_by');
$$ LANGUAGE sql IMMUTABLE;
