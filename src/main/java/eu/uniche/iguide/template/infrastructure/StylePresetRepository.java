package eu.uniche.iguide.template.infrastructure;

import eu.uniche.iguide.template.domain.StylePreset;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Platform-owned rows: not Project-scoped ({@code 04} section 9). */
public interface StylePresetRepository extends JpaRepository<StylePreset, String> {

    List<StylePreset> findByKeyIn(Collection<String> keys);

    /** Whether any Project's theme was set from the preset: its values are then immutable ({@code 04} section 15). */
    @Query(value = "SELECT EXISTS (SELECT 1 FROM project_themes WHERE style_preset_key = :key)", nativeQuery = true)
    boolean isUsedByAnyProject(@Param("key") String key);
}
