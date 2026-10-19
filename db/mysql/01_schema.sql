-- Base source (historique RH) : MySQL 8
CREATE DATABASE IF NOT EXISTS rh_source CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE rh_source;

CREATE TABLE IF NOT EXISTS employes (
  id              INT AUTO_INCREMENT PRIMARY KEY,
  matricule       VARCHAR(20)  NOT NULL,
  nom             VARCHAR(100),
  prenom          VARCHAR(100),
  email           VARCHAR(150),
  telephone       VARCHAR(20),
  date_naissance  DATE,
  date_embauche   DATE,
  departement     VARCHAR(100),
  poste           VARCHAR(100),
  salaire         DECIMAL(12,2),
  statut          VARCHAR(20)  DEFAULT 'ACTIF',
  source          VARCHAR(10)  NOT NULL DEFAULT 'MYSQL',
  updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  CONSTRAINT uk_employes_matricule UNIQUE (matricule)
) ENGINE=InnoDB;

-- Utilisateur en lecture/écriture pour Pentaho (synchro aller et retour)
CREATE USER IF NOT EXISTS 'etl'@'%' IDENTIFIED WITH mysql_native_password BY 'etl_pwd';
GRANT SELECT, INSERT, UPDATE ON rh_source.* TO 'etl'@'%';

-- Utilisateur de supervision pour Netdata
CREATE USER IF NOT EXISTS 'netdata'@'%' IDENTIFIED WITH mysql_native_password BY 'netdata_pwd';
GRANT USAGE, REPLICATION CLIENT, PROCESS ON *.* TO 'netdata'@'%';
FLUSH PRIVILEGES;
