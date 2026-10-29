package eu.uniche.iguide.workingcopy.application;

import static org.assertj.core.api.Assertions.assertThat;

import eu.uniche.iguide.readiness.application.ReadinessIssue;
import eu.uniche.iguide.workingcopy.domain.WorkingCopyResourceType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The comparison behind the apply refusal (DECISIONS.md D-037). */
class ApplyReadinessGateTest {

    private static final UUID ROUTE = UUID.randomUUID();

    private static ReadinessIssue dangling(int stop) {
        return new ReadinessIssue("ROUTE", ROUTE, "Highlights", null, "stops[" + stop + "].poiId", "DANGLING_REFERENCE");
    }

    @Test
    void aFindingThatOnlyMovedIsNotNew() {
        assertThat(ApplyReadinessGate.added(List.of(dangling(1)), List.of(dangling(3)))).isEmpty();
    }

    @Test
    void aSecondFindingOfTheSameKindIsNew() {
        assertThat(ApplyReadinessGate.added(List.of(dangling(1)), List.of(dangling(0), dangling(2)))).hasSize(1);
    }

    @Test
    void aFixedFindingAndAnUnrelatedOneAreNotNew() {
        ReadinessIssue surfaces = new ReadinessIssue("PROJECT", null, null, null, "surfaces", "NO_SURFACE_ENABLED");
        assertThat(ApplyReadinessGate.added(List.of(surfaces, dangling(0)), List.of(surfaces))).isEmpty();
    }

    @Test
    void mapsAreNamedAsTheEvaluationNamesThem() {
        assertThat(ApplyReadinessGate.readinessType(WorkingCopyResourceType.GEOGRAPHIC_MAP)).isEqualTo("MAP");
        assertThat(ApplyReadinessGate.readinessType(WorkingCopyResourceType.ROUTE)).isEqualTo("ROUTE");
    }
}
