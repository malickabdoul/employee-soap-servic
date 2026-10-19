#!/usr/bin/env bash
# Exécute le projet SoapUI en ligne de commande (image officielle SmartBear, aucune installation requise).
# Le projet peut aussi être ouvert dans SoapUI : File > Import Project > tests/soapui/EmployeeService-soapui-project.xml
set -euo pipefail
cd "$(dirname "$0")/../tests/soapui"
mkdir -p reports
docker run --rm --network host -v "$PWD:/project" -v "$PWD/reports:/reports" \
  -e COMMAND_LINE="-r -a -j -f/reports /project/EmployeeService-soapui-project.xml" \
  smartbear/soapuios-testrunner:5.10.0 | tee reports/console.log | grep -A8 "TestCaseRunner Summary"
