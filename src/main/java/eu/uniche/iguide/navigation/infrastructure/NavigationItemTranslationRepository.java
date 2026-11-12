package eu.uniche.iguide.navigation.infrastructure;

import eu.uniche.iguide.navigation.domain.NavigationItemTranslation;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Every query takes the Project explicitly ({@code 06} section 2). */
public interface NavigationItemTranslationRepository extends JpaRepository<NavigationItemTranslation, UUID> {

    List<NavigationItemTranslation> findByProjectIdAndNavigationItemIdIn(UUID projectId, Collection<UUID> itemIds);
}
