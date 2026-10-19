-- Fusion de la zone de transit dans la base de référence (exécutée par le job Pentaho).
-- Règle de conflit : la modification la plus récente gagne. Une ligne modifiée via SOAP
-- après la dernière modification MySQL n'est donc jamais écrasée.
INSERT INTO employee AS e (matricule, nom, prenom, email, telephone, date_naissance, date_embauche,
                           departement, poste, salaire, statut, source, updated_at)
SELECT matricule, nom, prenom, email, telephone, date_naissance, date_embauche,
       departement, poste, salaire, statut, 'MYSQL', updated_at
FROM stg_employee
ON CONFLICT (matricule) DO UPDATE SET
  nom = EXCLUDED.nom, prenom = EXCLUDED.prenom, email = EXCLUDED.email,
  telephone = EXCLUDED.telephone, date_naissance = EXCLUDED.date_naissance,
  date_embauche = EXCLUDED.date_embauche, departement = EXCLUDED.departement,
  poste = EXCLUDED.poste, salaire = EXCLUDED.salaire, statut = EXCLUDED.statut,
  source = 'MYSQL', updated_at = EXCLUDED.updated_at
WHERE e.updated_at < EXCLUDED.updated_at;
