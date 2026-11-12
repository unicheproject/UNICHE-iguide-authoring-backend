package eu.uniche.iguide.navigation.infrastructure;

import eu.uniche.iguide.navigation.domain.Navigation;
import eu.uniche.iguide.navigation.domain.NavigationContext;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Every query takes the Project explicitly ({@code 06} section 2). */
public interface NavigationRepository extends JpaRepository<Navigation, UUID> {

    List<Navigation> findByProjectIdOrderByContextAsc(UUID projectId);

    Optional<Navigation> findByProjectIdAndId(UUID projectId, UUID id);

    boolean existsByProjectIdAndContext(UUID projectId, NavigationContext context);
}
