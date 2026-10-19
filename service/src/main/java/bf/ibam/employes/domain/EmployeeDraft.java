package bf.ibam.employes.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Données saisies pour une création ou une modification. Pour une modification,
 * un champ null signifie « inchangé ».
 */
public record EmployeeDraft(
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
        String statut) {

    /** Applique les champs non nuls de {@code changes} sur l'employé existant. */
    public static EmployeeDraft merge(EmployeeRecord current, EmployeeDraft changes) {
        return new EmployeeDraft(
                current.matricule(),
                pick(changes.nom(), current.nom()),
                pick(changes.prenom(), current.prenom()),
                pick(changes.email(), current.email()),
                pick(changes.telephone(), current.telephone()),
                pick(changes.dateNaissance(), current.dateNaissance()),
                pick(changes.dateEmbauche(), current.dateEmbauche()),
                pick(changes.departement(), current.departement()),
                pick(changes.poste(), current.poste()),
                pick(changes.salaire(), current.salaire()),
                pick(changes.statut(), current.statut()));
    }

    private static <T> T pick(T value, T fallback) {
        return value != null ? value : fallback;
    }
}
