package eu.uniche.iguide.template.infrastructure;

import eu.uniche.iguide.template.domain.ApplicationTemplate;
import eu.uniche.iguide.template.domain.TemplateStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Platform-owned rows: not Project-scoped ({@code 04} section 2). */
public interface ApplicationTemplateRepository extends JpaRepository<ApplicationTemplate, UUID> {

    Optional<ApplicationTemplate> findByKeyAndVersion(String key, int version);

    List<ApplicationTemplate> findByStatusOrderByKeyAscVersionDesc(TemplateStatus status);
}
