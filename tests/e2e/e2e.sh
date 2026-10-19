#!/usr/bin/env bash
# Test de bout en bout de la chaîne principale :
#   MySQL -> Pentaho -> PostgreSQL -> service SOAP -> outbox -> Pentaho -> MySQL, supervisé par Netdata.
# Prérequis : `docker compose up -d` lancé et PDI_HOME ou PDI_DOCKER_IMAGE défini (voir README).
set -uo pipefail
cd "$(dirname "$0")/../.."

URL="${SERVICE_URL:-http://localhost:8080/services/EmployeeService}"
NS="http://ws.employes.ibam.bf/v1"
PASS=0; FAIL=0
LOG="tests/e2e/derniere-execution.log"
: > "$LOG"

ok()   { echo "  [OK]    $1" | tee -a "$LOG"; PASS=$((PASS+1)); }
ko()   { echo "  [ECHEC] $1" | tee -a "$LOG"; FAIL=$((FAIL+1)); }
step() { echo; echo "== $1" | tee -a "$LOG"; }
check(){ if grep -q -- "$2" <<<"$3"; then ok "$1"; else ko "$1 (attendu : $2)"; echo "$3" >> "$LOG"; fi; }

if [[ "${E2E_MODE:-docker}" == local ]]; then
  # Installation locale sans Docker (voir docs/installation-windows.md) : clients mysql/psql et pg_ctl
  # pris dans le PATH ou via MYSQL_CLI, PSQL, PG_CTL ; PG_DATA = répertoire de données PostgreSQL.
  mysql_q() { "${MYSQL_CLI:-mysql}" -h "${MYSQL_HOST:-127.0.0.1}" -P "${MYSQL_PORT:-3306}" -uroot -proot_pwd --default-character-set=utf8mb4 rh_source -N -e "$1" 2>/dev/null | tr -d '\r'; }
  pg_q()    { PGPASSWORD=postgres_pwd "${PSQL:-psql}" -h "${PG_HOST:-localhost}" -p "${PG_PORT:-5432}" -U postgres -d employes_ref -tA -c "$1" | tr -d '\r'; }
  pg_stop()  { "${PG_CTL:-pg_ctl}" -D "$PG_DATA" -m fast -w stop; }
  pg_start() { "${PG_CTL:-pg_ctl}" -D "$PG_DATA" -l "$PG_DATA/../pg.log" -w start; }
else
  mysql_q() { docker compose exec -T mysql mysql -uroot -proot_pwd --default-character-set=utf8mb4 rh_source -N -e "$1" 2>/dev/null; }
  pg_q()    { docker compose exec -T postgres psql -U postgres -d employes_ref -tA -c "$1"; }
  pg_stop()  { docker compose stop postgres; }
  pg_start() { docker compose start postgres; }
fi
soap() {  # soap <operation> <corps XML>
  curl -s -m 10 -H 'Content-Type: text/xml;charset=UTF-8' -H "SOAPAction: \"$NS/$1\"" --data-binary @- "$URL" <<XML
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:emp="$NS">
  <soapenv:Body>$2</soapenv:Body>
</soapenv:Envelope>
XML
}
sync() { ./scripts/run-sync.sh "$1" > "tests/e2e/pentaho-$1.log" 2>&1 && grep -q "Finished!" "tests/e2e/pentaho-$1.log" && ! grep -q "Finished with errors" "tests/e2e/pentaho-$1.log"; }

MAT="EMP-0$((RANDOM % 900 + 100))"
step "0. Préparation : matricule de test $MAT"
mysql_q "DELETE FROM employes WHERE matricule='$MAT';"
pg_q "DELETE FROM employee_changes WHERE matricule='$MAT'; DELETE FROM employee WHERE matricule='$MAT';" >/dev/null
check "WSDL publié" "updateEmployee" "$(curl -s "$URL?wsdl")"

step "1. Insertion d'un employé dans MySQL (données à normaliser)"
mysql_q "INSERT INTO employes (matricule,nom,prenom,email,date_naissance,date_embauche,departement,poste,salaire,statut)
         VALUES ('$MAT','  traore ','Ali','ALI.TRAORE.$MAT@ATSER.BF','1994-04-04','2022-02-01','info','Développeur',550000,'actif');"
check "Ligne présente dans MySQL" "$MAT" "$(mysql_q "SELECT matricule FROM employes WHERE matricule='$MAT'")"

step "2. Synchronisation aller MySQL -> PostgreSQL (Pentaho)"
if sync aller; then ok "Job sync_mysql_to_pg terminé sans erreur"; else ko "Job sync_mysql_to_pg (voir tests/e2e/pentaho-aller.log)"; fi
check "Employé normalisé dans PostgreSQL" "TRAORE|Informatique|ACTIF|MYSQL" \
      "$(pg_q "SELECT nom||'|'||departement||'|'||statut||'|'||source FROM employee WHERE matricule='$MAT'")"

