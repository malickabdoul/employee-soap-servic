#!/usr/bin/env bash
# Installation et démarrage automatiques du projet sous Windows, SANS Docker (à lancer dans Git Bash).
#
#   ./scripts/install-windows.sh           installe ce qui manque, initialise les bases, démarre tout
#   ./scripts/install-windows.sh start     redémarre MySQL, PostgreSQL et le service (ex. après un redémarrage du PC)
#   ./scripts/install-windows.sh stop      arrête MySQL, PostgreSQL et le service
#   ./scripts/install-windows.sh reset     recrée les bases avec les données de départ (avant une démonstration)
#   ./scripts/install-windows.sh status    état des composants et adresses utiles
#
# Variables facultatives : TOOLS (défaut ~/tools, chemin SANS espace), MYSQL_PORT (3306), PG_PORT (5433),
# SERVICE_PORT (8080), PG_BIN (dossier bin d'un PostgreSQL existant), SKIP_NETDATA=1, SKIP_TESTS=1.
# Détails et procédure manuelle équivalente : docs/installation-windows.md
set -euo pipefail
cd "$(dirname "$0")/.."
REPO="$PWD"

TOOLS="${TOOLS:-$HOME/tools}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
PG_PORT="${PG_PORT:-5433}"
SERVICE_PORT="${SERVICE_PORT:-8080}"
DL="$TOOLS/dl"
DATA="$TOOLS/data"

MYSQL_VER=8.0.40
MYSQL_URL="https://downloads.mysql.com/archives/get/p/23/file/mysql-$MYSQL_VER-winx64.zip"
PG_URL="https://get.enterprisedb.com/postgresql/postgresql-17.6-1-windows-x64-binaries.zip"
JDK17_URL="https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse"
JRE11_URL="https://api.adoptium.net/v3/binary/latest/11/ga/windows/x64/jre/hotspot/normal/eclipse"
MAVEN_URL="https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.zip"
PDI_ZIP=pdi-ce-9.4.0.0-343.zip
PDI_URL="https://github.com/ambientelivre/legacy-pentaho-ce/releases/download/pdi-ce-9.4.0.0-343/$PDI_ZIP"
CONNECTOR=mysql-connector-j-8.0.33.jar
CONNECTOR_URL="https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/8.0.33/$CONNECTOR"
SOAPUI_TGZ=SoapUI-5.7.2-linux-bin.tar.gz
SOAPUI_URL="https://dl.eviware.com/soapuios/5.7.2/$SOAPUI_TGZ"

say()  { echo; echo "== $*"; }
info() { echo "   $*"; }
die()  { echo "ERREUR : $*" >&2; exit 1; }
win()  { cygpath -m "$1"; }                       # chemin Windows avec des /
listening() { netstat -ano | grep LISTENING | grep -q ":$1 "; }
wait_port() {  # wait_port <port> <nom> <secondes>
  for _ in $(seq 1 "$3"); do listening "$1" && return 0; sleep 1; done
  die "$2 ne répond pas sur le port $1"
}
ps_start() {  # ps_start <exe> <arguments> <journal> : processus détaché, sans fenêtre
  # Entrées/sorties de powershell redirigées vers /dev/null : sinon le processus lancé hérite
  # de la sortie du script et bloque un éventuel « | tee » ou « | tail » jusqu'à son arrêt.
  powershell.exe -NoProfile -Command "Start-Process -WindowStyle Hidden -FilePath '$(cygpath -w "$1")' \
    -ArgumentList '$2' -RedirectStandardOutput '$(cygpath -w "$3")' -RedirectStandardError '$(cygpath -w "$3").err'" \
    < /dev/null > /dev/null 2>&1
}
download() {  # download <url> <fichier> : reprise automatique si la connexion coupe
  [[ -s "$DL/$2" ]] && return 0
  info "téléchargement de $2"
  for _ in $(seq 1 10); do
    curl -fsSL -C - --retry 3 --speed-limit 20000 --speed-time 30 -o "$DL/$2.part" "$1" && { mv "$DL/$2.part" "$DL/$2"; return 0; }
    sleep 2
  done
  die "téléchargement impossible : $1"
}
first_dir() { { ls -d "$@" 2>/dev/null || true; } | sort -V | tail -1; }

