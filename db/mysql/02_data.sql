-- Jeu de données fictives. Certaines lignes sont volontairement "sales"
-- (casse, espaces, alias de département) pour que Pentaho les normalise,
-- et trois lignes sont invalides pour alimenter la table des rejets.
USE rh_source;
SET NAMES utf8mb4;

INSERT INTO employes (matricule, nom, prenom, email, telephone, date_naissance, date_embauche, departement, poste, salaire, statut) VALUES
('EMP-0001','OUEDRAOGO','Awa','awa.ouedraogo@atser.bf','+22670112233','1990-03-12','2015-09-01','Informatique','Développeuse Java',650000.00,'ACTIF'),
('EMP-0002','kabore','  Issouf ','issouf.kabore@atser.bf','+22671223344','1988-07-25','2012-02-15','INFO','Administrateur systèmes',720000.00,'actif'),
('EMP-0003','SAWADOGO','Mariam','mariam.sawadogo@atser.bf','+22676334455','1993-11-02','2018-04-10','RH','Chargée de recrutement',480000.00,'ACTIF'),
('EMP-0004','Compaore','Boukary','boukary.compaore@atser.bf','+22678445566','1985-01-30','2010-06-01','Finance','Comptable principal',800000.00,'ACTIF'),
('EMP-0005','ZONGO','Rasmata','rasmata.zongo@atser.bf',NULL,'1995-05-18','2020-01-06','ressources humaines','Assistante RH',350000.00,'ACTIF'),
('EMP-0006','TRAORE','Seydou','seydou.traore@atser.bf','+22670556677','1987-09-09','2011-03-21','Commercial','Responsable commercial',900000.00,'ACTIF'),
('EMP-0007','ILBOUDO','Aminata','aminata.ilboudo@atser.bf','+22671667788','1992-12-24','2017-08-14','DSI','Analyste sécurité',700000.00,'ACTIF'),
('EMP-0008','NIKIEMA','Paul','paul.nikiema@atser.bf','+22675778899','1983-04-04','2008-10-01','Direction','Directeur général',1500000.00,'ACTIF'),
('EMP-0009','yameogo','Clarisse','CLARISSE.YAMEOGO@ATSER.BF','+22670889900','1996-02-14','2021-05-03','FIN','Contrôleuse de gestion',550000.00,'ACTIF'),
('EMP-0010','SOME','Didier','didier.some@atser.bf','+22676990011','1991-06-30','2016-11-07','Logistique','Responsable logistique',600000.00,'ACTIF'),
('EMP-0011','DIALLO','Fatimata','fatimata.diallo@atser.bf','+22678001122','1994-08-08','2019-02-18','Informatique','Développeuse front-end',580000.00,'ACTIF'),
('EMP-0012','BARRY','Ousmane','ousmane.barry@atser.bf',NULL,'1989-10-10','2013-07-22','commercial ','Commercial terrain',420000.00,'SUSPENDU'),
('EMP-0013','KONATE','Salimata','salimata.konate@atser.bf','+22670123987','1997-03-03','2022-09-12','RH','Gestionnaire paie',450000.00,'ACTIF'),
('EMP-0014','TAPSOBA','Hamidou','hamidou.tapsoba@atser.bf','+22671234098','1980-12-01','2005-01-10','Logistique','Magasinier',300000.00,'SORTI'),
('EMP-0015','BELEM','Josiane','josiane.belem@atser.bf','+22675345109','1998-07-19','2023-03-01','informatique','Technicienne support',380000.00,'ACTIF'),
('EMP-0016','ROUAMBA','Eric','eric.rouamba@atser.bf','+22676456210','1986-05-27','2009-09-15','Finance','Trésorier',750000.00,'ACTIF'),
('EMP-0017','SANOU','Bintou','bintou.sanou@atser.bf','+22678567321','1993-01-15','2018-12-03','Commercial','Chargée de clientèle',460000.00,'ACTIF'),
('EMP-0018','KAMBOU','Gilbert','gilbert.kambou@atser.bf','+22670678432','1990-09-21','2014-04-28','Direction','Directeur administratif',1200000.00,'ACTIF'),
('EMP-0019','OUATTARA','Rokia','rokia.ouattara@atser.bf','+22671789543','1995-11-11','2020-10-19','Informatique','Administratrice base de données',680000.00,'ACTIF'),
('EMP-0020','SOMDA','Achille','achille.somda@atser.bf','+22675890654','1984-02-29','2007-06-11','LOG','Chauffeur',250000.00,'ACTIF'),
-- Lignes invalides (rejetées par Pentaho)
('EMP-0021',NULL,'Inconnu','inconnu@atser.bf',NULL,'1990-01-01','2015-01-01','Informatique','Stagiaire',150000.00,'ACTIF'),
('EMP-0022','KINDA','Moussa','moussa.kinda-at-atser.bf',NULL,'1991-04-12','2016-03-14','Finance','Aide-comptable',320000.00,'ACTIF'),
('EMP-0023','ZERBO','Alice','alice.zerbo@atser.bf',NULL,'1992-08-30','2017-05-02','Commercial','Commerciale',-100.00,'ACTIF');
