package bf.ibam.employes.domain;

/**
 * Levée quand un matricule ou un email existe déjà.
 * Traduite en SOAP Fault {@code DUPLICATE_EMPLOYEE} par EmployeeEndpoint ;
 * {@code field} indique le champ en conflit (« matricule » ou « email »).
 */

public class DuplicateEmployeeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String field;

    public DuplicateEmployeeException(String field, String message) {
        // Erreur métier attendue : inutile de capturer la stack trace
        super(message, null, false, false);
        // super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
