package eu.uniche.iguide.platform.projectaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import eu.uniche.iguide.platform.catalogue.CatalogueClient;
import eu.uniche.iguide.platform.catalogue.CatalogueConditionalResponse;
import eu.uniche.iguide.platform.catalogue.CatalogueMeAuthorizationDto;
import java.util.List;
import org.junit.jupiter.api.Test;

class MeAuthorizationQueryTest {

    private final CatalogueClient catalogueClient = mock(CatalogueClient.class);
    private final MeAuthorizationQuery query = new MeAuthorizationQuery(catalogueClient);

    @Test
    void servesARepeatedReadFromTheCacheWithinTheTtlTheCatalogueDeclared() {
        when(catalogueClient.getMeAuthorization(isNull()))
                .thenReturn(CatalogueConditionalResponse.changed(view("\"etag-1\"", 300)));

        query.forSubject("subject-1");
        query.forSubject("subject-1");

        verify(catalogueClient, times(1)).getMeAuthorization(isNull());
        verifyNoMoreInteractions(catalogueClient);
    }

    @Test
    void cachesPerSubject() {
        when(catalogueClient.getMeAuthorization(isNull()))
                .thenReturn(CatalogueConditionalResponse.changed(view("\"etag-1\"", 300)));

        query.forSubject("subject-1");
        query.forSubject("subject-2");

        verify(catalogueClient, times(2)).getMeAuthorization(isNull());
    }

    /** A {@code 304} keeps the cached body and only extends its expiry. */
    @Test
    void anExpiredEntryIsRefreshedConditionallyAndSurvivesANotModified() {
        CatalogueMeAuthorizationDto cached = view("\"etag-1\"", 0);
        when(catalogueClient.getMeAuthorization(isNull()))
                .thenReturn(CatalogueConditionalResponse.changed(cached));
        when(catalogueClient.getMeAuthorization("\"etag-1\""))
                .thenReturn(CatalogueConditionalResponse.unchanged());

        CatalogueMeAuthorizationDto first = query.forSubject("subject-1");
        CatalogueMeAuthorizationDto second = query.forSubject("subject-1");

        assertThat(second).isSameAs(first);
        verify(catalogueClient).getMeAuthorization("\"etag-1\"");
    }

    @Test
    void anExpiredEntryIsReplacedWhenTheCatalogueReturnsANewView() {
        CatalogueMeAuthorizationDto stale = view("\"etag-1\"", 0);
        CatalogueMeAuthorizationDto fresh = view("\"etag-2\"", 300);
        when(catalogueClient.getMeAuthorization(isNull()))
                .thenReturn(CatalogueConditionalResponse.changed(stale));
        when(catalogueClient.getMeAuthorization("\"etag-1\""))
                .thenReturn(CatalogueConditionalResponse.changed(fresh));

        query.forSubject("subject-1");

        assertThat(query.forSubject("subject-1")).isSameAs(fresh);
    }

    private static CatalogueMeAuthorizationDto view(String etag, long ttlSeconds) {
        return new CatalogueMeAuthorizationDto("subject-1", false, List.of(), List.of(), etag,
                ttlSeconds);
    }
}
