package eu.uniche.iguide.readiness.application;

/**
 * What one kind contributes to readiness ({@code 07} section 14, DECISIONS.md D-033). Each kind implements it in its
 * own package; every check runs in the read-only transaction of {@link GetReadinessQuery}, after the Project access
 * check, and evaluates only {@code ACTIVE} rows.
 */
public interface ReadinessCheck {

    void check(ReadinessContext context, ReadinessReport report);
}
