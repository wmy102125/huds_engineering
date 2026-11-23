package com.hugo.huds.attatchment;

import com.hugo.huds.config.StorageProperties;
import com.hugo.huds.attachment.dto.AttachmentEntityType;
import com.hugo.huds.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


/**
 * @author Meiyan Wang
 * @date 2026-11-13 3:21 p.m.
 */
public class AttachmentIntegrationTest extends AbstractIntegrationTest {
    private static final String ATTACHMENT = "/api/v1/attachments";
    private static final String PHOTO_TYPE = "image/png";

    @Autowired
    private S3Client s3Client;

    @Autowired
    private StorageProperties storageProperties;

    private final HttpClient http = HttpClient.newHttpClient();

    /** A registered file together with its bytes, so a test can upload exactly what it announced. */
    private record Photo(UUID id, byte[] content) {
    }

    // ---------- registering ----------

    @Test
    void registerCreatesAttachment() throws Exception {
        UUID attachmentId = UUID.randomUUID();
        byte[] content = randomBytes(2048);

        MvcResult result = mockMvc.perform(authed(post(ATTACHMENT + "/" + attachmentId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request(newCase(), "photo.png", PHOTO_TYPE, content.length, sha256Hex(content)))))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.attachmentId").value(attachmentId.toString()))
                .andExpect(jsonPath("$.mimeType").value(PHOTO_TYPE))
                .andExpect(jsonPath("$.mediaKind").value("PHOTO"))
                .andExpect(jsonPath("$.uploadStatus").value("PENDING"))
                .andReturn();

        UUID id = UUID.fromString(jsonOf(result).get("attachmentId").asText());
        Long rows = jdbc.queryForObject("select count(*) from attachments where attachment_id = ?", Long.class, id);
        assertEquals(1, rows);
    }

    @Test
    void registeringTheSameAttachmentTwiceWithTheSameContentIsSafe() throws Exception {
        UUID attachmentId = UUID.randomUUID();
        byte[] content = randomBytes(2048);
        String body = json(request(newCase(), "photo.png", PHOTO_TYPE, content.length, sha256Hex(content)));

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(authed(post(ATTACHMENT + "/" + attachmentId))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.attachmentId").value(attachmentId.toString()));
        }

        Long rows = jdbc.queryForObject("select count(*) from attachments where attachment_id = ?", Long.class, attachmentId);
        assertEquals(1, rows);
    }