[[ "$(uname -s)" =~ ^(MINGW|MSYS|CYGWIN) ]] || die "ce script est prévu pour Git Bash sous Windows (sinon : docker compose up)"
[[ "$TOOLS" != *" "* ]] || die "TOOLS ne doit pas contenir d'espace (Pentaho et MySQL ne le supportent pas) : $TOOLS"
mkdir -p "$DL" "$DATA"

# ---------------------------------------------------------------- localisation des outils
locate() {
  M="$TOOLS/mysql-$MYSQL_VER-winx64/bin"
  JRE11="$(first_dir "$TOOLS"/jdk-11*-jre)"
  PDI_HOME="$TOOLS/data-integration"
  SOAPUI_HOME="$TOOLS/soapui/SoapUI-5.7.2"
  if [[ -z "${PG_BIN:-}" ]]; then
    PG_BIN="$(first_dir "$TOOLS"/pgsql/bin)"
    for d in "/c/Program Files/PostgreSQL/"*/bin; do [[ -x "$d/initdb.exe" ]] && PG_BIN="$d"; done
  fi
  JAVA17=""
  local v; v="$(java -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+).*/\1/')" || true
  if [[ "$v" =~ ^[0-9]+$ && "$v" -ge 17 ]]; then
    JAVA17="$(cygpath -u "$(java -XshowSettings:properties -version 2>&1 | grep 'java.home' | sed 's/.*= //' | tr -d '\r')")"
  fi
  [[ -z "$JAVA17" ]] && JAVA17="$(first_dir "$TOOLS"/jdk-17*)"
  MVN="$(command -v mvn || true)"
  [[ -z "$MVN" && -x "$TOOLS/apache-maven-3.9.9/bin/mvn" ]] && MVN="$TOOLS/apache-maven-3.9.9/bin/mvn"
  return 0
}

