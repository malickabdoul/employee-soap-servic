-- Comptes techniques (mots de passe de démonstration, à changer en production)
CREATE USER app WITH PASSWORD 'app_pwd';
GRANT SELECT, INSERT, UPDATE ON employee, employee_changes, departement TO app;
GRANT USAGE ON SEQUENCE employee_changes_id_seq TO app;

CREATE USER etl WITH PASSWORD 'etl_pwd';
GRANT SELECT, INSERT, UPDATE, DELETE, TRUNCATE ON ALL TABLES IN SCHEMA public TO etl;
GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO etl;

CREATE USER netdata WITH PASSWORD 'netdata_pwd';
GRANT pg_monitor TO netdata;
