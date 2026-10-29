package bf.ibam.employes.config;

import bf.ibam.employes.domain.EmployeeValidator;
import bf.ibam.employes.ws.ClientFaultCodeInterceptor;
import bf.ibam.employes.ws.EmployeeEndpoint;
import jakarta.xml.ws.Endpoint;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Set;
import javax.xml.namespace.QName;
import org.apache.cxf.Bus;
import org.apache.cxf.ext.logging.LoggingFeature;
import org.apache.cxf.jaxws.EndpointImpl;
import org.apache.cxf.metrics.MetricsFeature;
import org.apache.cxf.metrics.MetricsProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CxfConfig {

    private static final String NS = "http://ws.employes.ibam.bf/v1";
    private static final ZoneId ZONE = ZoneId.of("Africa/Ouagadougou");

    /** Publie le service sur /services/EmployeeService ; le WSDL est servi tel qu'écrit (contract-first). */
    @Bean
    public Endpoint employeeServiceEndpoint(Bus bus, EmployeeEndpoint implementor,
            ObjectProvider<MetricsProvider> metricsProvider) {
        EndpointImpl endpoint = new EndpointImpl(bus, implementor);
        endpoint.setWsdlLocation("classpath:wsdl/EmployeeService.wsdl");
        endpoint.setServiceName(new QName(NS, "EmployeeService"));
        endpoint.setEndpointName(new QName(NS, "EmployeePort"));
        LoggingFeature logging = new LoggingFeature();
        logging.setPrettyLogging(true);

        logging.setLimit(8 * 1024); // messages loggés tronqués à 8 Ko
        // Données personnelles : masquées dans les logs SOAP
        logging.setSensitiveElementNames(Set.of("salaire", "email", "telephone", "dateNaissance"));
        
        endpoint.getFeatures().add(logging);
        endpoint.getOutFaultInterceptors().add(new ClientFaultCodeInterceptor());
        // Métriques cxf_server_requests_seconds (opération, résultat, durée) exposées sur /actuator/prometheus
        metricsProvider.ifAvailable(p -> endpoint.getFeatures().add(new MetricsFeature(p)));
        endpoint.publish("/EmployeeService");
        return endpoint;
    }

    @Bean
    public EmployeeValidator employeeValidator() {
        return new EmployeeValidator(Clock.systemDefaultZone());
    }
}
