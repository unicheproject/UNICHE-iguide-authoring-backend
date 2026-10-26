package eu.uniche.iguide.platform.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import eu.uniche.iguide.support.ContentIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

/** Errors are RFC 7807 problem details with the platform correlation header ({@code 07} section 15, D-034). */
class ProblemDetailsIntegrationTest extends ContentIntegrationTest {

    private static final String PROBLEM_JSON = "application/problem+json";

    @BeforeEach
    void setUpLanguages() throws Exception {
        languages(PROJECT_A);
    }

    @Test
    void aValidationErrorIsAProblemWithItsCodeAndFieldPathsButNeverTheRejectedValue() throws Exception {
        String rejected = "Not A <Slug> 12345";
        JsonNode place = createPlace(PROJECT_A, "Ακρόπολη", "SITE");
        MvcResult result = mockMvc.perform(json(put(placeUrl(PROJECT_A) + "/{id}", place.get("id").asText()),
                        Map.of("version", place.get("version").asLong(), "slug", rejected, "type", "SITE"))
                        .header("X-UNICHE-Request-Id", "problem-1"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(header().string("X-UNICHE-Request-Id", "problem-1"))
                .andExpect(jsonPath("$.type").value("urn:uniche:iguide:problem:VALIDATION_ERROR"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.title").value("Validation error"))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.requestId").value("problem-1"))
                .andExpect(jsonPath("$.errors[0].path").value("slug"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_FORMAT"))
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(rejected);
    }

    @Test
    void aVersionConflictCarriesTheCurrentVersionAndTheModifiedMembers() throws Exception {
        JsonNode place = createPlace(PROJECT_A, "Ακρόπολη", "SITE");
        Map<String, Object> body = Map.of("version", place.get("version").asLong() + 7, "slug", "a", "type", "SITE");
        mockMvc.perform(json(put(placeUrl(PROJECT_A) + "/{id}", place.get("id").asText()), body))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:uniche:iguide:problem:VERSION_CONFLICT"))
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"))
                .andExpect(jsonPath("$.currentVersion").value(place.get("version").asLong()))
                .andExpect(jsonPath("$.modifiedBySubjectDisplayName").isEmpty())
                .andExpect(jsonPath("$.modifiedAt").isEmpty())
                .andExpect(jsonPath("$.references").doesNotExist());
    }

    @Test
    void aResourceInUseListsTheReferencesAndOtherErrorsLeaveTheConflictMembersOut() throws Exception {
        JsonNode poi = createPoi(PROJECT_A, "{\"title\": \"Κούρος\"}");
        mockMvc.perform(json(post("/api/v1/projects/{p}/triggers", PROJECT_A), Map.of("triggerType", "QR_CODE",
                        "targetResourceType", "POI", "targetResourceId", poi.get("id").asText())))
                .andExpect(status().isCreated());
        mockMvc.perform(json(post(poiUrl(PROJECT_A) + "/{id}/status", poi.get("id").asText()),
                        Map.of("targetStatus", "ARCHIVED", "version", poi.get("version").asLong())))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"))
                .andExpect(jsonPath("$.references[0].resourceType").value("TRIGGER"))
                .andExpect(jsonPath("$.modifiedBySubjectDisplayName").doesNotExist())
                .andExpect(jsonPath("$.currentVersion").doesNotExist());
    }

    @Test
    void anUnauthenticatedCallIsAProblemWithTheBearerChallenge() throws Exception {
        mockMvc.perform(get(placeUrl(PROJECT_A)).header("X-Request-Id", "legacy-9"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(header().string("X-UNICHE-Request-Id", "legacy-9"))
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:uniche:iguide:problem:UNAUTHENTICATED"))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.requestId").value("legacy-9"));
    }

    @Test
    void frameworkErrorsAreProblemsToo() throws Exception {
        mockMvc.perform(authenticated(get("/api/v1/no-such-endpoint")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(json(post(placeUrl(PROJECT_A)), "{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        mockMvc.perform(authenticated(delete(placeUrl(PROJECT_A))))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        mockMvc.perform(authenticated(post(placeUrl(PROJECT_A))).contentType("text/plain").content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void everySuccessfulResponseCarriesThePlatformRequestId() throws Exception {
        mockMvc.perform(authenticated(get(placeUrl(PROJECT_A))))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-UNICHE-Request-Id"))
                .andExpect(header().doesNotExist("X-Request-Id"));
    }
}
