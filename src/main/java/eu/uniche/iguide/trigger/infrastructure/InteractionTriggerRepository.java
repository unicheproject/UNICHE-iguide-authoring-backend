package eu.uniche.iguide.trigger.infrastructure;

import eu.uniche.iguide.resource.domain.ResourceStatus;
import eu.uniche.iguide.trigger.domain.InteractionTrigger;
import eu.uniche.iguide.trigger.domain.TriggerTargetType;
import eu.uniche.iguide.trigger.domain.TriggerType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every query takes the Project explicitly ({@code 06} section 2). */
public interface InteractionTriggerRepository extends JpaRepository<InteractionTrigger, UUID>,
        JpaSpecificationExecutor<InteractionTrigger> {

    Optional<InteractionTrigger> findByProjectIdAndId(UUID projectId, UUID id);

    boolean existsByProjectIdAndCode(UUID projectId, String code);

    boolean existsByProjectIdAndCodeAndIdNot(UUID projectId, String code, UUID id);

    List<InteractionTrigger> findByProjectIdAndTriggerTypeAndStatusNot(UUID projectId, TriggerType type,
                                                                      ResourceStatus status);

    List<InteractionTrigger> findByProjectIdAndTargetResourceTypeAndTargetResourceIdAndStatusNotOrderBySortOrderAsc(
            UUID projectId, TriggerTargetType targetType, UUID targetId, ResourceStatus status);

    @Query("""
            select coalesce(max(t.sortOrder), 0) from InteractionTrigger t
            where t.projectId = :projectId and t.targetResourceType = :targetType and t.targetResourceId = :targetId
            """)
    int findMaxSortOrder(@Param("projectId") UUID projectId, @Param("targetType") TriggerTargetType targetType,
                         @Param("targetId") UUID targetId);
}
