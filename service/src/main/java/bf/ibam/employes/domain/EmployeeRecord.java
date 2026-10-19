package bf.ibam.employes.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Modèle commun d'employé, identique dans MySQL (source) et PostgreSQL (référence). */
public record EmployeeRecord(
        String matricule,
        String nom,
        String prenom,
        String email,
        String telephone,
        LocalDate dateNaissance,
        LocalDate dateEmbauche,
        String departement,
        String poste,
        BigDecimal salaire,
        String statut,
        String source,
        LocalDateTime updatedAt) {
}
