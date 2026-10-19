# Modèle commun d'employé

Le même modèle est utilisé dans MySQL (table `employes`, base source), dans PostgreSQL (table `employee`, **base de référence**) et dans le contrat SOAP (type `Employee`).

| Champ (SQL) | Élément SOAP | Type | Règle |
|---|---|---|---|
| matricule | matricule | VARCHAR(20) | Clé métier, obligatoire, format `EMP-0000`, unique |
| nom | nom | VARCHAR(100) | Obligatoire, stocké en majuscules |
| prenom | prenom | VARCHAR(100) | Obligatoire |
| email | email | VARCHAR(150) | Obligatoire, format valide, stocké en minuscules, unique |
| telephone | telephone | VARCHAR(20) | Optionnel, 8 à 15 chiffres, `+` autorisé au début |
| date_naissance | dateNaissance | DATE | Obligatoire, au moins 18 ans |
| date_embauche | dateEmbauche | DATE | Obligatoire, pas dans le futur, après la date de naissance |
| departement | departement | VARCHAR(100) | Obligatoire, doit exister dans la table `departement` |
| poste | poste | VARCHAR(100) | Obligatoire |
| salaire | salaire | NUMERIC(12,2) | Obligatoire, strictement positif |
| statut | statut | ACTIF / SUSPENDU / SORTI | `ACTIF` par défaut à la création |
| source | source | MYSQL / SOAP | Origine de la dernière modification (non modifiable par le client) |
| updated_at | updatedAt | TIMESTAMP(3) | Date de dernière modification, sert à résoudre les conflits |

## Départements de référence

| Code | Libellé | Alias acceptés par Pentaho |
|---|---|---|
| INFO | Informatique | INFO, INFORMATIQUE, DSI |
| RH | Ressources humaines | RH, RESSOURCES HUMAINES |
| FIN | Finance | FIN, FINANCE |
| COM | Commercial | COM, COMMERCIAL |
| LOG | Logistique | LOG, LOGISTIQUE |
| DIR | Direction | DIR, DIRECTION |

Le service SOAP accepte le code ou le libellé (sans tenir compte de la casse) et enregistre toujours le libellé officiel.

## Pourquoi PostgreSQL est la base de référence

- C'est la cible de Pentaho : les données y arrivent nettoyées, normalisées et validées.
- Ses contraintes (`CHECK`, clé étrangère vers `departement`, unicité de l'email) garantissent le modèle même en cas d'erreur applicative.
- Le service SOAP lit et écrit uniquement dans PostgreSQL ; MySQL reste la source historique alimentée en retour par l'outbox.

Les règles de validation sont les mêmes dans la transformation Pentaho (`Valider regles`) et dans le service (`EmployeeValidator`).
