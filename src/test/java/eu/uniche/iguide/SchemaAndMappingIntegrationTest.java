package eu.uniche.iguide;

import static org.assertj.core.api.Assertions.assertThat;

import eu.uniche.iguide.support.PostgresIntegrationTest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * That this context starts at all is the assertion: Liquibase created the schema and
 * {@code ddl-auto=validate} then compared every JPA mapping against it. An entity that drifts from its
 * changeset fails here.
 */
class SchemaAndMappingIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void liquibaseCreatedTheTenancyBaseline() throws Exception {
        assertThat(tableNames()).contains(
                "projects", "catalogue_organization_snapshots", "identity_snapshots",
                // Liquibase's own bookkeeping, present only because migrations actually ran.
                "databasechangelog");
    }

    @Test
    void theProjectPrimaryKeyIsNotGeneratedLocally() throws Exception {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                     SELECT column_default
                     FROM information_schema.columns
                     WHERE table_name = 'projects' AND column_name = 'id'
                     """);
             ResultSet result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            // The Catalogue Project UUID is the primary key. A default here would mean this backend
            // could mint its own, which is exactly what must be impossible.
            assertThat(result.getString("column_default")).isNull();
        }
    }

    private List<String> tableNames() throws Exception {
        List<String> names = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                names.add(result.getString("table_name"));
            }
        }
        return names;
    }
}
