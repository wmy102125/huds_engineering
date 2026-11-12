package com.hugo.huds.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the S3-compatible object storage (MinIO locally, AWS S3 in production).
 * An empty endpoint means "use the real AWS endpoint of the region".
 *
 * @author Meiyan Wang
 * @date 2026-11-12 10:51 a.m.
 */
@ConfigurationProperties(prefix = "huds.storage")
public record StorageProperties(
        String endpoint,
        @DefaultValue("ca-central-1")
        String region,
        String bucket,
        String accessKey,
        String secretKey,
        @DefaultValue("false")
        boolean pathStyle
) {

}
