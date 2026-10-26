package eu.uniche.iguide.workingcopy.application;

import eu.uniche.iguide.readiness.application.ReadinessCheck;
import eu.uniche.iguide.readiness.application.ReadinessContext;
import eu.uniche.iguide.readiness.application.ReadinessReport;
import eu.uniche.iguide.workingcopy.domain.ResourceWorkingCopy;
import eu.uniche.iguide.workingcopy.domain.WorkingCopyResourceType;
import eu.uniche.iguide.workingcopy.infrastructure.ResourceWorkingCopyRepository;
import org.springframework.stereotype.Component;

/** Every working copy warns {@code PENDING_WORKING_COPY}: visitors do not see it until applied (DECISIONS.md D-033). */
@Component
public class WorkingCopyReadiness implements ReadinessCheck {

    private final ResourceWorkingCopyRepository copies;

    public WorkingCopyReadiness(ResourceWorkingCopyRepository copies) {
        this.copies = copies;
    }

    @Override
    public void check(ReadinessContext context, ReadinessReport report) {
        for (ResourceWorkingCopy copy : copies.findByProjectId(context.projectId())) {
            String type = copy.getResourceType() == WorkingCopyResourceType.GEOGRAPHIC_MAP ? "MAP"
                    : copy.getResourceType().name();
            report.warn(type, copy.getResourceId(), context.index().title(type, copy.getResourceId()), null,
                    "workingCopy", "PENDING_WORKING_COPY");
        }
    }
}
