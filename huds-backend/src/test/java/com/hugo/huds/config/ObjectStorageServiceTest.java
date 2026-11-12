package com.hugo.huds.config;

import com.hugo.huds.storage.ObjectStorageService;
import com.hugo.huds.storage.PresignedUpload;
import com.hugo.huds.storage.StoredObject;
import com.hugo.huds.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

/**
 * @author Meiyan Wang
 * @date 2026-11-12 1:12 p.m.
 */
@SpringBootTest
@ActiveProfiles("local")
public class ObjectStorageServiceTest extends AbstractIntegrationTest {
    @Autowired
    private ObjectStorageService storage;

    @Autowired
    private S3Client s3Client;

    @Autowired
    private StorageProperties properties;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void uploadConfirmDownload() throws Exception {
        byte[] content = "hello signed link".getBytes(StandardCharsets.UTF_8);
        String key = storage.newObjectKey("smoke-test");

        try {
            // 1. The server signs a link for this exact size and type.
            PresignedUpload upload = storage.presignUpload(key, "text/plain", content.length);

            // 2. The client sends the file straight to storage, with the headers it was given.
            HttpRequest.Builder put = HttpRequest.newBuilder(URI.create(upload.url()))
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(content));
            upload.headers().forEach(put::header);
            HttpResponse<String> putResponse = http.send(put.build(), HttpResponse.BodyHandlers.ofString());
            assertThat(putResponse.statusCode())
                    .as("upload response: %s", putResponse.body())
                    .isBetween(200, 299);

            // 3. The server confirms that the file is there and has the declared size.
            Optional<StoredObject> stored = storage.head(key);
            assertThat(stored).isPresent();
            assertThat(stored.get().byteSize()).isEqualTo(content.length);
            assertThat(stored.get().contentType()).isEqualTo("text/plain");

            // 4. The client downloads through a signed link.
            HttpRequest get = HttpRequest.newBuilder(URI.create(storage.presignDownload(key, "hello.txt").url())).GET().build();
            HttpResponse<String> getResponse = http.send(get, HttpResponse.BodyHandlers.ofString());
            assertThat(getResponse.statusCode()).isEqualTo(200);
            assertThat(getResponse.body()).isEqualTo("hello signed link");
        } finally {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(properties.bucket()).key(key).build());
        }
    }

    @Test
    void uploadOfADifferentSizeIsRejected() throws Exception {
        String key = storage.newObjectKey("smoke-test");

        try {
            // The link is signed for 5 bytes, but 20 bytes are sent.
            PresignedUpload upload = storage.presignUpload(key, "text/plain", 5);
            byte[] bigger = "this is more than five".getBytes(StandardCharsets.UTF_8);

            HttpRequest.Builder put = HttpRequest.newBuilder(URI.create(upload.url()))
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(bigger));
            upload.headers().forEach(put::header);
            HttpResponse<String> response = http.send(put.build(), HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isGreaterThanOrEqualTo(400);
            assertThat(storage.head(key)).isEmpty();
        } finally {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(properties.bucket()).key(key).build());
        }
    }
}
