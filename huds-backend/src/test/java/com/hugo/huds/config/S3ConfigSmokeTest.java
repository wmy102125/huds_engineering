package com.hugo.huds.config;

import com.hugo.huds.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

/**
 * @author Meiyan Wang
 * @date 2026-11-12 11:05 a.m.
 */
@SpringBootTest
@ActiveProfiles("local")
public class S3ConfigSmokeTest extends AbstractIntegrationTest {
    @Autowired
    private S3Client s3Client;

    @Autowired
    private StorageProperties properties;

//    @Test
//    void putGetDeleteObject() {
//        String key = "smoke-test/hello.txt";
//
//        s3Client.putObject(
//                PutObjectRequest.builder().bucket(properties.bucket()).key(key).contentType("text/plain").build(),
//                RequestBody.fromString("hello minio"));
//
//        String content = s3Client.getObjectAsBytes(
//                GetObjectRequest.builder().bucket(properties.bucket()).key(key).build()).asUtf8String();
//        assertThat(content).isEqualTo("hello minio");
//
//        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(properties.bucket()).key(key).build());
//    }
}
