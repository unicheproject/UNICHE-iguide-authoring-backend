package eu.uniche.iguide.asset.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import eu.uniche.iguide.support.ContentIntegrationTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/** Assets: upload to S3, metadata, texts, lifecycle, and where-used ({@code 03} section 4, {@code 08} Milestone 3). */
class AssetIntegrationTest extends ContentIntegrationTest {

    @BeforeEach
    void setUpLanguages() throws Exception {
        languages(PROJECT_A);
        languages(PROJECT_B);
    }

    // --- Upload ----------------------------------------------------------------------------------

    @Test
    void anUploadRecordsTypeChecksumAndDimensionsAndItsBytesAreReadableThroughTheSignedUrl() throws Exception {
        byte[] bytes = png(40, 30);
        JsonNode asset = uploadAsset(PROJECT_A, "C:\\photos\\kouros.png", "image/png", bytes);
        assertThat(asset.get("type").asText()).isEqualTo("IMAGE");
        assertThat(asset.get("status").asText()).isEqualTo("DRAFT");
        assertThat(asset.get("originalFilename").asText()).isEqualTo("kouros.png");
        assertThat(asset.get("internalName").asText()).isEqualTo("kouros.png");
        assertThat(asset.get("mimeType").asText()).isEqualTo("image/png");
        assertThat(asset.get("sizeBytes").asLong()).isEqualTo(bytes.length);
        assertThat(asset.get("checksum").asText())
                .isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        assertThat(asset.get("width").asInt()).isEqualTo(40);
        assertThat(asset.get("height").asInt()).isEqualTo(30);
        assertThat(asset.get("usageCount").asLong()).isZero();

        HttpResponse<byte[]> fetched = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(asset.get("url").asText())).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(fetched.statusCode()).isEqualTo(200);
        assertThat(fetched.body()).isEqualTo(bytes);

