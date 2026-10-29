package bf.ibam.employes.domain;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Locale;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Règles de validation du modèle commun d'employé. Ce sont les mêmes règles que celles
 * appliquées par la transformation Pentaho mysql_to_pg.ktr.
 */
public class EmployeeValidator {

    public static final Pattern MATRICULE = Pattern.compile("^EMP-\\d{4}$");
    public static final Pattern EMAIL = Pattern.compile("^[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}$");
    public static final Pattern TELEPHONE = Pattern.compile("^\\+?[0-9]{8,15}$");
    public static final Set<String> STATUTS = Set.of("ACTIF", "SUSPENDU", "SORTI");
    private static final int AGE_MINIMUM = 18;

    private final Clock clock;

    public EmployeeValidator(Clock clock) {
        this.clock = clock;
    }

    /** Valide un employé complet (déjà normalisé) ; departementConnu indique si le libellé existe. */
    public List<FieldViolation> validate(EmployeeDraft e, boolean departementConnu) {
        List<FieldViolation> errors = new ArrayList<>();
        LocalDate today = LocalDate.now(clock);

        if (isBlank(e.matricule())) {
            errors.add(new FieldViolation("matricule", "obligatoire"));
        } else if (!MATRICULE.matcher(e.matricule()).matches()) {
            errors.add(new FieldViolation("matricule", "format attendu EMP-0000"));
        }
        requireText(errors, "nom", e.nom(), 100);
        requireText(errors, "prenom", e.prenom(), 100);
        if (isBlank(e.email())) {
            errors.add(new FieldViolation("email", "obligatoire"));
        } else if (!EMAIL.matcher(e.email()).matches() || e.email().length() > 150) {
            errors.add(new FieldViolation("email", "format d'adresse invalide"));
        }
        if (!isBlank(e.telephone()) && !TELEPHONE.matcher(e.telephone()).matches()) {
            errors.add(new FieldViolation("telephone", "8 à 15 chiffres, précédés éventuellement de +"));
        }
        if (e.dateNaissance() == null) {
            errors.add(new FieldViolation("dateNaissance", "obligatoire"));
        } else if (e.dateNaissance().plusYears(AGE_MINIMUM).isAfter(today)) {
            errors.add(new FieldViolation("dateNaissance", "l'employé doit avoir au moins " + AGE_MINIMUM + " ans"));
        }
        if (e.dateEmbauche() == null) {
            errors.add(new FieldViolation("dateEmbauche", "obligatoire"));
        } else if (e.dateEmbauche().isAfter(today)) {
            errors.add(new FieldViolation("dateEmbauche", "ne peut pas être dans le futur"));
        } else if (e.dateNaissance() != null && !e.dateEmbauche().isAfter(e.dateNaissance())) {
            errors.add(new FieldViolation("dateEmbauche", "doit être postérieure à la date de naissance"));
        }
        if (isBlank(e.departement())) {
            errors.add(new FieldViolation("departement", "obligatoire"));
        } else if (!departementConnu) {
            errors.add(new FieldViolation("departement", "département inconnu : " + e.departement()));
        }
        requireText(errors, "poste", e.poste(), 100);
        if (e.salaire() == null) {
            errors.add(new FieldViolation("salaire", "obligatoire"));
        } else if (e.salaire().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add(new FieldViolation("salaire", "doit être strictement positif"));
        } else if (e.salaire().scale() > 2 || e.salaire().precision() - e.salaire().scale() > 10) {
            errors.add(new FieldViolation("salaire", "au plus 10 chiffres avant la virgule et 2 après"));
        }
        if (e.statut() == null || !STATUTS.contains(e.statut())) {
            errors.add(new FieldViolation("statut", "valeurs possibles : ACTIF, SUSPENDU, SORTI"));
        }
        return errors;
    }

    /** Normalisation identique à celle de Pentaho : espaces, casse du nom et de l'email. */
    public static EmployeeDraft normalize(EmployeeDraft e) {
        return new EmployeeDraft(
                upper(e.matricule()),
                upper(e.nom()),
                trim(e.prenom()),
                e.email() == null ? null : e.email().trim().toLowerCase(Locale.ROOT),
                trim(e.telephone()),
                e.dateNaissance(),
                e.dateEmbauche(),
                trim(e.departement()),
                trim(e.poste()),
                e.salaire(),
                upper(e.statut()));
    }

    private static void requireText(List<FieldViolation> errors, String field, String value, int max) {
        if (isBlank(value)) {
            errors.add(new FieldViolation(field, "obligatoire"));
        } else if (value.length() > max) {
            errors.add(new FieldViolation(field, "au plus " + max + " caractères"));
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }

    private static String upper(String s) {
        return s == null ? null : s.trim().toUpperCase(Locale.ROOT);
    }
}
