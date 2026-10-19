# Pentaho Data Integration : installation et exécution

Pentaho Data Integration (PDI, aussi appelé Kettle) ne fait pas partie du Docker Compose : il s'installe à part, comme le demande l'énoncé pour les outils à installation particulière.

## 1. Prérequis

- Java 11 ou 17 (PDI 9.4 / 10.x). Vérifier avec `java -version`.
- Les bases démarrées : `docker compose up -d mysql postgres` depuis la racine du dépôt.

## 2. Installer PDI Community Edition

1. Télécharger **Pentaho Data Integration Community Edition** (9.4 ou plus récent) depuis le site de Hitachi Vantara (section *Pentaho Community Edition*, un formulaire d'inscription gratuit peut être demandé).
2. Dézipper l'archive, par exemple dans `C:\pentaho\data-integration` ou `~/pentaho/data-integration`.
3. **Pilote MySQL** : télécharger *MySQL Connector/J 8.0.x* (fichier `mysql-connector-j-8.0.33.jar`) depuis dev.mysql.com et le copier dans `data-integration/lib/`. Le pilote PostgreSQL est déjà fourni.
4. Définir la variable `PDI_HOME` vers ce dossier :
   - Linux/macOS : `export PDI_HOME=~/pentaho/data-integration`
   - Windows (PowerShell) : `$env:PDI_HOME="C:\pentaho\data-integration"`

## 3. Fichiers du dossier

| Fichier | Rôle |
|---|---|
| `sync_mysql_to_pg.kjb` | Job aller : vide la zone de transit, lance `mysql_to_pg.ktr`, fusionne dans `employee`, journalise l'exécution |
| `mysql_to_pg.ktr` | Lecture MySQL → nettoyage des textes → normalisation des départements → validation → transit ou rejets |
| `sync_pg_to_mysql.kjb` | Job retour : lance `pg_to_mysql.ktr` puis journalise l'exécution |
| `pg_to_mysql.ktr` | Lecture de l'outbox `employee_changes` → Insert/Update MySQL → marque la ligne traitée |

Les connexions `mysql_source` et `pg_reference` utilisent des paramètres (`MYSQL_HOST`, `PG_HOST`, ports, utilisateurs, mots de passe) avec des valeurs par défaut pour `localhost`. La connexion MySQL est de type *Generic* avec le pilote `com.mysql.cj.jdbc.Driver`, car le type *MySQL* natif de PDI 9 attend l'ancien pilote 5.1.

## 4. Exécuter

En ligne de commande (recommandé, reproductible) :

```bash
./scripts/run-sync.sh aller    # MySQL -> PostgreSQL
./scripts/run-sync.sh retour   # PostgreSQL (outbox) -> MySQL
```

Sous Windows sans bash :

```bat
%PDI_HOME%\Kitchen.bat /file:pentaho\sync_mysql_to_pg.kjb /level:Basic
%PDI_HOME%\Kitchen.bat /file:pentaho\sync_pg_to_mysql.kjb /level:Basic
```

Dans l'interface graphique : lancer `spoon.sh` (ou `Spoon.bat`), *File > Open*, choisir le `.kjb`, puis *Run*.

### Variante sans installation locale

Si une image Docker contenant PDI dans `/opt/data-integration` est disponible, `run-sync.sh` peut l'utiliser :

```bash
export PDI_DOCKER_IMAGE=<image-pdi>
./scripts/run-sync.sh aller
```

Les tests de ce dépôt ont été exécutés ainsi avec PDI CE 9.0 (image communautaire `basisti/pentaho-pdi:ce-9.0.0.0-423-jdbc`).

## 5. Fuseau horaire

La règle « la modification la plus récente gagne » compare des dates venant des deux bases. PDI doit donc tourner dans le même fuseau que les bases (Africa/Ouagadougou, soit UTC). `run-sync.sh` passe `-Duser.timezone=Africa/Ouagadougou` à la JVM ; si vous lancez Spoon à la main, ajoutez cette option à `PENTAHO_DI_JAVA_OPTIONS`.

## 6. Planification

Pour une synchronisation périodique, planifier les deux commandes (cron sous Linux, Planificateur de tâches sous Windows), par exemple toutes les 5 minutes :

```cron
*/5 * * * * cd /chemin/employee-soap-service && PDI_HOME=/opt/pdi ./scripts/run-sync.sh aller && ./scripts/run-sync.sh retour
```
