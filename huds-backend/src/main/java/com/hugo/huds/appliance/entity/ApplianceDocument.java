package com.hugo.huds.appliance.entity;

import com.hugo.huds.appliance.dto.ApplianceDocumentType;
import com.hugo.huds.appliance.dto.ApplianceDocumentUploadStatus;
import com.hugo.huds.auth.entity.AuthoredAuditableEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * JPA entity mapped to the existing {@code appliance_documents} table.
 *
 * <p>The database schema is managed by Flyway. Hibernate must not be used
 * to create or update this table.</p>
 */
@Entity
@Table(name = "appliance_documents")
@Setter
@Getter
public class ApplianceDocument extends AuthoredAuditableEntity {

  @Id
  @Column(name = "appliance_document_id", nullable = false, updatable = false)
  private UUID applianceDocumentId;

  @Column(name = "document_type", nullable = false)
  @Enumerated(EnumType.STRING)
  private ApplianceDocumentType documentType;

  @Column(name = "document_ref", nullable = false)
  private String documentRef;

  @Column(name = "object_key", unique = true)
  private String objectKey;

  @Column(name = "file_name")
  private String fileName;

  @Column(name = "mime_type")
  private String mimeType;

  @Column(name = "byte_size")
  private Long byteSize;

  @Column(name = "sha256_hex")
  private String sha256Hex;

  @Column(name = "upload_status", nullable = false)
  @Enumerated(EnumType.STRING)
  private ApplianceDocumentUploadStatus uploadStatus = ApplianceDocumentUploadStatus.NO_FILE;

  @Version
  @Column(name = "row_version", nullable = false)
  @Setter(AccessLevel.NONE)
  private Long rowVersion;

  public ApplianceDocument() {
    // Required by JPA.
  }


}
