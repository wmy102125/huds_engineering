package com.hugo.huds.appliance.entity;

import com.hugo.huds.auth.persistence.AuthoredAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

/**
 * JPA entity mapped to the existing {@code appliances} table.
 *
 * <p>The database schema is managed by Flyway. Hibernate must not be used
 * to create or update this table. The audit columns are inherited from
 * {@link AuthoredAuditableEntity} and filled automatically.</p>
 */
@Entity
@Table(name = "appliances")
@SQLRestriction("archived_at IS NULL")
@Getter
@Setter
public class Appliance extends AuthoredAuditableEntity {

  @Id
  @Column(name = "appliance_id", nullable = false, updatable = false)
  private UUID applianceId;

  @Column(name = "appliance_model_id", nullable = false)
  private UUID applianceModelId;

  @Column(name = "serial_no")
  private String serialNo;

  @Column(name = "serial_verified", nullable = false)
  private Boolean serialVerified = false;

  @Column(name = "identity_notes")
  private String identityNotes;

  @Column(name = "archived_at")
  @Setter(AccessLevel.NONE)
  private Instant archivedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  @Setter(AccessLevel.NONE)
  private Long rowVersion;

  public Appliance() {
    // Required by JPA.
  }

  /**
   * Soft-deletes this appliance. The UPDATE is issued by dirty checking when the transaction commits.
   *
   * @param now the archive time
   */
  public void archive(Instant now) {
    this.archivedAt = now;
  }
}