package com.hugo.huds.attachment.entity;

import com.hugo.huds.auth.entity.AuthoredAuditableEntity;
import com.hugo.huds.attachment.dto.AttachmentEntityType;
import com.hugo.huds.attachment.dto.AttachmentKind;
import com.hugo.huds.attachment.dto.AttachmentUploadStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapped to the existing {@code diagnostic_media} table.
 *
 * <p>The database schema is managed by Flyway. Hibernate must not be used
 * to create or update this table.</p>
 */
@Entity
@Table(name = "attachments")
@Setter
@Getter
public class Attachment extends AuthoredAuditableEntity {

  @Id
  @Column(name = "attachment_id", nullable = false, updatable = false)
  private UUID attachmentId;

  @Column(name = "entity_type", nullable = false)
  @Enumerated(EnumType.STRING)
  private AttachmentEntityType entityType;

  @Column(name = "entity_id", nullable = false)
  private UUID entityId;

  @Column(name = "media_kind", nullable = false)
  @Enumerated(EnumType.STRING)
  private AttachmentKind mediaKind;

  @Column(name = "object_key", nullable = false, unique = true)
  private String objectKey;

  @Column(name = "thumbnail_object_key")
  private String thumbnailObjectKey;

  @Column(name = "file_name", nullable = false)
  private String fileName;

  @Column(name = "mime_type", nullable = false)
  private String mimeType;

  @Column(name = "byte_size", nullable = false)
  private long byteSize;

  @Column(name = "sha256_hex", nullable = false)
  private String sha256Hex;

  @Column(name = "upload_status", nullable = false)
  @Enumerated(EnumType.STRING)
  private AttachmentUploadStatus uploadStatus = AttachmentUploadStatus.PENDING;

  @Column(name = "captured_at")
  private Instant capturedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  @Setter(AccessLevel.NONE)
  private Long rowVersion;

  public Attachment() {
    // Required by JPA.
  }


}
