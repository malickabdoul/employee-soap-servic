package bf.ibam.employes.domain;

import java.util.List;

public class ValidationException extends RuntimeException {

    private final List<FieldViolation> violations;

    public ValidationException(List<FieldViolation> violations) {
        super("Données invalides : " + violations.size() + " erreur(s)");
        this.violations = List.copyOf(violations);
    }

    public List<FieldViolation> getViolations() {
        return violations;
    }
}
