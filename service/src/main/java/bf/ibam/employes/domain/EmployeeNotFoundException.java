package bf.ibam.employes.domain;

public class EmployeeNotFoundException extends RuntimeException {

    public EmployeeNotFoundException(String matricule) {
        super("Aucun employé avec le matricule " + matricule);
    }
}
