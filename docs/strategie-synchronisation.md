# Stratégie de synchronisation et de retour vers MySQL

## Aller : MySQL → PostgreSQL (job `sync_mysql_to_pg.kjb`)

1. **Vider la zone de transit** `stg_employee`.
2. **Transformation `mysql_to_pg.ktr`** :
   - *Lire employes MySQL* : lecture complète de la table `employes`.
   - *Nettoyer textes* : suppression des espaces, nom et statut en majuscules, email en minuscules.
   - *Normaliser departement* : alias (`INFO`, `DSI`, `ressources humaines`…) → libellé officiel.
   - *Valider regles* : mêmes règles que le service SOAP ; produit un champ `motif`.
   - *Ligne valide ?* : les lignes valides vont dans `stg_employee`, les autres dans `sync_reject` avec leur motif.
3. **Fusion** (`db/postgres/03_merge.sql`) : `INSERT … ON CONFLICT (matricule) DO UPDATE … WHERE employee.updated_at < EXCLUDED.updated_at`.
4. **Journalisation** dans `sync_run` (lignes fusionnées et rejetées).

Le job est **idempotent** : le relancer sans changement dans MySQL ne modifie rien.

Résultat sur le jeu de données fourni : 20 employés chargés, 3 rejetés (`nom obligatoire`, `email invalide`, `salaire doit etre > 0`).

## Retour : PostgreSQL → MySQL (job `sync_pg_to_mysql.kjb`)

Le service SOAP écrit dans PostgreSQL. Pour répercuter ces modifications vers MySQL, on utilise le patron **outbox transactionnelle** :

1. À chaque `createEmployee` ou `updateEmployee`, le service insère une ligne dans `employee_changes` (`matricule`, `operation`, `processed = false`) **dans la même transaction** que la modification. Une modification ne peut donc pas être perdue ni enregistrée sans son événement.
2. La transformation `pg_to_mysql.ktr` lit les lignes non traitées (jointes à la fiche actuelle de l'employé), fait un **Insert/Update** dans MySQL sur la clé `matricule`, puis marque chaque ligne `processed = true`.
3. MySQL reçoit aussi `source = 'SOAP'` et la même valeur de `updated_at` que PostgreSQL.

## Éviter les boucles et les conflits

- **Pas de boucle** : comme MySQL reçoit exactement le `updated_at` de PostgreSQL, la synchro aller suivante voit des dates égales et ne réécrit pas la ligne (condition `<` stricte).
- **Règle de conflit : la modification la plus récente gagne.** Si une fiche est modifiée via SOAP puis l'ancienne valeur MySQL est relue, PostgreSQL n'est pas écrasé (vérifié par l'étape 6 du test de bout en bout).
- Si la même fiche est modifiée dans MySQL **après** une modification SOAP, la synchro aller applique la valeur MySQL, plus récente.
- **Reprise** : une ligne d'outbox n'est marquée traitée qu'après l'écriture dans MySQL. Si le job échoue (MySQL arrêté), il suffit de le relancer ; Insert/Update est sans effet de bord en cas de rejeu.
- **Fuseau horaire** : PDI, les bases et le service utilisent tous Africa/Ouagadougou (UTC), sinon la comparaison des dates serait faussée.

## Limites connues

- La synchro est périodique (planification cron), pas en temps réel.
- La lecture MySQL est complète à chaque exécution ; pour de gros volumes, on filtrerait sur `updated_at > dernière exécution` (table `sync_run`).
- Les suppressions ne sont pas gérées : un employé qui part passe au statut `SORTI`, conformément aux opérations demandées (pas de `deleteEmployee`).
- Les lignes rejetées sont réinscrites dans `sync_reject` à chaque exécution tant qu'elles ne sont pas corrigées dans MySQL.
