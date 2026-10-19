#!/usr/bin/env bash
# Exécute le projet SoapUI en ligne de commande.
#   - par défaut : image officielle SmartBear (Docker, aucune installation requise) ;
#   - si SOAPUI_HOME est défini : SoapUI installé localement (ex. SoapUI-5.7.2), utile sans Docker.
# Le projet peut aussi être ouvert dans SoapUI : File > Import Project > tests/soapui/EmployeeService-soapui-project.xml
set -euo pipefail
cd "$(dirname "$0")/../tests/soapui"
mkdir -p reports
if [[ -n "${SOAPUI_HOME:-}" ]]; then
  SEP=":"; [[ "$(uname -s)" =~ ^(MINGW|MSYS|CYGWIN) ]] && SEP=";"
  JAVA_BIN="${SOAPUI_JAVA_HOME:+$SOAPUI_JAVA_HOME/bin/}java"
  "$JAVA_BIN" -Xmx1024m -Dsoapui.home="$SOAPUI_HOME/bin" -Dlog4j2.formatMsgNoLookups=true \
    -cp "$SOAPUI_HOME/bin/soapui-5.7.2.jar$SEP$SOAPUI_HOME/lib/*" com.eviware.soapui.tools.SoapUITestCaseRunner \
    -r -a -j -freports EmployeeService-soapui-project.xml 2>&1 | tee reports/console.log | grep -A8 "TestCaseRunner Summary"
else
  docker run --rm --network host -v "$PWD:/project" -v "$PWD/reports:/reports" \
    -e COMMAND_LINE="-r -a -j -f/reports /project/EmployeeService-soapui-project.xml" \
    smartbear/soapuios-testrunner:5.10.0 | tee reports/console.log | grep -A8 "TestCaseRunner Summary"
fi
