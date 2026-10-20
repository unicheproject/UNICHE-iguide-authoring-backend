package eu.uniche.iguide.support;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.uniche.iguide.platform.catalogue.CatalogueClient;
import eu.uniche.iguide.platform.catalogue.CatalogueProjectDto;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Base for the Project-owned content tests: two Projects the Catalogue admits the caller to, so every
 * family can prove that one Project's rows are invisible from the other.
 */
public abstract class ContentIntegrationTest extends PostgresIntegrationTest {

    protected static final UUID PROJECT_A = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    protected static final UUID PROJECT_B = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID ORGANIZATION_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    protected CatalogueClient catalogueClient;

    @BeforeEach
    protected void admitBothProjects() {
        clean();
        when(catalogueClient.getProject(PROJECT_A)).thenReturn(catalogueProject(PROJECT_A, "project-a"));
        when(catalogueClient.getProject(PROJECT_B)).thenReturn(catalogueProject(PROJECT_B, "project-b"));
    }

    /** Also after each test, so test classes that delete Projects never meet a foreign key. */
    @AfterEach
    protected void clean() {
        jdbc.execute("""
                DELETE FROM resource_assets; DELETE FROM interaction_triggers;
                DELETE FROM floor_plan_positions; DELETE FROM floor_plans;
                DELETE FROM asset_translations; DELETE FROM assets;
                DELETE FROM geographic_map_resources; DELETE FROM geographic_maps;
                DELETE FROM resource_categories; DELETE FROM category_translations; DELETE FROM categories;
                DELETE FROM guide_block_translations; DELETE FROM guide_blocks;
                DELETE FROM guide_translations; DELETE FROM guides;
                DELETE FROM event_occurrences; DELETE FROM event_translations; DELETE FROM events;
                DELETE FROM route_stop_translations; DELETE FROM route_stops;
                DELETE FROM route_translations; DELETE FROM routes;
                DELETE FROM poi_translations; DELETE FROM points_of_interest;
                DELETE FROM place_translations; DELETE FROM places;
                DELETE FROM project_languages; DELETE FROM projects
                """);
    }

    // --- Requests --------------------------------------------------------------------------------

    protected static MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + TestIdp.validToken("subject-1"));
    }

    protected MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, Object body) throws Exception {
        return authenticated(request).contentType(MediaType.APPLICATION_JSON)
                .content(body instanceof String s ? s : objectMapper.writeValueAsString(body));
    }

    protected JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    // --- Fixtures --------------------------------------------------------------------------------

    /** Greek default, English second. */
    protected void languages(UUID projectId) throws Exception {
        mockMvc.perform(json(put("/api/v1/projects/{p}/languages", projectId), """
                {"languages": [
                  {"languageCode": "el", "displayName": "Ελληνικά", "direction": "LTR", "isDefault": true},
                  {"languageCode": "en", "displayName": "English", "direction": "LTR", "isDefault": false}
                ]}""")).andExpect(status().isOk());
    }

    protected JsonNode createPlace(UUID projectId, String title, String type) throws Exception {
        return createPlace(projectId, "{\"title\": \"" + title + "\", \"type\": \"" + type + "\"}");
    }

    protected JsonNode createPlace(UUID projectId, String body) throws Exception {
        return read(mockMvc.perform(json(post("/api/v1/projects/{p}/places", projectId), body))
                .andExpect(status().isCreated()).andReturn());
    }

    protected JsonNode changeStatus(String path, Object id, String target, long version, int expected) throws Exception {
        return read(mockMvc.perform(json(post(path + "/{id}/status", id),
                        "{\"targetStatus\": \"" + target + "\", \"version\": " + version + "}"))
                .andExpect(status().is(expected)).andReturn());
    }

    protected JsonNode createPoi(UUID projectId, String body) throws Exception {
        return read(mockMvc.perform(json(post("/api/v1/projects/{p}/pois", projectId), body))
                .andExpect(status().isCreated()).andReturn());
    }

    /** Uploads one file to the Project's media library and expects {@code 201}. */
    protected JsonNode uploadAsset(UUID projectId, String filename, String contentType, byte[] bytes,
                                   String... params) throws Exception {
        var request = multipart("/api/v1/projects/{p}/assets", projectId)
                .file(new MockMultipartFile("file", filename, contentType, bytes));
        for (int i = 0; i + 1 < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return read(mockMvc.perform(authenticated(request)).andExpect(status().isCreated()).andReturn());
    }

    /** A real PNG of the given size, so the backend can read its dimensions. */
    protected static byte[] png(int width, int height) throws Exception {
        var image = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    protected static String assetUrl(UUID projectId) {
        return "/api/v1/projects/" + projectId + "/assets";
    }

    protected static String poiUrl(UUID projectId) {
        return "/api/v1/projects/" + projectId + "/pois";
    }

    protected static String placeUrl(UUID projectId) {
        return "/api/v1/projects/" + projectId + "/places";
    }

    private static CatalogueProjectDto catalogueProject(UUID id, String slug) {
        return new CatalogueProjectDto(id.toString(), ORGANIZATION_ID.toString(), slug, slug, "ACTIVE",
                new CatalogueProjectDto.CatalogueToolDto("iguide"));
    }
}
