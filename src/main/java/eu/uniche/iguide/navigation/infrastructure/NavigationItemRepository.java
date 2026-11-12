package eu.uniche.iguide.navigation.infrastructure;

import eu.uniche.iguide.navigation.domain.NavigationItem;
import eu.uniche.iguide.navigation.domain.NavigationTargetType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Every query takes the Project explicitly ({@code 06} section 2). */
public interface NavigationItemRepository extends JpaRepository<NavigationItem, UUID> {

    List<NavigationItem> findByProjectIdAndNavigationIdIn(UUID projectId, Collection<UUID> navigationIds);

    List<NavigationItem> findByProjectIdAndNavigationId(UUID projectId, UUID navigationId);

    Optional<NavigationItem> findByProjectIdAndId(UUID projectId, UUID id);

    List<NavigationItem> findByProjectIdAndTargetResourceTypeAndTargetResourceId(UUID projectId,
                                                                                 NavigationTargetType type, UUID id);
}
