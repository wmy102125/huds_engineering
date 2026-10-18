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
 * JPA entity mapped to the existing {@code appliances} table.
 *
 * <p>The database schema is managed by Flyway. Hibernate must not be used
 * to create or update this table.</p>
 */
@Entity
@Table(name = "appliances")
public class Appliance {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "appliance_id", nullable = false, updatable = false)
  private UUID applianceId;

  @Column(name = "appliance_type", nullable = false)
  private String applianceType;

  @Column(name = "brand")
  private String brand;

  @Column(name = "model_no")
  private String modelNo;

  @Column(name = "serial_no")
  private String serialNo;

  @Column(name = "serial_verified", nullable = false)
  private Boolean serialVerified = false;

  @Column(name = "identity_notes")
  private String identityNotes;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "archived_at")
  private Instant archivedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private Long rowVersion;

  public Appliance() {
    // Required by JPA.
  }

  public UUID getApplianceId() {
    return applianceId;
  }

  public void setApplianceId(UUID applianceId) {
    this.applianceId = applianceId;
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

  public String getSerialNo() {
    return serialNo;
  }

  public void setSerialNo(String serialNo) {
    this.serialNo = serialNo;
  }

  public Boolean getSerialVerified() {
    return serialVerified;
  }

  public void setSerialVerified(Boolean serialVerified) {
    this.serialVerified = serialVerified;
  }

  public String getIdentityNotes() {
    return identityNotes;
  }

  public void setIdentityNotes(String identityNotes) {
    this.identityNotes = identityNotes;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getArchivedAt() {
    return archivedAt;
  }

  public void setArchivedAt(Instant archivedAt) {
    this.archivedAt = archivedAt;
  }

  public Long getRowVersion() {
    return rowVersion;
  }

}
