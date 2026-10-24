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
 * JPA entity mapped to the existing {@code appliance_models} table.
 *
 * <p>The database schema is managed by Flyway. Hibernate must not be used
 * to create or update this table. The audit columns are inherited from
 * {@link AuthoredAuditableEntity} and filled automatically.</p>
 */
@Entity
@Table(name = "appliance_models")
@SQLRestriction("archived_at IS NULL")
@Setter
@Getter
public class ApplianceModel extends AuthoredAuditableEntity {

  @Id
  @Column(name = "appliance_model_id", nullable = false, updatable = false)
  private UUID applianceModelId;

  @Column(name = "appliance_type", nullable = false)
  private String applianceType;

  @Column(name = "brand")
  private String brand;

  @Column(name = "model_no")
  private String modelNo;

  @Column(name = "archived_at")
  @Setter(AccessLevel.NONE)
  private Instant archivedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  @Setter(AccessLevel.NONE)
  private Long rowVersion;

  public ApplianceModel() {
    // Required by JPA.
  }

  /**
   * Soft-deletes this appliance model. The UPDATE is issued by dirty checking when the transaction commits.
   *
   * @param now the archive time
   */
  public void archive(Instant now) {
    this.archivedAt = now;
  }
}