package bf.ibam.employes.ws;

import bf.ibam.employes.ws.generated.DuplicateEmployeeFault;
import bf.ibam.employes.ws.generated.EmployeeNotFoundFault;
import bf.ibam.employes.ws.generated.ValidationFault;
import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.binding.soap.interceptor.AbstractSoapInterceptor;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.phase.Phase;

/**
 * Les erreurs dues à la requête (données invalides, employé inexistant, doublon) sont
 * renvoyées avec le faultcode soap:Client ; les pannes de base restent en soap:Server.
 */
public class ClientFaultCodeInterceptor extends AbstractSoapInterceptor {

    public ClientFaultCodeInterceptor() {
        super(Phase.PRE_LOGICAL);
    }

    @Override
    public void handleMessage(SoapMessage message) {
        if (message.getContent(Exception.class) instanceof Fault fault) {
            Throwable cause = fault.getCause();
            if (cause instanceof ValidationFault || cause instanceof EmployeeNotFoundFault
                    || cause instanceof DuplicateEmployeeFault) {
                fault.setFaultCode(message.getVersion().getSender());
            }
        }
    }
}
