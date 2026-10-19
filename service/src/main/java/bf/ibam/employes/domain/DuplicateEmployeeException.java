package bf.ibam.employes.domain;

public class DuplicateEmployeeException extends RuntimeException {

    private final String field;

    public DuplicateEmployeeException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
