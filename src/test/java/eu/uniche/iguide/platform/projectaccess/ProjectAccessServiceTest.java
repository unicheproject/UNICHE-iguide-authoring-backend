package eu.uniche.iguide.platform.projectaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import eu.uniche.iguide.platform.catalogue.CatalogueClient;
import eu.uniche.iguide.platform.catalogue.CatalogueNotFoundException;
import eu.uniche.iguide.platform.catalogue.CatalogueProjectDto;
import eu.uniche.iguide.platform.catalogue.CatalogueTimeoutException;
import eu.uniche.iguide.platform.catalogue.CatalogueUnavailableException;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.project.application.ProjectCompanionSyncService;
import eu.uniche.iguide.project.domain.Project;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * Lazy reconciliation, from the access check's point of view. The distinction between the {@code 404}
 * and the {@code 5xx} case is the whole point: an unavailable Catalogue is not evidence that a Project is
 * gone, and treating it as such would soft-delete companion rows across an entire outage.
 */
class ProjectAccessServiceTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UnichePrincipal PRINCIPAL = new UnichePrincipal(
            "https://idp.test.local/realms/uniche", "subject-1", null, "someone", "Someone", "token");

    private final CatalogueClient catalogueClient = mock(CatalogueClient.class);
    private final ProjectCompanionSyncService companionSync = mock(ProjectCompanionSyncService.class);
    private final ProjectAccessService service =
            new ProjectAccessService(catalogueClient, companionSync);

    @Test
    void aCatalogueNotFoundSoftDeletesTheCompanionRowAndStillPropagates() {
        when(catalogueClient.getProject(PROJECT_ID))
                .thenThrow(new CatalogueNotFoundException("gone"));

        assertThatThrownBy(() -> service.requireAccess(PROJECT_ID, PRINCIPAL))
                .isInstanceOf(CatalogueNotFoundException.class);

        verify(companionSync).softDelete(PROJECT_ID);
        verify(companionSync, never()).upsert(any());
    }

    @Test
    void anUnavailableCatalogueChangesNothingLocally() {
        when(catalogueClient.getProject(PROJECT_ID))
                .thenThrow(new CatalogueUnavailableException("outage"));

        assertThatThrownBy(() -> service.requireAccess(PROJECT_ID, PRINCIPAL))
                .isInstanceOf(CatalogueUnavailableException.class);

        verify(companionSync, never()).softDelete(any());
        verify(companionSync, never()).upsert(any());
    }

    @Test
    void aTimedOutCatalogueChangesNothingLocally() {
        when(catalogueClient.getProject(PROJECT_ID))
                .thenThrow(new CatalogueTimeoutException("too slow", null));

        assertThatThrownBy(() -> service.requireAccess(PROJECT_ID, PRINCIPAL))
                .isInstanceOf(CatalogueTimeoutException.class);

        verify(companionSync, never()).softDelete(any());
    }

    @Test
    void aConcurrentSynchronizationOfTheSameRowIsNotAFailure() {
        CatalogueProjectDto dto = new CatalogueProjectDto(PROJECT_ID.toString(), UUID.randomUUID().toString(),
                "Museum", "museum", "ACTIVE", new CatalogueProjectDto.CatalogueToolDto("iguide"));
        Project row = Project.companionOf(PROJECT_ID, UUID.fromString(dto.orgId()), "Museum", "museum", "ACTIVE",
                Instant.now());
        when(catalogueClient.getProject(PROJECT_ID)).thenReturn(dto);
        when(companionSync.upsert(dto))
                .thenThrow(new ObjectOptimisticLockingFailureException(Project.class, PROJECT_ID));
        when(companionSync.get(PROJECT_ID)).thenReturn(row);

        assertThat(service.requireAccess(PROJECT_ID, PRINCIPAL).projectId())
                .isEqualTo(PROJECT_ID);
    }
}