# ---------------------------------------------------------------- installation des logiciels
install_tools() {
  locate
  say "1/7 Logiciels (dans $TOOLS)"
  if [[ -z "$JAVA17" ]]; then download "$JDK17_URL" jdk17.zip; unzip -q -o "$DL/jdk17.zip" -d "$TOOLS"; fi
  if [[ -z "$MVN" ]]; then download "$MAVEN_URL" maven.zip; unzip -q -o "$DL/maven.zip" -d "$TOOLS"; fi
  if [[ ! -x "$M/mysqld.exe" ]]; then download "$MYSQL_URL" "mysql-$MYSQL_VER-winx64.zip"; unzip -q -o "$DL/mysql-$MYSQL_VER-winx64.zip" -d "$TOOLS"; fi
  if [[ -z "$PG_BIN" ]]; then download "$PG_URL" pgsql.zip; unzip -q -o "$DL/pgsql.zip" -d "$TOOLS"; fi
  if [[ -z "$JRE11" ]]; then download "$JRE11_URL" jre11.zip; unzip -q -o "$DL/jre11.zip" -d "$TOOLS"; fi
  if [[ ! -f "$PDI_HOME/launcher/launcher.jar" ]]; then download "$PDI_URL" "$PDI_ZIP"; unzip -q -o "$DL/$PDI_ZIP" -d "$TOOLS"; fi
  if [[ ! -f "$PDI_HOME/lib/$CONNECTOR" ]]; then download "$CONNECTOR_URL" "$CONNECTOR"; cp "$DL/$CONNECTOR" "$PDI_HOME/lib/"; fi
  if [[ ! -f "$SOAPUI_HOME/bin/soapui-5.7.2.jar" ]]; then
    download "$SOAPUI_URL" "$SOAPUI_TGZ"; mkdir -p "$TOOLS/soapui"; tar -xzf "$DL/$SOAPUI_TGZ" -C "$TOOLS/soapui"
  fi
  # place disque : archives déjà extraites et symboles de débogage MySQL (~1,2 Go) inutiles
  rm -f "$DL"/*
  find "$TOOLS/mysql-$MYSQL_VER-winx64" -name "*.pdb" -delete 2>/dev/null || true
  find "$TOOLS/mysql-$MYSQL_VER-winx64" -type d -name debug -prune -exec rm -rf {} + 2>/dev/null || true
  rm -f "$M/mysqld-debug.exe"
  locate
  info "Java service : $JAVA17"
  info "Maven        : $MVN"
  info "MySQL        : $M"
  info "PostgreSQL   : $PG_BIN"
  info "Java 11      : $JRE11"
  info "Pentaho      : $PDI_HOME"
  info "SoapUI       : $SOAPUI_HOME"
}

# ---------------------------------------------------------------- MySQL (base source)
start_mysql() {
  say "2/7 MySQL (port $MYSQL_PORT)"
  local first=0
  if [[ ! -d "$DATA/mysql" ]]; then
    cat > "$TOOLS/my.ini" <<EOF
[mysqld]
basedir=$(win "$TOOLS/mysql-$MYSQL_VER-winx64")
datadir=$(win "$DATA/mysql")
port=$MYSQL_PORT
bind-address=127.0.0.1
default-time-zone='+00:00'
character-set-server=utf8mb4
collation-server=utf8mb4_unicode_ci
skip-log-bin
EOF
    "$M/mysqld.exe" --defaults-file="$(win "$TOOLS/my.ini")" --initialize-insecure
    first=1
  fi
  if listening "$MYSQL_PORT"; then info "déjà démarré"; else
    ps_start "$M/mysqld.exe" "--defaults-file=$(win "$TOOLS/my.ini")" "$DATA/mysqld.log"
    wait_port "$MYSQL_PORT" MySQL 60
  fi
  if [[ $first == 1 ]]; then
    "$M/mysql.exe" -h127.0.0.1 -P"$MYSQL_PORT" -uroot -e "ALTER USER 'root'@'localhost' IDENTIFIED BY 'root_pwd';"
  fi
  if ! "$M/mysql.exe" -h127.0.0.1 -P"$MYSQL_PORT" -uroot -proot_pwd -e "USE rh_source" 2>/dev/null; then
    for f in db/mysql/01_schema.sql db/mysql/02_data.sql; do
      "$M/mysql.exe" -h127.0.0.1 -P"$MYSQL_PORT" -uroot -proot_pwd --default-character-set=utf8mb4 < "$f" 2>&1 | grep -v "Using a password" || true
    done
  fi
  info "$("$M/mysql.exe" -h127.0.0.1 -P"$MYSQL_PORT" -uroot -proot_pwd -N rh_source -e "SELECT count(*) FROM employes" 2>/dev/null) employés dans rh_source.employes"
}

# ---------------------------------------------------------------- PostgreSQL (base de référence)
pg()  { PGPASSWORD=postgres_pwd "$PG_BIN/psql.exe" -h localhost -p "$PG_PORT" -U postgres "$@"; }
start_pg() {
  say "3/7 PostgreSQL (port $PG_PORT, instance dédiée)"
  if [[ ! -d "$DATA/pg" ]]; then
    echo postgres_pwd > "$TOOLS/pw.txt"
    "$PG_BIN/initdb.exe" -D "$(win "$DATA/pg")" -U postgres --pwfile="$(win "$TOOLS/pw.txt")" -E UTF8 --locale=C -A scram-sha-256 > "$DATA/initdb.log" 2>&1
    rm -f "$TOOLS/pw.txt"
    printf "port = %s\nlisten_addresses = 'localhost'\ntimezone = 'Africa/Ouagadougou'\nlog_timezone = 'Africa/Ouagadougou'\n" "$PG_PORT" >> "$DATA/pg/postgresql.conf"
  fi
  if listening "$PG_PORT"; then info "déjà démarré"; else
    "$PG_BIN/pg_ctl.exe" -D "$(win "$DATA/pg")" -l "$(win "$DATA/pg.log")" -w start > /dev/null 2>&1
    wait_port "$PG_PORT" PostgreSQL 60
  fi
  if [[ -z "$(pg -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='employes_ref'" | tr -d '\r')" ]]; then
    pg -d postgres -qc "CREATE DATABASE employes_ref"
    for f in db/postgres/01_schema.sql db/postgres/02_users.sql; do pg -d employes_ref -q -v ON_ERROR_STOP=1 -f "$f"; done
  fi
  info "base employes_ref prête"
}

# ---------------------------------------------------------------- service SOAP
start_service() {
  say "4/7 Service SOAP (port $SERVICE_PORT)"
  local jar="$REPO/service/target/employee-soap-service.jar"
  if [[ ! -f "$jar" ]]; then
    local opts="-B -q package"; [[ -n "${SKIP_TESTS:-}" ]] && opts="$opts -DskipTests"
    info "compilation Maven (et tests JUnit)…"
    (cd service && JAVA_HOME="$JAVA17" "$MVN" $opts)
  fi
  cp -u "$jar" "$TOOLS/employee-soap-service.jar"   # copie dans un chemin sans espace pour le lancement détaché
  if listening "$SERVICE_PORT"; then info "déjà démarré"; else
    DB_URL="jdbc:postgresql://localhost:$PG_PORT/employes_ref" DB_USER=app DB_PASSWORD=app_pwd SERVER_PORT="$SERVICE_PORT" \
      ps_start "$JAVA17/bin/java.exe" "-Duser.timezone=Africa/Ouagadougou -jar $(win "$TOOLS/employee-soap-service.jar")" "$DATA/service.log"
    wait_port "$SERVICE_PORT" "Service SOAP" 120
  fi
  for _ in $(seq 1 30); do curl -fs "http://localhost:$SERVICE_PORT/actuator/health" | grep -q '"UP"' && break; sleep 2; done
  info "santé : $(curl -s "http://localhost:$SERVICE_PORT/actuator/health" | head -c 40)…"
}

# ---------------------------------------------------------------- première synchronisation Pentaho
first_sync() {
  say "5/7 Pentaho : synchronisation MySQL -> PostgreSQL"
  if [[ "$(pg -d employes_ref -tAc "SELECT count(*) FROM employee" | tr -d '\r')" != 0 ]]; then info "déjà faite"; return; fi
  write_env; source "$TOOLS/env-local.sh"
  ./scripts/run-sync.sh aller > "$DATA/pentaho-aller.log" 2>&1 || die "job Pentaho en échec, voir $DATA/pentaho-aller.log"
  local r; r="$(pg -d employes_ref -tAc "SELECT rows_merged||' '||rows_rejected FROM sync_run ORDER BY id DESC LIMIT 1" | tr -d '\r')"
  info "employés chargés : ${r% *}, rejetés : ${r#* }"
}

# ---------------------------------------------------------------- Netdata (droits administrateur)
setup_netdata() {
  say "6/7 Netdata"
  if [[ -n "${SKIP_NETDATA:-}" ]]; then info "ignoré (SKIP_NETDATA)"; return; fi
  local status; status="$(powershell.exe -NoProfile -Command "(Get-Service Netdata -ErrorAction SilentlyContinue).Status" | tr -d '\r')"
  if [[ -z "$status" ]]; then
    info "installation via winget : acceptez la fenêtre administrateur (UAC)"
    powershell.exe -NoProfile -Command "winget install --id Netdata.Netdata -e --silent --accept-source-agreements --accept-package-agreements" | tail -2
  fi
  local conf="$TOOLS/netdata-conf"; mkdir -p "$conf/go.d" "$conf/health.d"
  sed "s/tcp(mysql:3306)/tcp(127.0.0.1:$MYSQL_PORT)/" netdata/go.d/mysql.conf > "$conf/go.d/mysql.conf"
  sed "s/@postgres:5432/@127.0.0.1:$PG_PORT/" netdata/go.d/postgres.conf > "$conf/go.d/postgres.conf"
  sed "s#http://employee-service:8080#http://127.0.0.1:$SERVICE_PORT#" netdata/go.d/prometheus.conf > "$conf/go.d/prometheus.conf"
  cp netdata/health.d/employee_service.conf "$conf/health.d/"
  if ! cmp -s "$conf/go.d/prometheus.conf" "/c/Program Files/Netdata/etc/netdata/go.d/prometheus.conf" \
     || ! cmp -s "$conf/health.d/employee_service.conf" "/c/Program Files/Netdata/etc/netdata/health.d/employee_service.conf"; then
    cat > "$conf/copy.ps1" <<EOF
Copy-Item "$(cygpath -w "$conf")\\go.d\\*.conf" "C:\\Program Files\\Netdata\\etc\\netdata\\go.d\\" -Force
Copy-Item "$(cygpath -w "$conf")\\health.d\\*.conf" "C:\\Program Files\\Netdata\\etc\\netdata\\health.d\\" -Force
Restart-Service Netdata
EOF
    info "copie de la configuration : acceptez la fenêtre administrateur (UAC)"
    powershell.exe -NoProfile -Command "Start-Process powershell -Verb RunAs -Wait -ArgumentList '-NoProfile','-ExecutionPolicy','Bypass','-File','$(cygpath -w "$conf/copy.ps1")'"
  fi
  info "tableau de bord : http://localhost:19999"
}

# ---------------------------------------------------------------- variables pour les tests
write_env() {
  cat > "$TOOLS/env-local.sh" <<EOF
# Généré par scripts/install-windows.sh : source $TOOLS/env-local.sh avant les scripts de test
export E2E_MODE=local
export PDI_HOME="$PDI_HOME" PENTAHO_JAVA_HOME="$JRE11"
export SOAPUI_HOME="$(win "$SOAPUI_HOME")" SOAPUI_JAVA_HOME="$JRE11"
export MYSQL_CLI="$M/mysql.exe" MYSQL_PORT=$MYSQL_PORT
export PSQL="$PG_BIN/psql.exe" PG_CTL="$PG_BIN/pg_ctl.exe" PG_DATA="$(win "$DATA/pg")" PG_PORT=$PG_PORT
export SERVICE_URL="http://localhost:$SERVICE_PORT/services/EmployeeService"
unset NoDefaultCurrentDirectoryInExePath
EOF
}

status() {
  locate
  say "État"
  for c in "MySQL:$MYSQL_PORT" "PostgreSQL:$PG_PORT" "Service SOAP:$SERVICE_PORT" "Netdata:19999"; do
    printf "   %-13s port %-5s %s\n" "${c%%:*}" "${c##*:}" "$(listening "${c##*:}" && echo démarré || echo arrêté)"
  done
  cat <<EOF

   WSDL      : http://localhost:$SERVICE_PORT/services/EmployeeService?wsdl
   Santé     : http://localhost:$SERVICE_PORT/actuator/health
   Netdata   : http://localhost:19999
   Pentaho   : $(cygpath -w "$PDI_HOME")\\Spoon.bat (interface graphique)
   Tests     : source $TOOLS/env-local.sh && ./scripts/run-soapui.sh && ./tests/e2e/e2e.sh
EOF
}

stop_all() {
  locate
  say "Arrêt"
  local pid; pid="$(netstat -ano | grep LISTENING | grep -E ":$SERVICE_PORT " | awk '{print $NF}' | head -1 || true)"
  [[ -n "$pid" ]] && taskkill //PID "$pid" //F > /dev/null && info "service arrêté"
  listening "$PG_PORT" && "$PG_BIN/pg_ctl.exe" -D "$(win "$DATA/pg")" -m fast -w stop > /dev/null && info "PostgreSQL arrêté"
  listening "$MYSQL_PORT" && "$M/mysqladmin.exe" -h127.0.0.1 -P"$MYSQL_PORT" -uroot -proot_pwd shutdown 2>/dev/null && info "MySQL arrêté"
  for _ in $(seq 1 60); do   # l'arrêt de MySQL est asynchrone : attendre que mysqld libère ses fichiers
    tasklist //FI "IMAGENAME eq mysqld.exe" 2>/dev/null | grep -qi mysqld || break; sleep 1
  done
  return 0
}

case "${1:-install}" in
  install) install_tools; start_mysql; start_pg; start_service; first_sync; setup_netdata
           say "7/7 Terminé"; write_env; status ;;
  start)   locate; start_mysql; start_pg; start_service; write_env; status ;;
  stop)    stop_all ;;
  reset)   stop_all; rm -rf "$DATA/mysql" "$DATA/pg"              # bases remises à leur état initial (23 employés)
           start_mysql; start_pg; start_service; first_sync; write_env; status ;;
  status)  status ;;
  *) echo "usage : $0 [install|start|stop|reset|status]" >&2; exit 2 ;;
esac
