-- Base de référence : PostgreSQL 16 (lue et écrite par le service SOAP)
CREATE TABLE IF NOT EXISTS departement (
  code     VARCHAR(10)  PRIMARY KEY,
  libelle  VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS employee (
  matricule       VARCHAR(20)   PRIMARY KEY,
  nom             VARCHAR(100)  NOT NULL,
  prenom          VARCHAR(100)  NOT NULL,
  email           VARCHAR(150)  NOT NULL UNIQUE,
  telephone       VARCHAR(20),
  date_naissance  DATE          NOT NULL,
  date_embauche   DATE          NOT NULL,
  departement     VARCHAR(100)  NOT NULL REFERENCES departement(libelle),
  poste           VARCHAR(100)  NOT NULL,
  salaire         NUMERIC(12,2) NOT NULL CHECK (salaire > 0),
  statut          VARCHAR(10)   NOT NULL CHECK (statut IN ('ACTIF','SUSPENDU','SORTI')),
  source          VARCHAR(10)   NOT NULL DEFAULT 'MYSQL' CHECK (source IN ('MYSQL','SOAP')),
  updated_at      TIMESTAMP(3)  NOT NULL DEFAULT now()
);

-- Zone de transit remplie par Pentaho avant la fusion dans employee
CREATE TABLE IF NOT EXISTS stg_employee (
  matricule VARCHAR(20), nom VARCHAR(100), prenom VARCHAR(100), email VARCHAR(150),
  telephone VARCHAR(20), date_naissance DATE, date_embauche DATE, departement VARCHAR(100),
  poste VARCHAR(100), salaire NUMERIC(12,2), statut VARCHAR(10), source VARCHAR(10),
  updated_at TIMESTAMP(3)
);

-- Lignes rejetées par la synchronisation MySQL -> PostgreSQL
CREATE TABLE IF NOT EXISTS sync_reject (
  id          BIGSERIAL PRIMARY KEY,
  matricule   VARCHAR(20),
  nom         VARCHAR(100),
  email       VARCHAR(150),
  salaire     NUMERIC(12,2),
  departement VARCHAR(100),
  motif       VARCHAR(255),
  rejected_at TIMESTAMP(3) NOT NULL DEFAULT now()
);

-- Outbox : modifications faites via SOAP, à répercuter vers MySQL
CREATE TABLE IF NOT EXISTS employee_changes (
  id           BIGSERIAL PRIMARY KEY,
  matricule    VARCHAR(20) NOT NULL REFERENCES employee(matricule),
  operation    VARCHAR(10) NOT NULL CHECK (operation IN ('CREATE','UPDATE')),
  changed_at   TIMESTAMP(3) NOT NULL DEFAULT now(),
  processed    BOOLEAN NOT NULL DEFAULT FALSE,
  processed_at TIMESTAMP(3)
);
CREATE INDEX IF NOT EXISTS idx_changes_pending ON employee_changes(processed) WHERE processed = FALSE;

-- Journal des exécutions de synchronisation
CREATE TABLE IF NOT EXISTS sync_run (
  id          BIGSERIAL PRIMARY KEY,
  direction   VARCHAR(20) NOT NULL,
  started_at  TIMESTAMP(3) NOT NULL DEFAULT now(),
  rows_merged INT,
  rows_rejected INT
);

INSERT INTO departement(code, libelle) VALUES
 ('INFO','Informatique'), ('RH','Ressources humaines'), ('FIN','Finance'),
 ('COM','Commercial'), ('LOG','Logistique'), ('DIR','Direction')
ON CONFLICT DO NOTHING;
