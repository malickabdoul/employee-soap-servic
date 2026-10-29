/** Service SOAP de gestion des employés (Spring Boot + Apache CXF, base de référence PostgreSQL). */

    package bf.ibam.employes;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class EmployeeServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmployeeServiceApplication.class, args);
    }
}
