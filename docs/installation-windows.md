# Installation locale sous Windows (sans Docker)

Procédure à suivre quand Docker Desktop n'est pas utilisable (virtualisation VT-x désactivée dans le BIOS, pas de droits administrateur, etc.). Elle a été déroulée sur Windows 10 : **15/15 tests JUnit, SoapUI 4/4 cas (54 assertions), test de bout en bout 23/23**.

Tout s'installe dans un dossier **sans espace**, ici `%USERPROFILE%\tools` (`~/tools` sous Git Bash). Seul Netdata demande les droits administrateur.

## Méthode automatique (recommandée)

Prérequis : [Git for Windows](https://git-scm.com/download/win) (fournit Git Bash et curl) et environ **3 Go d'espace disque libre**. Tout le reste est téléchargé par le script.

```bash
git clone https://github.com/malickabdoul/employee-soap-servic.git employee-soap-service
cd employee-soap-service
./scripts/install-windows.sh          # installe, initialise les bases, synchronise, démarre (~5 min hors téléchargements)
```

Accepter les deux fenêtres administrateur (installation de Netdata, puis copie de sa configuration). Ensuite :

```bash
./scripts/install-windows.sh status   # état et adresses (WSDL, Netdata, Spoon)
./scripts/install-windows.sh start    # après un redémarrage du PC
./scripts/install-windows.sh stop
source ~/tools/env-local.sh && ./scripts/run-soapui.sh && ./tests/e2e/e2e.sh   # tests
```

Le script est rejouable : il ne télécharge et n'initialise que ce qui manque. Il réutilise un JDK 17+, Maven et PostgreSQL déjà installés s'il les trouve. Testé depuis zéro sur une seconde instance (ports 3307/5434/8081) : installation en 3 min 40 hors téléchargements, puis test de bout en bout 23/23 sur l'installation complète.

## Méthode manuelle (détail des étapes du script)

| Composant | Version testée | Port | Source |
|---|---|---|---|
| JDK (service) | 17 ou 21 | – | adoptium.net |
| Maven | 3.9 | – | maven.apache.org |
| MySQL Community (archive ZIP) | 8.0.40 | 3306 | `https://downloads.mysql.com/archives/get/p/23/file/mysql-8.0.40-winx64.zip` |
| PostgreSQL | 16 ou 17 | 5433 (instance dédiée) | binaires d'une installation existante ou postgresql.org |
| Java 11 JRE (Pentaho) | 11 | – | `https://api.adoptium.net/v3/binary/latest/11/ga/windows/x64/jre/hotspot/normal/eclipse` |
| Pentaho PDI CE + Connector/J 8.0.33 | 9.4.0.0-343 | – | voir [pentaho/INSTALLATION.md](../pentaho/INSTALLATION.md) ¹ |
| SoapUI open source | 5.7.2 | – | `https://dl.eviware.com/soapuios/5.7.2/SoapUI-5.7.2-linux-bin.tar.gz` (pur Java, fonctionne sous Windows) |
| Netdata pour Windows | 2.11 | 19999 | `winget install Netdata.Netdata` (administrateur) |

¹ Depuis 2025, SourceForge ne fournit plus PDI CE ; Hitachi Vantara le distribue via un formulaire (« Pentaho Developer Edition »). L'archive `pdi-ce-9.4.0.0-343.zip` est aussi conservée par le miroir communautaire `github.com/ambientelivre/legacy-pentaho-ce` (releases).

Toutes les commandes ci-dessous se lancent dans **Git Bash** depuis la racine du dépôt.

## 1. MySQL (base source)

```bash
T=~/tools; cd $T && unzip -q mysql-8.0.40-winx64.zip && mkdir -p data
cat > my.ini <<EOF
[mysqld]
basedir=$(cygpath -m $T)/mysql-8.0.40-winx64
datadir=$(cygpath -m $T)/data/mysql
port=3306
bind-address=127.0.0.1
default-time-zone='+00:00'
character-set-server=utf8mb4
collation-server=utf8mb4_unicode_ci
EOF
M=$T/mysql-8.0.40-winx64/bin
$M/mysqld.exe --defaults-file="$(cygpath -m $T)/my.ini" --initialize-insecure
$M/mysqld.exe --defaults-file="$(cygpath -m $T)/my.ini" &      # laisse le serveur tourner
$M/mysql.exe -uroot -e "ALTER USER 'root'@'localhost' IDENTIFIED BY 'root_pwd';"
cd - && for f in db/mysql/01_schema.sql db/mysql/02_data.sql; do $M/mysql.exe -uroot -proot_pwd --default-character-set=utf8mb4 < $f; done
```

Résultat attendu : `SELECT count(*) FROM rh_source.employes` → 23.

## 2. PostgreSQL (base de référence) sur le port 5433

Une instance dédiée évite de toucher à un PostgreSQL déjà installé sur le port 5432.

```bash
B="/c/Program Files/PostgreSQL/17/bin"; T=~/tools
echo postgres_pwd > $T/pw.txt
"$B/initdb.exe" -D "$(cygpath -m $T)/data/pg" -U postgres --pwfile="$(cygpath -m $T)/pw.txt" -E UTF8 --locale=C -A scram-sha-256 && rm $T/pw.txt
printf "port = 5433\nlisten_addresses = 'localhost'\ntimezone = 'Africa/Ouagadougou'\n" >> $T/data/pg/postgresql.conf
"$B/pg_ctl.exe" -D "$(cygpath -m $T)/data/pg" -l "$(cygpath -m $T)/data/pg.log" start > /dev/null 2>&1
export PGPASSWORD=postgres_pwd
"$B/psql.exe" -h localhost -p 5433 -U postgres -c "CREATE DATABASE employes_ref"
for f in db/postgres/01_schema.sql db/postgres/02_users.sql; do "$B/psql.exe" -h localhost -p 5433 -U postgres -d employes_ref -v ON_ERROR_STOP=1 -f $f; done
```

## 3. Service SOAP

```bash
cd service && mvn -B package && cd ..          # compile + 15 tests JUnit
DB_URL=jdbc:postgresql://localhost:5433/employes_ref DB_USER=app DB_PASSWORD=app_pwd \
  java -Duser.timezone=Africa/Ouagadougou -jar service/target/employee-soap-service.jar
```

Vérifier : http://localhost:8080/services/EmployeeService?wsdl et http://localhost:8080/actuator/health (`UP`, base `PostgreSQL`).

## 4. Pentaho

Dézipper PDI dans `~/tools/data-integration`, copier `mysql-connector-j-8.0.33.jar` dans `data-integration/lib/`, dézipper le JRE 11 dans `~/tools`. Sous Git Bash, `run-sync.sh` détecte Windows et appelle directement le lanceur Java de PDI (`kitchen.sh` refuse cet OS et `Kitchen.bat` gère mal les chemins contenant des espaces).

```bash
export PDI_HOME=~/tools/data-integration PENTAHO_JAVA_HOME=~/tools/jdk-11.0.32.1+1-jre PG_PORT=5433
./scripts/run-sync.sh aller      # 23 lus, 20 chargés, 3 rejetés
```

## 5. Netdata

Dans un PowerShell **administrateur** :

```powershell
winget install --id Netdata.Netdata -e
```

Copier ensuite, en adaptant les hôtes pour une installation locale, les fichiers du dossier `netdata/` vers `C:\Program Files\Netdata\etc\netdata\` :

| Fichier | Valeur locale |
|---|---|
| `go.d/mysql.conf` | `dsn: netdata:netdata_pwd@tcp(127.0.0.1:3306)/` |
| `go.d/postgres.conf` | `dsn: 'postgres://netdata:netdata_pwd@127.0.0.1:5433/employes_ref?sslmode=disable'` |
| `go.d/prometheus.conf` | `url: http://127.0.0.1:8080/actuator/prometheus` |
| `health.d/employee_service.conf` | inchangé |

puis `Restart-Service Netdata`. Tableau de bord : http://localhost:19999.

## 6. Tests

```bash
export E2E_MODE=local PDI_HOME=~/tools/data-integration PENTAHO_JAVA_HOME=~/tools/jdk-11.0.32.1+1-jre \
       MYSQL_CLI=~/tools/mysql-8.0.40-winx64/bin/mysql.exe PSQL="/c/Program Files/PostgreSQL/17/bin/psql.exe" \
       PG_CTL="/c/Program Files/PostgreSQL/17/bin/pg_ctl.exe" PG_DATA="$(cygpath -m ~/tools/data/pg)" PG_PORT=5433
unset NoDefaultCurrentDirectoryInExePath

SOAPUI_HOME="$(cygpath -m ~/tools/soapui/SoapUI-5.7.2)" SOAPUI_JAVA_HOME=$PENTAHO_JAVA_HOME ./scripts/run-soapui.sh   # 4 cas, 54 assertions
./tests/e2e/e2e.sh                                                                                                   # 23/23
```

Le test de bout en bout arrête puis redémarre l'instance PostgreSQL (`pg_ctl`) pour vérifier l'erreur `DATA_ACCESS_ERROR` et la reconnexion automatique du service.