    @Test
    void sameIdWithDifferentContentReturns409() throws Exception {
        UUID attachmentId = UUID.randomUUID();
        UUID caseId = newCase();
        byte[] first = randomBytes(2048);
        byte[] second = randomBytes(2048);

        mockMvc.perform(authed(post(ATTACHMENT + "/" + attachmentId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request(caseId, "photo.png", PHOTO_TYPE, first.length, sha256Hex(first)))))
                .andExpect(status().isCreated());

        mockMvc.perform(authed(post(ATTACHMENT + "/" + attachmentId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request(caseId, "photo.png", PHOTO_TYPE, second.length, sha256Hex(second)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
    }

    @Test
    void unknownTargetRecordReturns404() throws Exception {
        byte[] content = randomBytes(2048);

        mockMvc.perform(authed(post(ATTACHMENT + "/" + UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request(UUID.randomUUID(), "photo.png", PHOTO_TYPE, content.length, sha256Hex(content)))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void unsupportedFileTypeReturns400() throws Exception {
        byte[] content = randomBytes(2048);

        mockMvc.perform(authed(post(ATTACHMENT + "/" + UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request(newCase(), "manual.pdf", "application/pdf", content.length, sha256Hex(content)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAYLOAD"));
    }

    @Test
    void tooLargePhotoReturns400() throws Exception {
        long overTheLimit = 15L * 1024 * 1024 + 1;

        mockMvc.perform(authed(post(ATTACHMENT + "/" + UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request(newCase(), "big.png", PHOTO_TYPE, overTheLimit, sha256Hex(randomBytes(16))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAYLOAD"));
    }

    @Test
    void aLargeVideoWithinTheVideoLimitIsAccepted() throws Exception {
        // The size is only announced here, nothing is uploaded.
        long twentyMegabytes = 20L * 1024 * 1024;

        mockMvc.perform(authed(post(ATTACHMENT + "/" + UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request(newCase(), "clip.mp4", "video/mp4", twentyMegabytes, sha256Hex(randomBytes(16))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mediaKind").value("VIDEO"));
    }

    @Test
    void anEmptyFileReturns400() throws Exception {
        mockMvc.perform(authed(post(ATTACHMENT + "/" + UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request(newCase(), "empty.png", PHOTO_TYPE, 0, sha256Hex(randomBytes(16))))))
                .andExpect(status().isBadRequest());
    }

    // ---------- asking for the upload link ----------

    @Test
    void requestUploadReturnsASignedLinkAndCanBeRepeated() throws Exception {
        Photo photo = registerPhoto("photo.png");

        assertTrue(uploadLink(photo.id()).startsWith("http"));
        assertTrue(uploadLink(photo.id()).startsWith("http"));

        String status = jdbc.queryForObject("select upload_status from attachments where attachment_id = ?", String.class, photo.id());
        assertEquals("PENDING", status);
    }

    @Test
    void requestUploadOfAnUnknownAttachmentReturns404() throws Exception {
        mockMvc.perform(authed(post(ATTACHMENT + "/" + UUID.randomUUID() + "/upload-url")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // ---------- confirming the upload ----------

    @Test
    void completeUploadBeforeTheFileArrivedReturns422() throws Exception {
        Photo photo = registerPhoto("photo.png");

        mockMvc.perform(authed(post(ATTACHMENT + "/" + photo.id() + "/complete-upload")))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("DOMAIN_RULE_VIOLATION"));
    }

    @Test
    void completeUploadWithAFileOfAnotherSizeReturns422() throws Exception {
        Photo photo = registerPhoto("photo.png");

        // Put a smaller file under the announced key, as if the client had uploaded the wrong file.
        s3Client.putObject(PutObjectRequest.builder()
                        .bucket(storageProperties.bucket())
                        .key(objectKeyOf(photo.id()))
                        .contentType(PHOTO_TYPE)
                        .build(),
                RequestBody.fromBytes(randomBytes(10)));

        mockMvc.perform(authed(post(ATTACHMENT + "/" + photo.id() + "/complete-upload")))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("DOMAIN_RULE_VIOLATION"));

        String status = jdbc.queryForObject("select upload_status from attachments where attachment_id = ?", String.class, photo.id());
        assertEquals("PENDING", status);
    }

    @Test
    void uploadCompleteAndDownloadGiveTheFileBackWithItsName() throws Exception {
        Photo photo = registerPhoto("boiler-room.png");

        // 1. Send the file straight to the signed link.
        putToLink(uploadLink(photo.id()), PHOTO_TYPE, photo.content());

        // 2. Confirm. The second call changes nothing and still succeeds.
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(authed(post(ATTACHMENT + "/" + photo.id() + "/complete-upload")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.uploadStatus").value("UPLOADED"));
        }

        // 3. Download it again and compare the bytes.
        MvcResult link = mockMvc.perform(authed(get(ATTACHMENT + "/" + photo.id() + "/download-url")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").exists())
                .andReturn();
        HttpResponse<byte[]> download = http.send(
                HttpRequest.newBuilder(URI.create(jsonOf(link).get("url").asText())).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());

        assertEquals(200, download.statusCode());
        assertArrayEquals(photo.content(), download.body());
        assertTrue(download.headers().firstValue("Content-Disposition").orElse("").contains("boiler-room.png"));
    }

    @Test
    void requestUploadAfterTheFileIsUploadedReturns422() throws Exception {
        Photo photo = registerPhoto("photo.png");
        putToLink(uploadLink(photo.id()), PHOTO_TYPE, photo.content());
        mockMvc.perform(authed(post(ATTACHMENT + "/" + photo.id() + "/complete-upload")))
                .andExpect(status().isOk());

        mockMvc.perform(authed(post(ATTACHMENT + "/" + photo.id() + "/upload-url")))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("DOMAIN_RULE_VIOLATION"));
    }

    // ---------- downloading ----------

    @Test
    void downloadLinkBeforeTheFileArrivedReturns422() throws Exception {
        Photo photo = registerPhoto("photo.png");

        mockMvc.perform(authed(get(ATTACHMENT + "/" + photo.id() + "/download-url")))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("DOMAIN_RULE_VIOLATION"));
    }

    // ---------- helpers ----------

    private Map<String, Object> request(UUID entityId, String fileName, String mimeType, long byteSize, String sha256Hex) {
        return Map.of("entityType", AttachmentEntityType.CASE.name(),
                "entityId", entityId.toString(),
                "fileName", fileName,
                "mimeType", mimeType,
                "byteSize", byteSize,
                "sha256Hex", sha256Hex,
                "capturedAt", Instant.now().toString());
    }

    /** Registers a new photo on a new case and returns it with the bytes it announced. */
    private Photo registerPhoto(String fileName) throws Exception {
        UUID attachmentId = UUID.randomUUID();
        byte[] content = randomBytes(2048);
        mockMvc.perform(authed(post(ATTACHMENT + "/" + attachmentId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request(newCase(), fileName, PHOTO_TYPE, content.length, sha256Hex(content)))))
                .andExpect(status().isCreated());
        return new Photo(attachmentId, content);
    }

    private String uploadLink(UUID attachmentId) throws Exception {
        MvcResult result = mockMvc.perform(authed(post(ATTACHMENT + "/" + attachmentId + "/upload-url")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").exists())
                .andReturn();
        return jsonOf(result).get("url").asText();
    }

    /** Sends the bytes to the signed link the way a browser would. */
    private void putToLink(String url, String contentType, byte[] content) throws Exception {
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create(url))
                        .header("Content-Type", contentType)
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(content))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
    }

    private String objectKeyOf(UUID attachmentId) {
        return jdbc.queryForObject("select object_key from attachments where attachment_id = ?", String.class, attachmentId);
    }

    private static byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        ThreadLocalRandom.current().nextBytes(bytes);
        return bytes;
    }

    private static String sha256Hex(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    private UUID newCase() {
        UUID adminUserId = jdbc.queryForObject(
                "SELECT user_id FROM users WHERE user_name = ? ORDER BY created_at DESC LIMIT 1",
                UUID.class, "Test ADMIN");
        return createCase(adminUserId);
    }

    /**
     * Inserts a customer, address, appliance model, appliance and case with only the required columns.
     * Every value is random, so tests never clash with each other in the shared test database.
     *
     * @param userId an existing user, used as created_by and updated_by
     * @return the id of the new case
     */
    protected UUID createCase(UUID userId) {
        UUID customerId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        UUID modelId = UUID.randomUUID();
        UUID applianceId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();

        jdbc.update("INSERT INTO customers (customer_id, customer_name, created_by, updated_by) VALUES (?, ?, ?, ?)",
                customerId, "Test Customer " + customerId, userId, userId);
        jdbc.update("INSERT INTO service_addresses (service_address_id, customer_id, address_line1, city, created_by, updated_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                addressId, customerId, "1 Test Street", "Sydney", userId, userId);
        jdbc.update("INSERT INTO appliance_models (appliance_model_id, appliance_type, model_no, created_by, updated_by) "
                        + "VALUES (?, ?, ?, ?, ?)",
                modelId, "WASHER", "MODEL-" + modelId, userId, userId);
        jdbc.update("INSERT INTO appliances (appliance_id, customer_id, appliance_model_id, created_by, updated_by) "
                        + "VALUES (?, ?, ?, ?, ?)",
                applianceId, customerId, modelId, userId, userId);
        jdbc.update("INSERT INTO diagnostic_cases (diagnostic_case_id, case_no, appliance_id, customer_id, service_address_id, "
                        + "reported_symptoms, created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                caseId, "TEST-" + caseId, applianceId, customerId, addressId, "Test symptom", userId, userId);
        return caseId;
    }
}
