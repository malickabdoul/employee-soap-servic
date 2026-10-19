package bf.ibam.employes.ws;

import bf.ibam.employes.domain.DuplicateEmployeeException;
import bf.ibam.employes.domain.EmployeeNotFoundException;
import bf.ibam.employes.domain.FieldViolation;
import bf.ibam.employes.domain.ValidationException;
import bf.ibam.employes.service.EmployeeService;
import bf.ibam.employes.ws.generated.CreateEmployeeRequest;
import bf.ibam.employes.ws.generated.CreateEmployeeResponse;
import bf.ibam.employes.ws.generated.DataAccessFault;
import bf.ibam.employes.ws.generated.DuplicateEmployeeFault;
import bf.ibam.employes.ws.generated.EmployeeNotFoundFault;
import bf.ibam.employes.ws.generated.EmployeePortType;
import bf.ibam.employes.ws.generated.GetEmployeeDetailsRequest;
import bf.ibam.employes.ws.generated.GetEmployeeDetailsResponse;
import bf.ibam.employes.ws.generated.UpdateEmployeeRequest;
import bf.ibam.employes.ws.generated.UpdateEmployeeResponse;
import bf.ibam.employes.ws.generated.ValidationFault;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;

/**
 * Implémentation du portType généré depuis le WSDL. Traduit les exceptions métier
 * en SOAP Faults déclarées dans le contrat.
 */
@Component
public class EmployeeEndpoint implements EmployeePortType {

    private static final Logger log = LoggerFactory.getLogger(EmployeeEndpoint.class);

    private final EmployeeService service;

    public EmployeeEndpoint(EmployeeService service) {
        this.service = service;
    }

    @Override
    public GetEmployeeDetailsResponse getEmployeeDetails(GetEmployeeDetailsRequest request)
            throws EmployeeNotFoundFault, ValidationFault, DataAccessFault {
        try {
            GetEmployeeDetailsResponse response = new GetEmployeeDetailsResponse();
            response.setEmployee(EmployeeMapper.toWs(service.getEmployeeDetails(request.getMatricule())));
            return response;
        } catch (EmployeeNotFoundException e) {
            throw notFound(e);
        } catch (ValidationException e) {
            throw validation(e);
        } catch (DataAccessException | TransactionException e) {
            throw dataAccess(e);
        }
    }

    @Override
    public CreateEmployeeResponse createEmployee(CreateEmployeeRequest request)
            throws ValidationFault, DuplicateEmployeeFault, DataAccessFault {
        try {
            if (request.getEmployee() == null) {
                throw new ValidationException(List.of(new FieldViolation("employee", "obligatoire")));
            }
            CreateEmployeeResponse response = new CreateEmployeeResponse();
            response.setEmployee(EmployeeMapper.toWs(service.createEmployee(EmployeeMapper.toDraft(request.getEmployee()))));
            return response;
        } catch (ValidationException e) {
            throw validation(e);
        } catch (DuplicateEmployeeException e) {
            throw duplicate(e);
        } catch (DataAccessException | TransactionException e) {
            throw dataAccess(e);
        }
    }

    @Override
    public UpdateEmployeeResponse updateEmployee(UpdateEmployeeRequest request)
            throws EmployeeNotFoundFault, ValidationFault, DuplicateEmployeeFault, DataAccessFault {
        try {
            UpdateEmployeeResponse response = new UpdateEmployeeResponse();
            response.setEmployee(EmployeeMapper.toWs(service.updateEmployee(request.getMatricule(),
                    EmployeeMapper.toDraft(request.getMatricule(), request.getChanges()))));
            return response;
        } catch (EmployeeNotFoundException e) {
            throw notFound(e);
        } catch (ValidationException e) {
            throw validation(e);
        } catch (DuplicateEmployeeException e) {
            throw duplicate(e);
        } catch (DataAccessException | TransactionException e) {
            throw dataAccess(e);
        }
    }

    private static EmployeeNotFoundFault notFound(EmployeeNotFoundException e) {
        return new EmployeeNotFoundFault(e.getMessage(),
                EmployeeMapper.faultInfo("EMPLOYEE_NOT_FOUND", e.getMessage(), List.of()));
    }

    private static ValidationFault validation(ValidationException e) {
        return new ValidationFault(e.getMessage(),
                EmployeeMapper.faultInfo("VALIDATION_ERROR", e.getMessage(), e.getViolations()));
    }

    private static DuplicateEmployeeFault duplicate(DuplicateEmployeeException e) {
        return new DuplicateEmployeeFault(e.getMessage(), EmployeeMapper.faultInfo("DUPLICATE_EMPLOYEE",
                e.getMessage(), List.of(new FieldViolation(e.getField(), "déjà utilisé"))));
    }

    private static DataAccessFault dataAccess(RuntimeException e) {
        log.error("Erreur d'accès à la base de référence : {}", e.getMessage());
        String message = "Base de référence indisponible, réessayez plus tard";
        return new DataAccessFault(message, EmployeeMapper.faultInfo("DATA_ACCESS_ERROR", message, List.of()));
    }
}
