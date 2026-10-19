# Tests et résultats attendus

| Niveau | Commande | Résultat attendu | Résultat obtenu |
|---|---|---|---|
| Unitaires + intégration SOAP (JUnit) | `cd service && mvn test` | 15 tests, 0 échec | 15 / 15 |
| SoapUI | `./scripts/run-soapui.sh` (ou ouvrir le projet dans SoapUI) | 4 cas, 12 requêtes, 54 assertions, 0 échec | 54 / 54 ([résumé](soapui/resultats/resume.txt)) |
| Bout en bout | `./tests/e2e/e2e.sh` | 23 vérifications réussies | 23 / 23 ([journal](e2e/resultat-reference.log)) |

## JUnit (`service/src/test`)

- `EmployeeValidatorTest` (6 tests) : employé valide, champs obligatoires, formats (matricule, email, téléphone, salaire, statut), âge minimum et date d'embauche, département inconnu, normalisation.
- `EmployeeEndpointTest` (9 tests) : démarre le service avec une base H2 et l'appelle via un vrai client SOAP généré depuis le WSDL : WSDL publié, consultation, employé inexistant, matricule mal formé, création + outbox, doublons de matricule et d'email, création invalide avec la liste des champs, modification partielle + outbox, modification d'un inexistant et email déjà pris.

## SoapUI (`tests/soapui/EmployeeService-soapui-project.xml`)

| Cas de test | Requêtes | Vérifications |
|---|---|---|
| 1 - Consultation getEmployeeDetails | employé existant, inexistant, matricule mal formé | conformité au WSDL, valeurs, `EMPLOYEE_NOT_FOUND`, `soap:Client`, `VALIDATION_ERROR` |
| 2 - Création puis modification | createEmployee, updateEmployee partiel, relecture | normalisation (nom, département), `source = SOAP`, seuls les champs fournis changent |
| 3 - Erreurs de création | données invalides, matricule existant, email utilisé | 4 champs en erreur listés, `DUPLICATE_EMPLOYEE` |
| 4 - Erreurs de modification | inexistant, email pris, salaire nul | `EMPLOYEE_NOT_FOUND`, `DUPLICATE_EMPLOYEE`, `VALIDATION_ERROR` |

Les cas 2 et 3 tirent un matricule au hasard à chaque exécution (script de démarrage du cas), le projet peut donc être relancé sans nettoyer la base.

Le cas « base indisponible » se teste à la main dans SoapUI : `docker compose stop postgres`, envoyer `getEmployeeDetails`, constater `DATA_ACCESS_ERROR`, puis `docker compose start postgres`. Il est automatisé dans le test de bout en bout.

## Bout en bout (`tests/e2e/e2e.sh`)

1. Insertion dans MySQL d'un employé avec des données à normaliser (`'  traore '`, `'info'`, email en majuscules, `'actif'`).
2. Job Pentaho aller : la fiche arrive normalisée dans PostgreSQL (`TRAORE`, `Informatique`, `ACTIF`).
3. `getEmployeeDetails` renvoie la fiche.
4. `updateEmployee` change le poste et le salaire, `source` passe à `SOAP`, une ligne apparaît dans l'outbox.
5. Job Pentaho retour : MySQL reçoit la modification, l'outbox est marquée traitée.
6. Nouveau job aller : PostgreSQL garde la valeur SOAP (pas de retour arrière, pas de boucle).
7. Faults : inexistant, invalide, doublon, puis PostgreSQL arrêté → `DATA_ACCESS_ERROR`, et reprise automatique après redémarrage.
8. Netdata collecte les métriques SOAP, PostgreSQL et MySQL, et l'alerte de disponibilité est chargée.

Résultats attendus de la synchronisation sur le jeu de données initial : **20 employés chargés, 3 rejetés** (`EMP-0021` nom manquant, `EMP-0022` email invalide, `EMP-0023` salaire négatif).
