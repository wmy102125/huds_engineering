package com.hugo.huds.appliance;

import com.hugo.huds.config.StorageProperties;
import com.hugo.huds.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


/**
 * @author Meiyan Wang
 * @date 2026-11-12 1:46 p.m.
 */
public class ApplianceDocumentIntegrationTest  extends AbstractIntegrationTest {

    private static final String DOCUMENTS = "/api/v1/appliance-documents";
    @Autowired
    private S3Client s3Client;

    @Autowired
    private StorageProperties storageProperties;

    private final HttpClient http = HttpClient.newHttpClient();
    private Map<String, Object> request(String documentRef) {
        return Map.of("documentType", "SERVICE_MANUAL", "documentRef", documentRef);
    }
    @Test
    void registerCreatesADocumentWithoutAFile() throws Exception {
        String ref = "REF-" + UUID.randomUUID();

        MvcResult result = mockMvc.perform(authed(post(DOCUMENTS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request(ref))))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.documentType").value("SERVICE_MANUAL"))
                .andExpect(jsonPath("$.documentRef").value(ref))
                .andExpect(jsonPath("$.uploadStatus").value("NO_FILE"))
                .andReturn();

        UUID id = UUID.fromString(jsonOf(result).get("applianceDocumentId").asText());
        Long rows = jdbc.queryForObject(
                "select count(*) from appliance_documents where appliance_document_id = ?", Long.class, id);
        assertEquals(1, rows);
    }

