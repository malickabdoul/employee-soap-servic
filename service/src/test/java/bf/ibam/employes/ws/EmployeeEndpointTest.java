package bf.ibam.employes.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bf.ibam.employes.ws.generated.CreateEmployeeRequest;
import bf.ibam.employes.ws.generated.DuplicateEmployeeFault;
import bf.ibam.employes.ws.generated.Employee;
import bf.ibam.employes.ws.generated.EmployeeChanges;
import bf.ibam.employes.ws.generated.EmployeeNotFoundFault;
import bf.ibam.employes.ws.generated.EmployeePortType;
import bf.ibam.employes.ws.generated.EmployeeService;
import bf.ibam.employes.ws.generated.FieldError;
import bf.ibam.employes.ws.generated.GetEmployeeDetailsRequest;
import bf.ibam.employes.ws.generated.NewEmployee;
import bf.ibam.employes.ws.generated.Statut;
import bf.ibam.employes.ws.generated.UpdateEmployeeRequest;
import bf.ibam.employes.ws.generated.ValidationFault;
import jakarta.xml.ws.BindingProvider;
import java.math.BigDecimal;
import java.net.URL;
import javax.xml.datatype.DatatypeFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;

/** Appels SOAP réels (client JAX-WS généré depuis le WSDL) sur le service démarré avec une base H2. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class EmployeeEndpointTest {

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    EmployeePortType client;

    @BeforeEach
    void setUp() throws Exception {
        String address = "http://localhost:" + port + "/services/EmployeeService";
        client = new EmployeeService(new URL(address + "?wsdl")).getEmployeePort();
        ((BindingProvider) client).getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, address);
    }

    @Test
    void wsdlPublie() {
        String wsdl = new RestTemplate().getForObject(
                "http://localhost:" + port + "/services/EmployeeService?wsdl", String.class);
        assertThat(wsdl).contains("getEmployeeDetails", "createEmployee", "updateEmployee", "validationFault");
    }

    @Test
    void pageDeDemonstrationServieALaRacine() {
        String page = new RestTemplate().getForObject("http://localhost:" + port + "/", String.class);
        assertThat(page).contains("Service SOAP de gestion des employés", "/services/EmployeeService");
    }

    @Test
    void getEmployeeDetailsRetourneLaFiche() throws Exception {
        Employee e = client.getEmployeeDetails(get("EMP-0001")).getEmployee();
        assertThat(e.getNom()).isEqualTo("OUEDRAOGO");
        assertThat(e.getDepartement()).isEqualTo("Informatique");
        assertThat(e.getDateNaissance().toXMLFormat()).isEqualTo("1990-03-12");
    }

    @Test
    void getEmployeeDetailsInexistant() {
        assertThatThrownBy(() -> client.getEmployeeDetails(get("EMP-9999")))
                .isInstanceOf(EmployeeNotFoundFault.class)
                .satisfies(t -> assertThat(((EmployeeNotFoundFault) t).getFaultInfo().getCode())
                        .isEqualTo("EMPLOYEE_NOT_FOUND"));
    }

    @Test
    void getEmployeeDetailsMatriculeMalForme() {
        assertThatThrownBy(() -> client.getEmployeeDetails(get("123")))
                .isInstanceOf(ValidationFault.class);
    }

    @Test
    void createEmployeeEnregistreEtAlimenteOutbox() throws Exception {
        Employee created = client.createEmployee(create(newEmployee("EMP-0500", "nouveau.agent@atser.bf")))
                .getEmployee();
        assertThat(created.getNom()).isEqualTo("SANKARA");
        assertThat(created.getDepartement()).isEqualTo("Finance");
        assertThat(created.getStatut()).isEqualTo(Statut.ACTIF);
        assertThat(created.getSource()).isEqualTo("SOAP");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM employee_changes WHERE matricule = 'EMP-0500' AND operation = 'CREATE'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void createEmployeeDoublonMatriculeOuEmail() {
        assertThatThrownBy(() -> client.createEmployee(create(newEmployee("EMP-0001", "autre@atser.bf"))))
                .isInstanceOf(DuplicateEmployeeFault.class);
        assertThatThrownBy(() -> client.createEmployee(create(newEmployee("EMP-0600", "awa.ouedraogo@atser.bf"))))
                .isInstanceOf(DuplicateEmployeeFault.class);
    }

    @Test
    void createEmployeeInvalideListeLesChamps() throws Exception {
        NewEmployee n = newEmployee("EMP-0700", "pas-un-email");
        n.setSalaire(new BigDecimal("-1"));
        n.setDepartement("Marketing");
        assertThatThrownBy(() -> client.createEmployee(create(n)))
                .isInstanceOf(ValidationFault.class)
                .satisfies(t -> assertThat(((ValidationFault) t).getFaultInfo().getFieldErrors())
                        .extracting(FieldError::getField)
                        .containsExactlyInAnyOrder("email", "salaire", "departement"));
    }

    @Test
    void updateEmployeeModifieSeulementLesChampsFournis() throws Exception {
        UpdateEmployeeRequest r = new UpdateEmployeeRequest();
        r.setMatricule("EMP-0002");
        EmployeeChanges c = new EmployeeChanges();
        c.setPoste("Responsable infrastructure");
        c.setSalaire(new BigDecimal("850000.00"));
        r.setChanges(c);
        Employee e = client.updateEmployee(r).getEmployee();
        assertThat(e.getPoste()).isEqualTo("Responsable infrastructure");
        assertThat(e.getSalaire()).isEqualByComparingTo("850000");
        assertThat(e.getNom()).isEqualTo("KABORE");
        assertThat(e.getSource()).isEqualTo("SOAP");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM employee_changes WHERE matricule = 'EMP-0002' AND operation = 'UPDATE'",
                Integer.class)).isGreaterThanOrEqualTo(1);
    }

    @Test
    void updateEmployeeInexistantOuEmailDejaPris() {
        UpdateEmployeeRequest r = new UpdateEmployeeRequest();
        r.setMatricule("EMP-9999");
        r.setChanges(new EmployeeChanges());
        assertThatThrownBy(() -> client.updateEmployee(r)).isInstanceOf(EmployeeNotFoundFault.class);

        UpdateEmployeeRequest r2 = new UpdateEmployeeRequest();
        r2.setMatricule("EMP-0002");
        EmployeeChanges c = new EmployeeChanges();
        c.setEmail("awa.ouedraogo@atser.bf");
        r2.setChanges(c);
        assertThatThrownBy(() -> client.updateEmployee(r2)).isInstanceOf(DuplicateEmployeeFault.class);
    }

    private static GetEmployeeDetailsRequest get(String matricule) {
        GetEmployeeDetailsRequest r = new GetEmployeeDetailsRequest();
        r.setMatricule(matricule);
        return r;
    }

    private static CreateEmployeeRequest create(NewEmployee n) {
        CreateEmployeeRequest r = new CreateEmployeeRequest();
        r.setEmployee(n);
        return r;
    }

    private static NewEmployee newEmployee(String matricule, String email) throws Exception {
        DatatypeFactory f = DatatypeFactory.newInstance();
        NewEmployee n = new NewEmployee();
        n.setMatricule(matricule);
        n.setNom("sankara");
        n.setPrenom("Thomas");
        n.setEmail(email);
        n.setDateNaissance(f.newXMLGregorianCalendar("1992-05-10"));
        n.setDateEmbauche(f.newXMLGregorianCalendar("2024-01-15"));
        n.setDepartement("FIN");
        n.setPoste("Analyste financier");
        n.setSalaire(new BigDecimal("600000.00"));
        return n;
    }
}
