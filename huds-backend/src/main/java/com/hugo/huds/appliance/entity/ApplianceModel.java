package com.hugo.huds.appliance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapped to the existing {@code appliance_models} table.
 *
 * <p>The database schema is managed by Flyway. Hibernate must not be used
 * to create or update this table.</p>
 */
@Entity
@Table(name = "appliance_models")
public class ApplianceModel {

  @Id
  @Column(name = "appliance_model_id", nullable = false, updatable = false)
  private UUID applianceModelId;

  @Column(name = "appliance_type", nullable = false)
  private String applianceType;

  @Column(name = "brand")
  private String brand;

  @Column(name = "model_no")
  private String modelNo;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "updated_by", nullable = false)
  private UUID updatedBy;

  @Column(name = "archived_at")
  private Instant archivedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private Long rowVersion;

  public ApplianceModel() {
    // Required by JPA.
  }

  /**
   * Soft-deletes this address. The UPDATE is issued by dirty checking when the transaction commits.
   *
   * @param now the archive time
   */
  public void archive(Instant now) {
    this.archivedAt = now;
  }

  public UUID getApplianceModelId() {
    return applianceModelId;
  }

  public void setApplianceModelId(UUID applianceModelId) {
    this.applianceModelId = applianceModelId;
  }

  public String getApplianceType() {
    return applianceType;
  }

  public void setApplianceType(String applianceType) {
    this.applianceType = applianceType;
  }

  public String getBrand() {
    return brand;
  }

  public void setBrand(String brand) {
    this.brand = brand;
  }

  public String getModelNo() {
    return modelNo;
  }

  public void setModelNo(String modelNo) {
    this.modelNo = modelNo;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(UUID createdBy) {
    this.createdBy = createdBy;
  }

  public UUID getUpdatedBy() {
    return updatedBy;
  }

  public void setUpdatedBy(UUID updatedBy) {
    this.updatedBy = updatedBy;
  }

  public Instant getArchivedAt() {
    return archivedAt;
  }

  public Long getRowVersion() {
    return rowVersion;
  }

}
