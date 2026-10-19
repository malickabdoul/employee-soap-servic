package bf.ibam.employes.repository;

import bf.ibam.employes.domain.EmployeeDraft;
import bf.ibam.employes.domain.EmployeeRecord;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Accès JDBC à la base de référence PostgreSQL. */
@Repository
public class EmployeeRepository {

    private static final String COLUMNS = "matricule, nom, prenom, email, telephone, date_naissance, date_embauche, "
            + "departement, poste, salaire, statut, source, updated_at";

    private final NamedParameterJdbcTemplate jdbc;

    public EmployeeRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<EmployeeRecord> findByMatricule(String matricule) {
        List<EmployeeRecord> rows = jdbc.query(
                "SELECT " + COLUMNS + " FROM employee WHERE matricule = :m",
                new MapSqlParameterSource("m", matricule), EmployeeRepository::map);
        return rows.stream().findFirst();
    }

    public boolean emailUsedByAnother(String email, String matricule) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM employee WHERE email = :e AND matricule <> :m",
                new MapSqlParameterSource("e", email).addValue("m", matricule), Integer.class);
        return n != null && n > 0;
    }

    /** Retourne le libellé officiel du département (recherche insensible à la casse). */
    public Optional<String> findDepartement(String libelle) {
        List<String> rows = jdbc.queryForList(
                "SELECT libelle FROM departement WHERE upper(libelle) = upper(:l) OR upper(code) = upper(:l)",
                new MapSqlParameterSource("l", libelle), String.class);
        return rows.stream().findFirst();
    }

    public void insert(EmployeeDraft e) {
        jdbc.update("INSERT INTO employee (matricule, nom, prenom, email, telephone, date_naissance, date_embauche, "
                + "departement, poste, salaire, statut, source, updated_at) VALUES (:matricule, :nom, :prenom, :email, "
                + ":telephone, :dateNaissance, :dateEmbauche, :departement, :poste, :salaire, :statut, 'SOAP', now())",
                params(e));
    }

    public void update(EmployeeDraft e) {
        jdbc.update("UPDATE employee SET nom = :nom, prenom = :prenom, email = :email, telephone = :telephone, "
                + "date_naissance = :dateNaissance, date_embauche = :dateEmbauche, departement = :departement, "
                + "poste = :poste, salaire = :salaire, statut = :statut, source = 'SOAP', updated_at = now() "
                + "WHERE matricule = :matricule", params(e));
    }

    /** Inscrit la modification dans l'outbox, lue par le job Pentaho de retour vers MySQL. */
    public void recordChange(String matricule, String operation) {
        jdbc.update("INSERT INTO employee_changes (matricule, operation) VALUES (:m, :op)",
                new MapSqlParameterSource("m", matricule).addValue("op", operation));
    }

    private static MapSqlParameterSource params(EmployeeDraft e) {
        return new MapSqlParameterSource()
                .addValue("matricule", e.matricule())
                .addValue("nom", e.nom())
                .addValue("prenom", e.prenom())
                .addValue("email", e.email())
                .addValue("telephone", e.telephone())
                .addValue("dateNaissance", e.dateNaissance())
                .addValue("dateEmbauche", e.dateEmbauche())
                .addValue("departement", e.departement())
                .addValue("poste", e.poste())
                .addValue("salaire", e.salaire())
                .addValue("statut", e.statut());
    }

    private static EmployeeRecord map(ResultSet rs, int i) throws SQLException {
        Timestamp updated = rs.getTimestamp("updated_at");
        return new EmployeeRecord(
                rs.getString("matricule"),
                rs.getString("nom"),
                rs.getString("prenom"),
                rs.getString("email"),
                rs.getString("telephone"),
                rs.getObject("date_naissance", java.time.LocalDate.class),
                rs.getObject("date_embauche", java.time.LocalDate.class),
                rs.getString("departement"),
                rs.getString("poste"),
                rs.getBigDecimal("salaire"),
                rs.getString("statut"),
                rs.getString("source"),
                updated == null ? null : updated.toLocalDateTime());
    }
}
