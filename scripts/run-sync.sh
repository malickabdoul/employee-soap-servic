#!/usr/bin/env bash
# Lance un job Pentaho :  ./scripts/run-sync.sh aller | retour
#   aller  : MySQL -> PostgreSQL (sync_mysql_to_pg.kjb)
#   retour : outbox PostgreSQL -> MySQL (sync_pg_to_mysql.kjb)
#
# Deux modes :
#   - PDI installé localement : export PDI_HOME=/chemin/vers/data-integration
#   - PDI dans un conteneur    : export PDI_DOCKER_IMAGE=<image contenant /opt/data-integration>
set -euo pipefail
cd "$(dirname "$0")/.."
case "${1:-aller}" in
  aller)  JOB=sync_mysql_to_pg.kjb ;;
  retour) JOB=sync_pg_to_mysql.kjb ;;
  *) echo "usage: $0 aller|retour" >&2; exit 2 ;;
esac
TZ_OPT="-Duser.timezone=Africa/Ouagadougou"   # même fuseau que les bases (voir docs/strategie-synchronisation.md)

if [[ -n "${PDI_HOME:-}" && "$(uname -s)" =~ ^(MINGW|MSYS|CYGWIN) ]]; then
  # Windows (Git Bash) : kitchen.sh refuse cet OS et Kitchen.bat gère mal les chemins avec espaces,
  # on lance donc directement le lanceur Java de PDI, comme le fait Kitchen.bat.
  JAVA_BIN="${PENTAHO_JAVA_HOME:+$PENTAHO_JAVA_HOME/bin/}java"
  JOB_FILE="$(cygpath -w "$PWD/pentaho/$JOB")"
  cd "$PDI_HOME"
  "$JAVA_BIN" --add-opens java.base/java.net=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED \
    --add-opens java.base/sun.net.www.protocol.jar=ALL-UNNAMED -Xms256m -Xmx1024m $TZ_OPT \
    -Dfile.encoding=UTF-8 -Djava.locale.providers=COMPAT,SPI -jar launcher/launcher.jar \
    -main org.pentaho.di.kitchen.Kitchen -initialDir "$(cygpath -w "$PDI_HOME")" \
    -file="$JOB_FILE" -level="${PDI_LOG_LEVEL:-Basic}" \
    -param:MYSQL_HOST="${MYSQL_HOST:-localhost}" -param:MYSQL_PORT="${MYSQL_PORT:-3306}" \
    -param:PG_HOST="${PG_HOST:-localhost}" -param:PG_PORT="${PG_PORT:-5432}"
elif [[ -n "${PDI_HOME:-}" ]]; then
  export PENTAHO_DI_JAVA_OPTIONS="-Xms256m -Xmx1024m $TZ_OPT"
  "$PDI_HOME/kitchen.sh" -file="$PWD/pentaho/$JOB" -level="${PDI_LOG_LEVEL:-Basic}" \
    -param:MYSQL_HOST="${MYSQL_HOST:-localhost}" -param:MYSQL_PORT="${MYSQL_PORT:-3306}" \
    -param:PG_HOST="${PG_HOST:-localhost}" -param:PG_PORT="${PG_PORT:-5432}"
elif [[ -n "${PDI_DOCKER_IMAGE:-}" ]]; then
  docker run --rm --network employee-soap_default -e TZ=Africa/Ouagadougou \
    -e PENTAHO_DI_JAVA_OPTIONS="-Xmx512m $TZ_OPT" -v "$PWD/pentaho:/jobs:ro" \
    --entrypoint /opt/data-integration/kitchen.sh "$PDI_DOCKER_IMAGE" \
    -file="/jobs/$JOB" -level="${PDI_LOG_LEVEL:-Basic}" -param:MYSQL_HOST=mysql -param:PG_HOST=postgres
else
  echo "Définir PDI_HOME (installation locale) ou PDI_DOCKER_IMAGE (voir pentaho/INSTALLATION.md)" >&2
  exit 2
fi
