package bf.ibam.employes.service;

import bf.ibam.employes.domain.DuplicateEmployeeException;
import bf.ibam.employes.domain.EmployeeDraft;
import bf.ibam.employes.domain.EmployeeNotFoundException;
import bf.ibam.employes.domain.EmployeeRecord;
import bf.ibam.employes.domain.EmployeeValidator;
import bf.ibam.employes.domain.FieldViolation;
import bf.ibam.employes.domain.ValidationException;
import bf.ibam.employes.repository.EmployeeRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Règles métier des trois opérations du service. */
@Service
public class EmployeeService {

    private final EmployeeRepository repository;
    private final EmployeeValidator validator;

    public EmployeeService(EmployeeRepository repository, EmployeeValidator validator) {
        this.repository = repository;
        this.validator = validator;
    }

    @Transactional(readOnly = true)
    public EmployeeRecord getEmployeeDetails(String matricule) {
        String m = requireMatricule(matricule);
        return repository.findByMatricule(m).orElseThrow(() -> new EmployeeNotFoundException(m));
    }

    @Transactional
    public EmployeeRecord createEmployee(EmployeeDraft input) {
        EmployeeDraft draft = EmployeeValidator.normalize(input);
        if (draft.statut() == null) {
            draft = withStatut(draft, "ACTIF");
        }
        EmployeeDraft e = resolveDepartement(draft);
        check(e, repository.findDepartement(nullToEmpty(e.departement())).isPresent());

        if (repository.findByMatricule(e.matricule()).isPresent()) {
            throw new DuplicateEmployeeException("matricule", "Le matricule " + e.matricule() + " existe déjà");
        }
        if (repository.emailUsedByAnother(e.email(), e.matricule())) {
            throw new DuplicateEmployeeException("email", "L'email " + e.email() + " est déjà utilisé");
        }
        try {
            repository.insert(e);
        } catch (DuplicateKeyException ex) {
            // Création concurrente du même matricule ou du même email
            throw new DuplicateEmployeeException("matricule", "Employé déjà existant (matricule ou email)");
        }
        repository.recordChange(e.matricule(), "CREATE");
        return repository.findByMatricule(e.matricule()).orElseThrow();
    }

    @Transactional
    public EmployeeRecord updateEmployee(String matricule, EmployeeDraft changes) {
        String m = requireMatricule(matricule);
        EmployeeRecord current = repository.findByMatricule(m).orElseThrow(() -> new EmployeeNotFoundException(m));

        EmployeeDraft merged = resolveDepartement(
                EmployeeValidator.normalize(EmployeeDraft.merge(current, EmployeeValidator.normalize(changes))));
        check(merged, repository.findDepartement(nullToEmpty(merged.departement())).isPresent());

        if (repository.emailUsedByAnother(merged.email(), m)) {
            throw new DuplicateEmployeeException("email", "L'email " + merged.email() + " est déjà utilisé");
        }
        try {
            repository.update(merged);
        } catch (DuplicateKeyException ex) {
            throw new DuplicateEmployeeException("email", "L'email " + merged.email() + " est déjà utilisé");
        }
        repository.recordChange(m, "UPDATE");
        return repository.findByMatricule(m).orElseThrow();
    }

    private String requireMatricule(String matricule) {
        String m = matricule == null ? "" : matricule.trim().toUpperCase();
        if (!EmployeeValidator.MATRICULE.matcher(m).matches()) {
            throw new ValidationException(List.of(new FieldViolation("matricule", "format attendu EMP-0000")));
        }
        return m;
    }

    private void check(EmployeeDraft e, boolean departementConnu) {
        List<FieldViolation> errors = validator.validate(e, departementConnu);
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    /** Remplace un code ou un libellé de département par son libellé officiel. */
    private EmployeeDraft resolveDepartement(EmployeeDraft e) {
        Optional<String> libelle = e.departement() == null ? Optional.empty() : repository.findDepartement(e.departement());
        if (libelle.isEmpty()) {
            return e;
        }
        return new EmployeeDraft(e.matricule(), e.nom(), e.prenom(), e.email(), e.telephone(), e.dateNaissance(),
                e.dateEmbauche(), libelle.get(), e.poste(), e.salaire(), e.statut());
    }

    private static EmployeeDraft withStatut(EmployeeDraft e, String statut) {
        return new EmployeeDraft(e.matricule(), e.nom(), e.prenom(), e.email(), e.telephone(), e.dateNaissance(),
                e.dateEmbauche(), e.departement(), e.poste(), e.salaire(), statut);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
