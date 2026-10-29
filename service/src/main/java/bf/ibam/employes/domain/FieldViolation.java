package bf.ibam.employes.domain;

/** Une erreur sur un champ ; renvoyée au client dans {@code fieldErrors} de la SOAP Fault. */
public record FieldViolation(String field, String message) {
}
