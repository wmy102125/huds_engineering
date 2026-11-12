package com.hugo.huds.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * @author Meiyan Wang
 * @date 2026-11-12 10:53 a.m.
 */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class S3Config {

    /**
     * Talks to the bucket directly (delete, head, ...).
     */
    @Bean
    public S3Client s3Client(StorageProperties storageProperties) {
        var builder = S3Client.builder().region(Region.of(storageProperties.region()))
                .credentialsProvider(credentialsProvider(storageProperties))
                .serviceConfiguration(s3Configuration(storageProperties));
        if (StringUtils.hasText(storageProperties.endpoint())) {
            builder.endpointOverride(URI.create(storageProperties.endpoint()));
        }
        return builder.build();
    }

    /** Only signs URLs; it never sends a request itself. */
    @Bean
    public S3Presigner s3Presigner(StorageProperties properties) {
        var builder = S3Presigner.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(credentialsProvider(properties))
                .serviceConfiguration(s3Configuration(properties));
        if (StringUtils.hasText(properties.endpoint())) {
            builder.endpointOverride(URI.create(properties.endpoint()));
        }
        return builder.build();
    }

    /**
     * With explicit keys (local MinIO) use them; without keys fall back to the default
     * AWS chain (environment variables, or the IAM role of the server in production).
     */
    private AwsCredentialsProvider credentialsProvider(StorageProperties properties) {
        if (StringUtils.hasText(properties.accessKey()) && StringUtils.hasText(properties.secretKey())) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
        }
        return DefaultCredentialsProvider.builder().build();
    }

    /** MinIO needs path-style URLs (http://host:9000/bucket/key). */
    private S3Configuration s3Configuration(StorageProperties properties) {
        return S3Configuration.builder()
                .pathStyleAccessEnabled(properties.pathStyle())
                .build();
    }
}
