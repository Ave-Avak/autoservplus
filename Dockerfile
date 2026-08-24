# =====================================================================================
# Image de l'application AutoServ+, en deux etapes.
#
# La separation build / runtime n'est pas cosmetique : l'image finale ne porte qu'un
# JRE et le JAR, ni JDK, ni Maven, ni sources, ni cache de dependances. Une image de
# build livree en production expose le code source et triple sa surface d'attaque.
# =====================================================================================

# ---------------------------------------------------------------------------------
# Etape 1 — compilation
# ---------------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build

WORKDIR /build

# Le wrapper et le descriptif de projet sont copies AVANT les sources, et resolus
# seuls : Docker met alors en cache la couche des dependances, qui ne change qu'au
# rythme du pom.xml. Copier src/ d'abord invaliderait ce cache a chaque edition de
# code et retelechargerait tout Maven Central a chaque build.
COPY mvnw ./
COPY .mvn/ .mvn/
COPY pom.xml ./

# Deux precautions liees au poste de developpement, sous Windows :
#   - le bit d'execution ne survit pas au clone ;
#   - le wrapper y est presente avec des fins de ligne CRLF, et le retour chariot du
#     shebang fait echouer le build sur un « ./mvnw: not found » qui ne designe pas
#     le vrai probleme. Le depot, lui, stocke bien du LF (.gitattributes), donc
#     l'integration continue ne voit jamais ce defaut : sans cette normalisation,
#     l'image se construirait sur Linux et pas sur la machine de son auteur.
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/

# Tests ignores volontairement : ils exigent Docker (Testcontainers), indisponible
# dans un conteneur de build. C'est l'integration continue qui execute « verify » ;
# construire l'image ne doit pas etre une seconde execution partielle de la suite.
RUN ./mvnw -B -q package -DskipTests

# ---------------------------------------------------------------------------------
# Etape 2 — execution
# ---------------------------------------------------------------------------------
# Base Debian/Ubuntu et non Alpine : Alpine repose sur musl, dont les differences de
# comportement avec la glibc sont une source connue d'ennuis en JVM (resolution DNS,
# fuseaux horaires, empreinte memoire des threads). L'ecart de taille ne justifie pas
# de deployer sur une libc differente de celle des tests.
#
# Aucune police systeme n'est installee, et ce n'est pas un oubli : les PDF comptables
# composent en Helvetica via FontFactory, l'une des quatorze polices garanties par le
# format PDF, dont OpenPDF porte les metriques dans son propre JAR. Rien n'est lu sur
# le systeme, donc ni fontconfig ni fonts-dejavu-core ne sont necessaires.
FROM eclipse-temurin:21-jre

# Utilisateur non privilegie : une faille d'execution de code arbitraire dans
# l'application ne doit pas donner root sur le conteneur.
RUN useradd --system --create-home --shell /usr/sbin/nologin autoserv \
    && mkdir -p /data/factures /data/uploads \
    && chown -R autoserv:autoserv /data

WORKDIR /app
COPY --from=build --chown=autoserv:autoserv /build/target/*.jar app.jar

USER autoserv

# MaxRAMPercentage plutot qu'un -Xmx fixe : la JVM lit alors le plafond memoire du
# conteneur (cgroup), donc la meme image se dimensionne seule sur un CX22 comme sur
# un poste de developpement. Sans ce reglage elle s'en tiendrait a 25 % par defaut,
# et un quart de 4 Go laisserait la moitie de la machine inutilisee.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"

# UTC, comme le conteneur PostgreSQL. Le schema n'a que des colonnes TIMESTAMPTZ,
# donc le fuseau du serveur n'entre pas dans la donnee, mais il entre dans les
# journaux : les faire concorder entre les deux conteneurs evite d'avoir a decaler
# mentalement une trace applicative pour la rapprocher d'une trace SQL.
ENV TZ=UTC

EXPOSE 8080

# Actuator n'expose que « health » et « info » (application.yml), le point de controle
# est donc deja disponible sans rien elargir. curl est present dans l'image Temurin,
# aucun paquet supplementaire n'est installe pour cette ligne.
#
# start-period genereux : Flyway peut avoir des migrations a appliquer au premier
# demarrage, et un conteneur declare malade pendant ce temps serait redemarre en
# boucle, ce qui laisserait le schema a moitie migre.
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1

# Forme « sh -c » et non exec directe : JAVA_OPTS doit etre reinterprete au demarrage
# pour qu un exploitant puisse l ajuster depuis .env sans reconstruire l image.
#
# Le « exec » interne n est pas decoratif : sans lui le shell reste PID 1 et java
# devient son enfant. Docker adresse SIGTERM au seul PID 1, et dash ne le relaie pas
# a ses enfants — l application ne recevrait donc jamais l ordre d arret et serait
# SIGKILL au bout du delai de grace, a chaque « compose down » comme a chaque
# redeploiement. Verifie : sans exec, /proc/1 porte sh et java tourne en PID 7.
ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar app.jar"]
