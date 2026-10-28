package bf.ibam.employes.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bf.ibam.employes.domain.DuplicateEmployeeException;
import bf.ibam.employes.domain.EmployeeDraft;
import bf.ibam.employes.domain.EmployeeNotFoundException;
import bf.ibam.employes.domain.EmployeeRecord;
import bf.ibam.employes.domain.EmployeeValidator;
import bf.ibam.employes.domain.FieldViolation;
import bf.ibam.employes.domain.ValidationException;
import bf.ibam.employes.repository.EmployeeRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Règles métier du service, testées sans base de données (dépôt simulé avec Mockito). */
class EmployeeServiceTest {

    private EmployeeRepository repository;
    private EmployeeService service;

    @BeforeEach
    void setUp() {
        repository = mock(EmployeeRepository.class);
        EmployeeValidator validator =
                new EmployeeValidator(Clock.fixed(Instant.parse("2026-10-19T00:00:00Z"), ZoneOffset.UTC));
        service = new EmployeeService(repository, validator);
        when(repository.findDepartement(anyString())).thenReturn(Optional.empty());
        when(repository.findDepartement("Informatique")).thenReturn(Optional.of("Informatique"));
        when(repository.findDepartement("INFO")).thenReturn(Optional.of("Informatique"));
    }

    private static EmployeeDraft draft(String departement, String statut) {
        return new EmployeeDraft("EMP-0100", "ZONGO", "Alice", "alice.zongo@atser.bf", "+22670000000",
                LocalDate.of(1995, 1, 1), LocalDate.of(2020, 1, 1), departement, "Analyste",
                new BigDecimal("500000.00"), statut);
    }

    private static EmployeeRecord record(String matricule, String email) {
        return new EmployeeRecord(matricule, "ZONGO", "Alice", email, "+22670000000",
                LocalDate.of(1995, 1, 1), LocalDate.of(2020, 1, 1), "Informatique", "Analyste",
                new BigDecimal("500000.00"), "ACTIF", "SOAP", null);
    }

    @Test
    void getMatriculeNormaliseAvantRecherche() {
        when(repository.findByMatricule("EMP-0001")).thenReturn(Optional.of(record("EMP-0001", "a@b.bf")));
        assertThat(service.getEmployeeDetails(" emp-0001 ").matricule()).isEqualTo("EMP-0001");
    }

    @Test
    void getMatriculeMalFormeOuInexistant() {
        assertThatThrownBy(() -> service.getEmployeeDetails("12"))
                .isInstanceOf(ValidationException.class);
        when(repository.findByMatricule("EMP-9999")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getEmployeeDetails("EMP-9999"))
                .isInstanceOf(EmployeeNotFoundException.class);
    }

    @Test
    void creationStatutParDefautEtCodeDepartementResolu() {
        when(repository.findByMatricule("EMP-0100"))
                .thenReturn(Optional.empty(), Optional.of(record("EMP-0100", "alice.zongo@atser.bf")));
        service.createEmployee(draft("INFO", null));

        ArgumentCaptor<EmployeeDraft> inserted = ArgumentCaptor.forClass(EmployeeDraft.class);
        verify(repository).insert(inserted.capture());
        assertThat(inserted.getValue().statut()).isEqualTo("ACTIF");
        assertThat(inserted.getValue().departement()).isEqualTo("Informatique");
        verify(repository).recordChange("EMP-0100", "CREATE");
    }

    @Test
    void creationRefuseeSiMatriculeOuEmailDejaPris() {
        when(repository.findByMatricule("EMP-0100")).thenReturn(Optional.of(record("EMP-0100", "x@y.bf")));
        assertThatThrownBy(() -> service.createEmployee(draft("Informatique", "ACTIF")))
                .isInstanceOf(DuplicateEmployeeException.class)
                .extracting("field").isEqualTo("matricule");

        when(repository.findByMatricule("EMP-0100")).thenReturn(Optional.empty());
        when(repository.emailUsedByAnother("alice.zongo@atser.bf", "EMP-0100")).thenReturn(true);
        assertThatThrownBy(() -> service.createEmployee(draft("Informatique", "ACTIF")))
                .isInstanceOf(DuplicateEmployeeException.class)
                .extracting("field").isEqualTo("email");
        verify(repository, never()).insert(any());
    }

    @Test
    void creationInvalideNeTouchePasLaBase() {
        assertThatThrownBy(() -> service.createEmployee(draft("Cuisine", "ACTIF")))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).getViolations())
                        .extracting(FieldViolation::field).containsExactly("departement"));
        verify(repository, never()).insert(any());
        verify(repository, never()).recordChange(anyString(), anyString());
    }

    @Test
    void miseAJourPartielleConserveLesAutresChamps() {
        when(repository.findByMatricule("EMP-0001")).thenReturn(Optional.of(record("EMP-0001", "a@b.bf")));
        EmployeeDraft changes = new EmployeeDraft(null, null, null, null, null, null, null, null,
                "Chef de projet", new BigDecimal("750000.00"), null);
        service.updateEmployee("EMP-0001", changes);

        ArgumentCaptor<EmployeeDraft> updated = ArgumentCaptor.forClass(EmployeeDraft.class);
        verify(repository).update(updated.capture());
        assertThat(updated.getValue().poste()).isEqualTo("Chef de projet");
        assertThat(updated.getValue().salaire()).isEqualByComparingTo("750000");
        assertThat(updated.getValue().nom()).isEqualTo("ZONGO");
        assertThat(updated.getValue().email()).isEqualTo("a@b.bf");
        verify(repository).recordChange("EMP-0001", "UPDATE");
    }

    @Test
    void miseAJourRefuseeSiEmailDejaUtilise() {
        when(repository.findByMatricule("EMP-0001")).thenReturn(Optional.of(record("EMP-0001", "a@b.bf")));
        when(repository.emailUsedByAnother("pris@atser.bf", "EMP-0001")).thenReturn(true);
        EmployeeDraft changes = new EmployeeDraft(null, null, null, " PRIS@atser.bf ", null, null, null, null,
                null, null, null);
        assertThatThrownBy(() -> service.updateEmployee("EMP-0001", changes))
                .isInstanceOf(DuplicateEmployeeException.class)
                .extracting("field").isEqualTo("email");
        verify(repository, never()).update(any());
    }
}
