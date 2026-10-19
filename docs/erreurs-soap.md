# Erreurs SOAP (Fault) du service EmployeeService

Toutes les erreurs sont des SOAP 1.1 Faults déclarées dans le WSDL. L'élément `<detail>` contient un objet `ServiceFaultInfo` :

| Champ | Description |
|---|---|
| `code` | Code stable, à utiliser par les clients pour traiter l'erreur |
| `message` | Message lisible en français |
| `fieldErrors` (0..n) | Pour les erreurs de données : champ concerné (`field`) et explication (`message`) |

## Liste des erreurs

| Fault (élément detail) | code | faultcode | Opérations | Cause |
|---|---|---|---|---|
| `employeeNotFoundFault` | `EMPLOYEE_NOT_FOUND` | `soap:Client` | getEmployeeDetails, updateEmployee | Aucun employé avec ce matricule |
| `validationFault` | `VALIDATION_ERROR` | `soap:Client` | les trois | Une ou plusieurs règles du modèle non respectées (liste dans `fieldErrors`) |
| `duplicateEmployeeFault` | `DUPLICATE_EMPLOYEE` | `soap:Client` | createEmployee, updateEmployee | Matricule déjà existant, ou email déjà utilisé par un autre employé |
| `dataAccessFault` | `DATA_ACCESS_ERROR` | `soap:Server` | les trois | PostgreSQL indisponible ou erreur technique de base ; le client peut réessayer |

`soap:Client` signifie que la requête doit être corrigée ; `soap:Server` signifie que le problème vient du serveur.

## Paramètres et réponses

### getEmployeeDetails

- Entrée : `matricule` (obligatoire, format `EMP-0000`, la casse et les espaces sont tolérés).
- Sortie : `employee` (type `Employee`, tous les champs du modèle).
- Erreurs : `EMPLOYEE_NOT_FOUND`, `VALIDATION_ERROR` (matricule mal formé), `DATA_ACCESS_ERROR`.

### createEmployee

- Entrée : `employee` (type `NewEmployee`). Tous les champs sont obligatoires sauf `telephone` et `statut` (`ACTIF` par défaut).
- Traitement : normalisation (espaces, nom en majuscules, email en minuscules, libellé officiel du département), validation, insertion avec `source = SOAP`, ajout d'une ligne `CREATE` dans l'outbox, le tout dans une seule transaction.
- Sortie : l'employé créé, avec `updatedAt`.
- Erreurs : `VALIDATION_ERROR`, `DUPLICATE_EMPLOYEE`, `DATA_ACCESS_ERROR`.

### updateEmployee

- Entrée : `matricule` + `changes` (type `EmployeeChanges`, tous les champs facultatifs). Seuls les champs présents sont modifiés ; le matricule ne peut pas changer.
- Traitement : fusion avec la fiche actuelle, validation de la fiche complète, mise à jour avec `source = SOAP`, ajout d'une ligne `UPDATE` dans l'outbox.
- Sortie : l'employé modifié.
- Erreurs : `EMPLOYEE_NOT_FOUND`, `VALIDATION_ERROR`, `DUPLICATE_EMPLOYEE` (email), `DATA_ACCESS_ERROR`.

## Exemples

Employé inexistant :

```xml
<soap:Fault>
  <faultcode>soap:Client</faultcode>
  <faultstring>Aucun employé avec le matricule EMP-9999</faultstring>
  <detail>
    <employeeNotFoundFault xmlns="http://ws.employes.ibam.bf/v1">
      <code>EMPLOYEE_NOT_FOUND</code>
      <message>Aucun employé avec le matricule EMP-9999</message>
    </employeeNotFoundFault>
  </detail>
</soap:Fault>
```

Données invalides (requête `tests/requests/create-invalide.xml`) :

```xml
<soap:Fault>
  <faultcode>soap:Client</faultcode>
  <faultstring>Données invalides : 4 erreur(s)</faultstring>
  <detail>
    <validationFault xmlns="http://ws.employes.ibam.bf/v1">
      <code>VALIDATION_ERROR</code>
      <message>Données invalides : 4 erreur(s)</message>
      <fieldErrors><field>email</field><message>format d'adresse invalide</message></fieldErrors>
      <fieldErrors><field>dateNaissance</field><message>l'employé doit avoir au moins 18 ans</message></fieldErrors>
      <fieldErrors><field>departement</field><message>département inconnu : Marketing</message></fieldErrors>
      <fieldErrors><field>salaire</field><message>doit être strictement positif</message></fieldErrors>
    </validationFault>
  </detail>
</soap:Fault>
```

Base indisponible :

```xml
<soap:Fault>
  <faultcode>soap:Server</faultcode>
  <faultstring>Base de référence indisponible, réessayez plus tard</faultstring>
  <detail>
    <dataAccessFault xmlns="http://ws.employes.ibam.bf/v1">
      <code>DATA_ACCESS_ERROR</code>
      <message>Base de référence indisponible, réessayez plus tard</message>
    </dataAccessFault>
  </detail>
</soap:Fault>
```

## Où c'est implémenté

- Contrat : `service/src/main/resources/wsdl/EmployeeService.wsdl`
- Traduction exceptions → Faults : `service/src/main/java/bf/ibam/employes/ws/EmployeeEndpoint.java`
- faultcode Client/Server : `service/src/main/java/bf/ibam/employes/ws/ClientFaultCodeInterceptor.java`
