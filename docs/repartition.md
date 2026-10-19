# Répartition du travail – Groupe 6 (SI)

| Membre | Responsabilité | Livrables principaux |
|---|---|---|
| NANA Abdoul Faycal | Modèle de données et environnement | `db/`, `docker-compose.yml`, données fictives, `docs/modele-employe.md` |
| OUEDRAOGO Y. Ibrahim | Intégration Pentaho | `pentaho/` (jobs aller et retour), `docs/strategie-synchronisation.md`, `pentaho/INSTALLATION.md` |
| KABORE Wend-Barka Boukary | Contrat et service SOAP | `EmployeeService.wsdl`, `EmployeeEndpoint`, `docs/erreurs-soap.md` |
| TINDANO Fernand Amed | Règles métier et tests | `EmployeeService`, `EmployeeValidator`, tests JUnit, projet SoapUI |
| KOMPAORE Abdoul Malick | Supervision et validation de bout en bout | `netdata/`, `docs/supervision-netdata.md`, `tests/e2e/e2e.sh`, coordination du README et de la présentation |

## Planning (19 – 29 octobre 2026)

| Dates | Étape |
|---|---|
| 19 – 20 oct | Modèle commun, schémas MySQL et PostgreSQL, données fictives, Docker Compose |
| 20 – 23 oct | Job Pentaho MySQL → PostgreSQL (validation, rejets, fusion) |
| 20 – 24 oct | WSDL, service CXF, trois opérations, Faults |
| 24 – 26 oct | Stratégie de retour : outbox et job Pentaho inverse |
| 24 – 26 oct | Netdata et métriques du service |
| 26 – 27 oct | JUnit, SoapUI, test de bout en bout |
| 27 – 28 oct | README, documentation, test d'installation sur une machine neuve |
| 28 – 29 oct | Présentation, répétition, envoi du lien du dépôt |