step "3. Lecture via SOAP : getEmployeeDetails"
R=$(soap getEmployeeDetails "<emp:getEmployeeDetailsRequest><emp:matricule>$MAT</emp:matricule></emp:getEmployeeDetailsRequest>")
check "Réponse contient le nom normalisé" "<nom>TRAORE</nom>" "$R"
check "Réponse contient l'email en minuscules" "ali.traore" "$R"

step "4. Modification via SOAP : updateEmployee"
R=$(soap updateEmployee "<emp:updateEmployeeRequest><emp:matricule>$MAT</emp:matricule><emp:changes><emp:poste>Lead développeur</emp:poste><emp:salaire>700000</emp:salaire></emp:changes></emp:updateEmployeeRequest>")
check "Poste modifié" "<poste>Lead développeur</poste>" "$R"
check "Source passée à SOAP" "<source>SOAP</source>" "$R"
check "Modification inscrite dans l'outbox" "1" "$(pg_q "SELECT count(*) FROM employee_changes WHERE matricule='$MAT' AND NOT processed")"

step "5. Synchronisation retour PostgreSQL -> MySQL (Pentaho)"
if sync retour; then ok "Job sync_pg_to_mysql terminé sans erreur"; else ko "Job sync_pg_to_mysql (voir tests/e2e/pentaho-retour.log)"; fi
check "MySQL a reçu la modification" "Lead développeur	700000.00	SOAP" \
      "$(mysql_q "SELECT poste, salaire, source FROM employes WHERE matricule='$MAT'")"
check "Outbox marquée traitée" "0" "$(pg_q "SELECT count(*) FROM employee_changes WHERE matricule='$MAT' AND NOT processed")"

step "6. Nouvelle synchronisation aller : pas de retour arrière ni de boucle"
sync aller && ok "Job relancé" || ko "Job relancé"
check "PostgreSQL garde la valeur SOAP" "Lead développeur" "$(pg_q "SELECT poste FROM employee WHERE matricule='$MAT'")"

step "7. Erreurs SOAP (Fault)"
check "Employé inexistant -> EMPLOYEE_NOT_FOUND" "EMPLOYEE_NOT_FOUND" \
      "$(soap getEmployeeDetails '<emp:getEmployeeDetailsRequest><emp:matricule>EMP-9999</emp:matricule></emp:getEmployeeDetailsRequest>')"
check "Création invalide -> VALIDATION_ERROR" "VALIDATION_ERROR" "$(soap createEmployee "$(sed -n '/<emp:createEmployeeRequest>/,/<\/emp:createEmployeeRequest>/p' tests/requests/create-invalide.xml)")"
check "Création en doublon -> DUPLICATE_EMPLOYEE" "DUPLICATE_EMPLOYEE" "$(soap createEmployee "$(sed -n '/<emp:createEmployeeRequest>/,/<\/emp:createEmployeeRequest>/p' tests/requests/create-doublon.xml)")"
pg_stop >/dev/null 2>&1
check "Base arrêtée -> DATA_ACCESS_ERROR" "DATA_ACCESS_ERROR" \
      "$(soap getEmployeeDetails '<emp:getEmployeeDetailsRequest><emp:matricule>EMP-0001</emp:matricule></emp:getEmployeeDetailsRequest>')"
pg_start >/dev/null 2>&1
R=""
for i in $(seq 1 30); do  # le pool de connexions se reconnecte tout seul
  R=$(soap getEmployeeDetails '<emp:getEmployeeDetailsRequest><emp:matricule>EMP-0001</emp:matricule></emp:getEmployeeDetailsRequest>')
  grep -q "<nom>OUEDRAOGO</nom>" <<<"$R" && break; sleep 2
done
check "Service rétabli après redémarrage de la base" "<nom>OUEDRAOGO</nom>" "$R"

step "8. Supervision Netdata"
CHARTS=$(curl -s -m 10 "http://localhost:19999/api/v1/charts")
check "Netdata collecte les métriques SOAP (cxf_server_requests)" "prometheus_employee_soap_service.cxf_server_requests" "$CHARTS"
check "Netdata collecte PostgreSQL" "postgres" "$CHARTS"
check "Netdata collecte MySQL" "mysql_rh_source" "$CHARTS"
check "Alerte de disponibilité du service chargée" "employee_soap_service_injoignable" "$(curl -s -m 10 "http://localhost:19999/api/v1/alarms?all")"

echo
echo "Résultat : $PASS réussis, $FAIL en échec" | tee -a "$LOG"
[[ $FAIL -eq 0 ]]
