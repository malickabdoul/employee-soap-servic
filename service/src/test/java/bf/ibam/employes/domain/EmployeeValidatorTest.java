package bf.ibam.employes.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class EmployeeValidatorTest {

    private final EmployeeValidator validator =
            new EmployeeValidator(Clock.fixed(Instant.parse("2026-10-19T00:00:00Z"), ZoneOffset.UTC));

    private EmployeeDraft valid() {
        return new EmployeeDraft("EMP-0100", "ZONGO", "Alice", "alice.zongo@atser.bf", "+22670000000",
                LocalDate.of(1995, 1, 1), LocalDate.of(2020, 1, 1), "Informatique", "Analyste",
                new BigDecimal("500000.00"), "ACTIF");
    }

    private List<String> fields(EmployeeDraft d, boolean depOk) {
        return validator.validate(d, depOk).stream().map(FieldViolation::field).toList();
    }

    @Test
    void employeValideSansErreur() {
        assertThat(validator.validate(valid(), true)).isEmpty();
    }

    @Test
    void champsObligatoiresManquants() {
        EmployeeDraft d = new EmployeeDraft(null, " ", null, null, null, null, null, null, null, null, null);
        assertThat(fields(d, false)).containsExactlyInAnyOrder("matricule", "nom", "prenom", "email",
                "dateNaissance", "dateEmbauche", "departement", "poste", "salaire", "statut");
    }

    @Test
    void formatsInvalides() {
        EmployeeDraft d = new EmployeeDraft("E1", "ZONGO", "Alice", "alice-at-atser.bf", "12ab",
                LocalDate.of(1995, 1, 1), LocalDate.of(2020, 1, 1), "Informatique", "Analyste",
                new BigDecimal("-5"), "EN_CONGE");
        assertThat(fields(d, true)).containsExactlyInAnyOrder("matricule", "email", "telephone", "salaire", "statut");
    }

    @Test
    void regleDesDates() {
        EmployeeDraft mineur = new EmployeeDraft("EMP-0100", "Z", "A", "a@b.bf", null,
                LocalDate.of(2010, 1, 1), LocalDate.of(2026, 1, 1), "Informatique", "Stagiaire",
                BigDecimal.TEN, "ACTIF");
        assertThat(fields(mineur, true)).containsExactly("dateNaissance");

        EmployeeDraft futur = new EmployeeDraft("EMP-0100", "Z", "A", "a@b.bf", null,
                LocalDate.of(1990, 1, 1), LocalDate.of(2027, 1, 1), "Informatique", "Analyste",
                BigDecimal.TEN, "ACTIF");
        assertThat(fields(futur, true)).containsExactly("dateEmbauche");
    }

    @Test
    void departementInconnu() {
        assertThat(fields(valid(), false)).containsExactly("departement");
    }

    @Test
    void normalisationCommePentaho() {
        EmployeeDraft n = EmployeeValidator.normalize(new EmployeeDraft(" emp-0100 ", " zongo ", " Alice ",
                " Alice.Zongo@ATSER.bf ", null, null, null, " Informatique ", " Analyste ", null, "actif"));
        assertThat(n.matricule()).isEqualTo("EMP-0100");
        assertThat(n.nom()).isEqualTo("ZONGO");
        assertThat(n.prenom()).isEqualTo("Alice");
        assertThat(n.email()).isEqualTo("alice.zongo@atser.bf");
        assertThat(n.statut()).isEqualTo("ACTIF");
    }
}
