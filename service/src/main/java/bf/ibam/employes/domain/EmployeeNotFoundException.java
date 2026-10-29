package bf.ibam.employes.domain;

/** Aucun employé avec ce matricule. Traduite en SOAP Fault EMPLOYEE_NOT_FOUND. */
public class EmployeeNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String matricule;

    public EmployeeNotFoundException(String matricule) {
        super("Aucun employé avec le matricule " + matricule, null, false, false);
        this.matricule = matricule;
    }
    
    public String getMatricule() {
        return matricule;
    }
}