        JsonNode link = read(mockMvc.perform(authenticated(get(assetUrl(PROJECT_A) + "/{id}/download",
                asset.get("id").asText()))).andExpect(status().isOk()).andReturn());
        assertThat(link.get("url").asText()).contains("response-content-disposition");
    }

    @Test
    void typesFollowTheFileAndOnlyAnImageCanBeAFloorPlan() throws Exception {
        assertThat(uploadAsset(PROJECT_A, "guide.pdf", "application/pdf", "%PDF".getBytes(StandardCharsets.UTF_8))
                .get("type").asText()).isEqualTo("DOCUMENT");
        assertThat(uploadAsset(PROJECT_A, "vase.glb", "application/octet-stream", new byte[]{1, 2, 3})
                .get("type").asText()).isEqualTo("THREE_D_MODEL");
        assertThat(uploadAsset(PROJECT_A, "level-1.png", "image/png", png(10, 10), "type", "FLOOR_PLAN",
                "internalName", "Ground floor").get("internalName").asText()).isEqualTo("Ground floor");

        mockMvc.perform(authenticated(multipart(assetUrl(PROJECT_A), PROJECT_A)
                        .file(new MockMultipartFile("file", "notes.txt", "text/plain", new byte[]{1}))
                        .param("type", "FLOOR_PLAN")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("WRONG_ASSET_TYPE"));
        mockMvc.perform(authenticated(multipart(assetUrl(PROJECT_A), PROJECT_A)
                        .file(new MockMultipartFile("file", "empty.png", "image/png", new byte[0]))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("EMPTY_FILE"));
    }

    @Test
    void anUploadAboveItsTypeLimitIsRefusedNamingTheLimit() throws Exception {
        mockMvc.perform(authenticated(multipart(assetUrl(PROJECT_A), PROJECT_A)
                        .file(new MockMultipartFile("file", "long.pdf", "application/pdf", new byte[4096]))))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.type").value("ASSET_TOO_LARGE"))
                .andExpect(jsonPath("$.title").value("The file is larger than the 2048 bytes limit for document files."));
    }

    @Test
    void uploadLimitsAreServedToAnyAuthenticatedCaller() throws Exception {
        mockMvc.perform(authenticated(get("/api/v1/upload-limits")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxUploadBytes").value(209715200))
                .andExpect(jsonPath("$.perType.IMAGE").value(1048576))
                .andExpect(jsonPath("$.perType.FLOOR_PLAN").value(1048576))
                .andExpect(jsonPath("$.perType.DOCUMENT").value(2048));
        mockMvc.perform(get("/api/v1/upload-limits")).andExpect(status().isUnauthorized());
    }

    // --- Texts and lifecycle ---------------------------------------------------------------------

    @Test
    void activatingAnImageNeedsDefaultLanguageAlternativeTextUnlessDecorative() throws Exception {
        JsonNode asset = uploadAsset(PROJECT_A, "kouros.png", "image/png", png(8, 8));
        JsonNode missing = changeStatus(assetUrl(PROJECT_A), asset.get("id").asText(), "ACTIVE",
                asset.get("version").asLong(), 422);
        assertThat(missing.get("errors").get(0).get("path").asText()).isEqualTo("translations.el.alternativeText");

        JsonNode described = update(asset, Map.of("internalName", "Kouros", "translations", Map.of(
                "el", Map.of("alternativeText", "Μαρμάρινος κούρος", "title", "Κούρος"),
                "en", Map.of("alternativeText", "A marble kouros", "credits", "Museum archive"))), 200);
        assertThat(described.get("translations").get("en").get("credits").asText()).isEqualTo("Museum archive");
        JsonNode active = changeStatus(assetUrl(PROJECT_A), asset.get("id").asText(), "ACTIVE",
                described.get("version").asLong(), 200);
        assertThat(active.get("status").asText()).isEqualTo("ACTIVE");

        // An ACTIVE asset stays editable, but keeps its text alternative.
        assertThat(update(active, Map.of("internalName", "Kouros", "translations",
                Map.of("el", Map.of("alternativeText", " "))), 422).get("errors").get(0).get("code").asText())
                .isEqualTo("MISSING_REQUIRED_FIELDS");

        JsonNode decorative = uploadAsset(PROJECT_A, "border.png", "image/png", png(4, 4));
        JsonNode flagged = update(decorative, Map.of("internalName", "Border", "decorative", true), 200);
        changeStatus(assetUrl(PROJECT_A), decorative.get("id").asText(), "ACTIVE", flagged.get("version").asLong(), 200);

        assertThat(update(asset, Map.of("internalName", "Kouros", "translations", Map.of("fr", Map.of("title", "x"))), 409)
                .get("type").asText()).isEqualTo("VERSION_CONFLICT");
        assertThat(update(active, Map.of("internalName", "Kouros", "translations", Map.of("fr", Map.of("title", "x"))), 422)
                .get("errors").get(0).get("code").asText()).isEqualTo("UNKNOWN_LANGUAGE");
    }

    @Test
    void theFileOfADraftIsReplacedWithOneOfTheSameTypeAndAnActiveAssetKeepsItsFile() throws Exception {
        JsonNode asset = uploadAsset(PROJECT_A, "kouros.png", "image/png", png(8, 8));
        JsonNode replaced = replace(asset, "kouros-v2.png", "image/png", png(16, 12), 200);
        assertThat(replaced.get("originalFilename").asText()).isEqualTo("kouros-v2.png");
        assertThat(replaced.get("width").asInt()).isEqualTo(16);
        assertThat(replaced.get("id").asText()).isEqualTo(asset.get("id").asText());
        assertThat(replace(replaced, "notes.pdf", "application/pdf", new byte[]{1}, 422)
                .get("errors").get(0).get("code").asText()).isEqualTo("WRONG_ASSET_TYPE");

        JsonNode flagged = update(replaced, Map.of("internalName", "Kouros", "decorative", true), 200);
        JsonNode active = changeStatus(assetUrl(PROJECT_A), asset.get("id").asText(), "ACTIVE",
                flagged.get("version").asLong(), 200);
        assertThat(replace(active, "kouros-v3.png", "image/png", png(8, 8), 422)
                .get("errors").get(0).get("code").asText()).isEqualTo("RESOURCE_NOT_EDITABLE");
    }

    // --- Where used and isolation ----------------------------------------------------------------

    @Test
    void aFloorPlanImageIsCheckedAndKeepsTheAssetInUse() throws Exception {
        JsonNode pdf = uploadAsset(PROJECT_A, "plan.pdf", "application/pdf", new byte[]{1});
        mockMvc.perform(json(post("/api/v1/projects/{p}/floor-plans", PROJECT_A),
                        Map.of("internalName", "Ground", "assetId", pdf.get("id").asText())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("WRONG_ASSET_TYPE"));
        JsonNode foreign = uploadAsset(PROJECT_B, "plan.png", "image/png", png(8, 8));
        mockMvc.perform(json(post("/api/v1/projects/{p}/floor-plans", PROJECT_A),
                        Map.of("internalName", "Ground", "assetId", foreign.get("id").asText())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("RESOURCE_NOT_FOUND"));

        JsonNode image = uploadAsset(PROJECT_A, "plan.png", "image/png", png(120, 80), "type", "FLOOR_PLAN");
        JsonNode plan = read(mockMvc.perform(json(post("/api/v1/projects/{p}/floor-plans", PROJECT_A),
                        Map.of("internalName", "Ground", "assetId", image.get("id").asText())))
                .andExpect(status().isCreated()).andReturn());
        assertThat(plan.get("width").asInt()).isEqualTo(120);
        assertThat(plan.get("height").asInt()).isEqualTo(80);

        JsonNode reloaded = read(mockMvc.perform(authenticated(get(assetUrl(PROJECT_A) + "/{id}", image.get("id").asText())))
                .andExpect(status().isOk()).andReturn());
        assertThat(reloaded.get("usageCount").asLong()).isEqualTo(1);
        JsonNode usages = read(mockMvc.perform(authenticated(get(assetUrl(PROJECT_A) + "/{id}/usages",
                image.get("id").asText()))).andExpect(status().isOk()).andReturn());
        assertThat(usages.get(0).get("resource").get("resourceType").asText()).isEqualTo("FLOOR_PLAN");
        assertThat(usages.get(0).get("usages").get(0).get("usage").asText()).isEqualTo("FLOOR_PLAN_IMAGE");

        JsonNode inUse = changeStatus(assetUrl(PROJECT_A), image.get("id").asText(), "ARCHIVED",
                image.get("version").asLong(), 409);
        assertThat(inUse.get("type").asText()).isEqualTo("RESOURCE_IN_USE");
        assertThat(inUse.get("references").get(0).get("title").asText()).isEqualTo("Ground");
    }

    @Test
    void anAssetIsInvisibleFromAnotherProjectAndTheListFilters() throws Exception {
        JsonNode asset = uploadAsset(PROJECT_A, "kouros.png", "image/png", png(8, 8));
        uploadAsset(PROJECT_A, "guide.pdf", "application/pdf", new byte[]{1});
        mockMvc.perform(authenticated(get(assetUrl(PROJECT_B) + "/{id}", asset.get("id").asText())))
                .andExpect(status().isNotFound());
        mockMvc.perform(authenticated(get(assetUrl(PROJECT_B)))).andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(authenticated(get(assetUrl(PROJECT_A)).param("type", "DOCUMENT")))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].originalFilename").value("guide.pdf"));
        mockMvc.perform(authenticated(get(assetUrl(PROJECT_A)).param("q", "KOUR")))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // --- Helpers ---------------------------------------------------------------------------------

    private JsonNode update(JsonNode asset, Map<String, Object> fields, int expected) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>(fields);
        body.put("version", asset.get("version").asLong());
        body.putIfAbsent("internalName", asset.get("internalName").asText());
        return read(mockMvc.perform(json(put(assetUrl(PROJECT_A) + "/{id}", asset.get("id").asText()), body))
                .andExpect(status().is(expected)).andReturn());
    }

    private JsonNode replace(JsonNode asset, String filename, String type, byte[] bytes, int expected) throws Exception {
        return read(mockMvc.perform(authenticated(multipart(assetUrl(PROJECT_A) + "/{id}/file", asset.get("id").asText())
                        .file(new MockMultipartFile("file", filename, type, bytes))
                        .param("version", asset.get("version").asText())))
                .andExpect(status().is(expected)).andReturn());
    }
}
