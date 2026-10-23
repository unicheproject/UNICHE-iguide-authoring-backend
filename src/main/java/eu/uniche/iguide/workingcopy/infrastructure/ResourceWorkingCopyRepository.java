package eu.uniche.iguide.workingcopy.infrastructure;

import eu.uniche.iguide.workingcopy.domain.ResourceWorkingCopy;
import eu.uniche.iguide.workingcopy.domain.WorkingCopyResourceType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every query takes the Project explicitly ({@code 06} section 2). */
public interface ResourceWorkingCopyRepository extends JpaRepository<ResourceWorkingCopy, UUID> {

    Optional<ResourceWorkingCopy> findByProjectIdAndResourceTypeAndResourceId(UUID projectId,
                                                                             WorkingCopyResourceType resourceType,
                                                                             UUID resourceId);

    List<ResourceWorkingCopy> findByProjectIdAndResourceTypeAndResourceIdIn(UUID projectId,
                                                                           WorkingCopyResourceType resourceType,
                                                                           Collection<UUID> resourceIds);

    @Modifying
    @Query("delete from ResourceWorkingCopy c where c.projectId = :projectId and c.resourceType = :resourceType"
            + " and c.resourceId = :resourceId")
    int deleteFor(@Param("projectId") UUID projectId, @Param("resourceType") WorkingCopyResourceType resourceType,
                  @Param("resourceId") UUID resourceId);
}
