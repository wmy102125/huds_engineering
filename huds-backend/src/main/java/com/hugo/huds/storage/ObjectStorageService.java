package com.hugo.huds.storage;

import com.hugo.huds.config.StorageProperties;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The only place talks to object storage. It never sees the file itself:
 * files go straight between client and storage through short-lived singed links.
 *
 * @author Meiyan Wang
 * @date 2026-11-12 12:50 p.m.
 */
@Service
public class ObjectStorageService {

    private static final Duration UPLOAD_LINK_LIFEIME = Duration.ofMinutes(10);
    private static final Duration DOWNLOAD_LINK_LIFETIME = Duration.ofMinutes(5);

    private final S3Client s3Client;
    private final S3Presigner presigner;
    private final String bucket;

    public ObjectStorageService(S3Client s3Client, S3Presigner presigner, StorageProperties properties) {
        this.s3Client = s3Client;
        this.presigner = presigner;
        this.bucket = properties.bucket();
    }

    /**
     * Creates a new key for a file. The key is made by the server and never contains the user's file name.
     *
     * @param prefix the folder of the key, for example "appliance-documents"
     * @return the new object key
     */
    public String newObjectKey(String prefix) {
        return prefix + "/" + UUID.randomUUID();
    }

    /**
     * Create a singed link for uploading files. The content type and the size are part of the signature,
     * so the client cannot send a different kind of file or a bigger one.
     *
     * @param objectKey the key the file will be stored under
     * @param contentType the content type of the file
     * @param byteSize the exact size of the file in bytes
     * @return the link and the headers the client must send with it
     */
    public PresignedUpload presignUpload(String objectKey, String contentType, long byteSize) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(contentType)
                .contentLength(byteSize)
                .build();
        PresignedPutObjectRequest presignedPutObjectRequest = presigner.presignPutObject(
                PutObjectPresignRequest.builder()
                        .signatureDuration(UPLOAD_LINK_LIFEIME)
                        .putObjectRequest(putObjectRequest)
                        .build());
        // The browser sets Host and Content-Length itself, so only the other signed headers are returned.
        Map<String, String> headers = new LinkedHashMap<>();
        presignedPutObjectRequest.signedHeaders().forEach((name, values) -> {
            if (!name.equalsIgnoreCase("host") && !name.equalsIgnoreCase("content-length")) {
                headers.put(name, String.join(",", values));
            }
        });
        return new PresignedUpload(presignedPutObjectRequest.url().toString(), headers, presignedPutObjectRequest.expiration());
    }

    /**
     * Creates a signed link for downloading one file.
     *
     * @param objectKey the key of the file
     * @param fileName  the name the downloaded file should have
     * @return the link, valid for a few minutes
     */
    public PresignedDownload presignDownload(String objectKey, String fileName) {
        // filename* is the form that allows any characters; the name is percent-encoded so it cannot break the header.
        String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");

        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .responseContentDisposition("inline; filename*=UTF-8''" + encodedName)
                .build();
        PresignedGetObjectRequest presigned = presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(DOWNLOAD_LINK_LIFETIME)
                .getObjectRequest(getRequest)
                .build());
        return new PresignedDownload(presigned.url().toString(), presigned.expiration());
    }

    /**
     * Looks up a stored file, used to confirm that an upload really finished.
     *
     * @param objectKey the key of the file
     * @return the size and content type, or empty if there is no such file
     */
    public Optional<StoredObject> head(String objectKey) {
        try {
            HeadObjectResponse response = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build());
            return Optional.of(new StoredObject(response.contentLength(), response.contentType()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }
}