    @Test
    void registeringTheSameDocumentTwiceReturns409() throws Exception {
        String ref = "REF-" + UUID.randomUUID();
        mockMvc.perform(authed(post(DOCUMENTS)).contentType(MediaType.APPLICATION_JSON).content(json(request(ref))))
                .andExpect(status().isCreated());

        // The reference is compared without regard to case, so a different spelling is the same document.
        mockMvc.perform(authed(post(DOCUMENTS)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(request(ref.toLowerCase()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
    }

    @Test
    void blankReferenceReturns400() throws Exception {
        mockMvc.perform(authed(post(DOCUMENTS)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(request("   "))))
                .andExpect(status().isBadRequest());
    }

    private UUID registerDocument() throws Exception {
        MvcResult result = mockMvc.perform(authed(post(DOCUMENTS)).contentType(MediaType.APPLICATION_JSON)
                        .content(json( Map.of( "documentType", "SERVICE_MANUAL", "documentRef", "REF-" + UUID.randomUUID(),"mimeType","application/pdf"))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(jsonOf(result).get("applianceDocumentId").asText());
    }

    private Map<String, Object> uploadRequest(String mimeType, long byteSize) {
        return Map.of("fileName", "manual.pdf", "mimeType", mimeType, "byteSize", byteSize,
                "sha256Hex", "a".repeat(64));
    }

    @Test
    void requestUploadReturnsASignedLinkAndMakesTheDocumentPending() throws Exception {
        UUID id = registerDocument();

        mockMvc.perform(authed(post(DOCUMENTS + "/" + id + "/upload-url"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(uploadRequest("application/pdf", 1024))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document.uploadStatus").value("PENDING"))
                .andExpect(jsonPath("$.document.fileName").value("manual.pdf"))
                .andExpect(jsonPath("$.upload.url").exists())
                .andExpect(jsonPath("$.upload.expiresAt").exists());

        String status = jdbc.queryForObject(
                "select upload_status from appliance_documents where appliance_document_id = ?", String.class, id);
        assertEquals("PENDING", status);
    }

    @Test
    void requestUploadCanBeRepeatedWhileStillPending() throws Exception {
        UUID id = registerDocument();
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(authed(post(DOCUMENTS + "/" + id + "/upload-url"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(uploadRequest("application/pdf", 1024))))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void unsupportedFileTypeReturns400() throws Exception {
        UUID id = registerDocument();
        mockMvc.perform(authed(post(DOCUMENTS + "/" + id + "/upload-url"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(uploadRequest("application/zip", 1024))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAYLOAD"));
    }

    @Test
    void tooLargeFileReturns400() throws Exception {
        UUID id = registerDocument();
        mockMvc.perform(authed(post(DOCUMENTS + "/" + id + "/upload-url"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(uploadRequest("application/pdf", 26L * 1024 * 1024))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requestUploadForUnknownDocumentReturns404() throws Exception {
        mockMvc.perform(authed(post(DOCUMENTS + "/" + UUID.randomUUID() + "/upload-url"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(uploadRequest("application/pdf", 1024))))
                .andExpect(status().isNotFound());
    }
    /** Requests an upload link for a PDF of the given size and returns the link part of the response. */
    private JsonNode requestUploadLink(UUID id, int byteSize) throws Exception {
        MvcResult result = mockMvc.perform(authed(post(DOCUMENTS + "/" + id + "/upload-url"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(uploadRequest("application/pdf", byteSize))))
                .andExpect(status().isOk())
                .andReturn();
        return jsonOf(result).get("upload");
    }

    /** Sends the bytes to the signed link the way a browser would. */
    private int putFile(JsonNode upload, byte[] content) throws Exception {
        HttpRequest.Builder put = HttpRequest.newBuilder(URI.create(upload.get("url").asText()))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(content));
        upload.get("headers").properties().forEach(header -> put.header(header.getKey(), header.getValue().asText()));
        return http.send(put.build(), HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    private void deleteStoredFile(UUID id) {
        String key = jdbc.queryForObject(
                "select object_key from appliance_documents where appliance_document_id = ?", String.class, id);
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(storageProperties.bucket()).key(key).build());
    }

    @Test
    void completeUploadMarksTheDocumentUploadedOnceTheFileIsInStorage() throws Exception {
        UUID id = registerDocument();
        byte[] content = new byte[1024];
        JsonNode upload = requestUploadLink(id, content.length);
        try {
            assertEquals(200, putFile(upload, content));

            mockMvc.perform(authed(post(DOCUMENTS + "/" + id + "/complete-upload")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.uploadStatus").value("UPLOADED"));

            // A retried confirmation changes nothing.
            mockMvc.perform(authed(post(DOCUMENTS + "/" + id + "/complete-upload")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.uploadStatus").value("UPLOADED"));
        } finally {
            deleteStoredFile(id);
        }
    }

    @Test
    void completeUploadBeforeTheFileArrivedReturns422() throws Exception {
        UUID id = registerDocument();
        requestUploadLink(id, 1024);

        mockMvc.perform(authed(post(DOCUMENTS + "/" + id + "/complete-upload")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DOMAIN_RULE_VIOLATION"));

        String status = jdbc.queryForObject(
                "select upload_status from appliance_documents where appliance_document_id = ?", String.class, id);
        assertEquals("PENDING", status);
    }

    @Test
    void completeUploadWithoutRequestingAnUploadReturns422() throws Exception {
        UUID id = registerDocument();

        mockMvc.perform(authed(post(DOCUMENTS + "/" + id + "/complete-upload")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void downloadLinkGivesTheFileBackWithItsOriginalName() throws Exception {
        UUID id = registerDocument();
        byte[] content = "manual content".getBytes(StandardCharsets.UTF_8);
        JsonNode upload = requestUploadLink(id, content.length);
        try {
            assertEquals(200, putFile(upload, content));
            mockMvc.perform(authed(post(DOCUMENTS + "/" + id + "/complete-upload"))).andExpect(status().isOk());

            MvcResult result = mockMvc.perform(authed(MockMvcRequestBuilders.get(DOCUMENTS + "/" + id + "/download-url")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.url").exists())
                    .andExpect(jsonPath("$.expiresAt").exists())
                    .andReturn();

            HttpRequest download = HttpRequest.newBuilder(URI.create(jsonOf(result).get("url").asText())).GET().build();
            HttpResponse<String> response = http.send(download, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertEquals("manual content", response.body());
            assertTrue(response.headers().firstValue("Content-Disposition").orElse("").contains("manual.pdf"));
        } finally {
            deleteStoredFile(id);
        }
    }

    @Test
    void downloadLinkOfADocumentWithoutAFileReturns422() throws Exception {
        UUID id = registerDocument();

        mockMvc.perform(authed(MockMvcRequestBuilders.get(DOCUMENTS + "/" + id + "/download-url")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void downloadLinkWithoutLoginReturns401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get(DOCUMENTS + "/" + UUID.randomUUID() + "/download-url"))
                .andExpect(status().isUnauthorized());
    }
}
