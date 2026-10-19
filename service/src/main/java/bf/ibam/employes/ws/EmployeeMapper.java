package bf.ibam.employes.ws;

import bf.ibam.employes.domain.EmployeeDraft;
import bf.ibam.employes.domain.EmployeeRecord;
import bf.ibam.employes.domain.FieldViolation;
import bf.ibam.employes.ws.generated.Employee;
import bf.ibam.employes.ws.generated.EmployeeChanges;
import bf.ibam.employes.ws.generated.FieldError;
import bf.ibam.employes.ws.generated.NewEmployee;
import bf.ibam.employes.ws.generated.ServiceFaultInfo;
import bf.ibam.employes.ws.generated.Statut;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

/** Conversion entre les types générés depuis le WSDL et le modèle métier. */
final class EmployeeMapper {

    private static final DatatypeFactory XML_DATES;

    static {
        try {
            XML_DATES = DatatypeFactory.newInstance();
        } catch (DatatypeConfigurationException e) {
            throw new IllegalStateException(e);
        }
    }

    private EmployeeMapper() {
    }

    static Employee toWs(EmployeeRecord r) {
        Employee e = new Employee();
        e.setMatricule(r.matricule());
        e.setNom(r.nom());
        e.setPrenom(r.prenom());
        e.setEmail(r.email());
        e.setTelephone(r.telephone());
        e.setDateNaissance(toXml(r.dateNaissance()));
        e.setDateEmbauche(toXml(r.dateEmbauche()));
        e.setDepartement(r.departement());
        e.setPoste(r.poste());
        e.setSalaire(r.salaire());
        e.setStatut(Statut.fromValue(r.statut()));
        e.setSource(r.source());
        e.setUpdatedAt(toXml(r.updatedAt()));
        return e;
    }

    static EmployeeDraft toDraft(NewEmployee n) {
        return new EmployeeDraft(n.getMatricule(), n.getNom(), n.getPrenom(), n.getEmail(), n.getTelephone(),
                toLocalDate(n.getDateNaissance()), toLocalDate(n.getDateEmbauche()), n.getDepartement(),
                n.getPoste(), n.getSalaire(), n.getStatut() == null ? null : n.getStatut().value());
    }

    static EmployeeDraft toDraft(String matricule, EmployeeChanges c) {
        if (c == null) {
            return new EmployeeDraft(matricule, null, null, null, null, null, null, null, null, null, null);
        }
        return new EmployeeDraft(matricule, c.getNom(), c.getPrenom(), c.getEmail(), c.getTelephone(),
                toLocalDate(c.getDateNaissance()), toLocalDate(c.getDateEmbauche()), c.getDepartement(),
                c.getPoste(), c.getSalaire(), c.getStatut() == null ? null : c.getStatut().value());
    }

    static ServiceFaultInfo faultInfo(String code, String message, List<FieldViolation> violations) {
        ServiceFaultInfo info = new ServiceFaultInfo();
        info.setCode(code);
        info.setMessage(message);
        for (FieldViolation v : violations) {
            FieldError fe = new FieldError();
            fe.setField(v.field());
            fe.setMessage(v.message());
            info.getFieldErrors().add(fe);
        }
        return info;
    }

    private static XMLGregorianCalendar toXml(LocalDate d) {
        if (d == null) {
            return null;
        }
        return XML_DATES.newXMLGregorianCalendarDate(d.getYear(), d.getMonthValue(), d.getDayOfMonth(),
                DatatypeConstants.FIELD_UNDEFINED);
    }

    private static XMLGregorianCalendar toXml(LocalDateTime t) {
        return t == null ? null : XML_DATES.newXMLGregorianCalendar(t.withNano(t.getNano() / 1_000_000 * 1_000_000).toString());
    }

    private static LocalDate toLocalDate(XMLGregorianCalendar c) {
        return c == null ? null : LocalDate.of(c.getYear(), c.getMonth(), c.getDay());
    }
}
