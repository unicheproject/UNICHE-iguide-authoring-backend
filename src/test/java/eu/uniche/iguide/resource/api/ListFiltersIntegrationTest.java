package eu.uniche.iguide.resource.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import eu.uniche.iguide.support.ContentIntegrationTest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The list parameters every content collection shares ({@code 07} section 14, {@code 06} section 16; DECISIONS.md
 * D-030): {@code language}, {@code featured}, {@code sort}, and {@code categoryId} on Points of Interest and Routes.
 */
class ListFiltersIntegrationTest extends ContentIntegrationTest {

    private static final List<String> KINDS = List.of("places", "pois", "routes", "guides", "events", "categories");

    @BeforeEach
    void setUpLanguages() throws Exception {
        languages(PROJECT_A);
        languages(PROJECT_B);
    }

    @Test
    void languageNarrowsTheSearchToOneLanguageAndMustBeOfTheProject() throws Exception {
        for (String kind : KINDS) {
            create(kind, "Kouros");
            mockMvc.perform(authenticated(get(url(kind)).param("q", "kouros")))
                    .andExpect(jsonPath("$.totalElements").value(1));
            // Created in the default language, Greek: not found among the English texts.
            mockMvc.perform(authenticated(get(url(kind)).param("q", "kouros").param("language", "el")))
                    .andExpect(jsonPath("$.totalElements").value(1));
            mockMvc.perform(authenticated(get(url(kind)).param("q", "kouros").param("language", "en")))
                    .andExpect(jsonPath("$.totalElements").value(0));
            mockMvc.perform(authenticated(get(url(kind)).param("language", "fr")))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errors[0].path").value("language"))
                    .andExpect(jsonPath("$.errors[0].code").value("UNKNOWN_LANGUAGE"));
        }
    }

    @Test
    void sortNamesAnIndexedColumnAndDirection() throws Exception {
        create("places", "Alpha");
        create("places", "Beta");
        create("places", "Gamma");
        mockMvc.perform(authenticated(get(url("places")).param("sort", "slug,desc")))
                .andExpect(jsonPath("$.content[0].slug").value("gamma"))
                .andExpect(jsonPath("$.content[2].slug").value("alpha"));
        mockMvc.perform(authenticated(get(url("places")).param("sort", "slug")))
                .andExpect(jsonPath("$.content[0].slug").value("alpha"));
        mockMvc.perform(authenticated(get(url("places"))))
                .andExpect(jsonPath("$.content[0].slug").value("alpha"))
                .andExpect(jsonPath("$.content[2].slug").value("gamma"));
        for (String bad : List.of("title", "slug,sideways", "slug,asc,again")) {
            mockMvc.perform(authenticated(get(url("places")).param("sort", bad)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errors[0].code").value("INVALID_SORT"));
        }
        create("categories", "Sculpture");
        mockMvc.perform(authenticated(get(url("categories")).param("sort", "code,desc")))
                .andExpect(status().isOk());
    }

    @Test
    void featuredFiltersEitherWay() throws Exception {
        JsonNode plain = create("places", "Agora");
        JsonNode featured = create("places", "Acropolis");
        mockMvc.perform(json(put(url("places") + "/{id}", featured.get("id").asText()), Map.of(
                        "version", featured.get("version").asLong(), "slug", featured.get("slug").asText(),
                        "type", "SITE", "featured", true)))
                .andExpect(status().isOk());

        mockMvc.perform(authenticated(get(url("places")).param("featured", "true")))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(featured.get("id").asText()));
        mockMvc.perform(authenticated(get(url("places")).param("featured", "false")))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(plain.get("id").asText()));
    }

    @Test
    void categoryIdIncludesSubcategoriesAndNoneFindsTheUncategorized() throws Exception {
        JsonNode sculpture = category("Sculpture", "POI", null);
        JsonNode kouroi = category("Kouroi", "POI", sculpture.get("id").asText());
        JsonNode inParent = create("pois", "Stele");
        JsonNode inChild = create("pois", "Kouros");
        create("pois", "Amphora");
        bind("pois", inParent, sculpture);
        bind("pois", inChild, kouroi);

        mockMvc.perform(authenticated(get(url("pois")).param("categoryId", sculpture.get("id").asText())))
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(authenticated(get(url("pois")).param("categoryId", kouroi.get("id").asText())))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(inChild.get("id").asText()));
        mockMvc.perform(authenticated(get(url("pois")).param("categoryId", "none")))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(authenticated(get(url("pois")).param("categoryId", "not-an-id")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].path").value("categoryId"));

        JsonNode foreign = read(mockMvc.perform(json(post("/api/v1/projects/{p}/categories", PROJECT_B),
                Map.of("name", "Foreign", "scope", "POI"))).andExpect(status().isCreated()).andReturn());
        mockMvc.perform(authenticated(get(url("pois")).param("categoryId", foreign.get("id").asText())))
                .andExpect(jsonPath("$.totalElements").value(0));

        JsonNode trails = category("Trails", "ROUTE", null);
        JsonNode route = create("routes", "Coast walk");
        create("routes", "Old town");
        bind("routes", route, trails);
        mockMvc.perform(authenticated(get(url("routes")).param("categoryId", trails.get("id").asText())))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(route.get("id").asText()));
        mockMvc.perform(authenticated(get(url("routes")).param("categoryId", "none")))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // --- Helpers ---------------------------------------------------------------------------------

    private JsonNode create(String kind, String title) throws Exception {
        Map<String, Object> body = new HashMap<>();
        switch (kind) {
            case "places" -> body.putAll(Map.of("title", title, "type", "SITE"));
            case "routes" -> body.putAll(Map.of("title", title, "type", "OUTDOOR"));
            // A code unlike the name: a Category's code matches q in every language.
            case "categories" -> body.putAll(Map.of("name", title, "scope", "POI",
                    "code", "item-" + title.length() + "-" + Integer.toHexString(title.hashCode())));
            default -> body.put("title", title);
        }
        return read(mockMvc.perform(json(post(url(kind)), body)).andExpect(status().isCreated()).andReturn());
    }

    private JsonNode category(String name, String scope, String parentId) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("name", name, "scope", scope));
        body.put("parentCategoryId", parentId);
        return read(mockMvc.perform(json(post(url("categories")), body)).andExpect(status().isCreated()).andReturn());
    }

    private void bind(String kind, JsonNode resource, JsonNode category) throws Exception {
        mockMvc.perform(json(post(url(kind) + "/{id}/categories", resource.get("id").asText()),
                        Map.of("categoryId", category.get("id").asText())))
                .andExpect(status().isCreated());
    }

    private static String url(String kind) {
        return "/api/v1/projects/" + PROJECT_A + "/" + kind;
    }
}
