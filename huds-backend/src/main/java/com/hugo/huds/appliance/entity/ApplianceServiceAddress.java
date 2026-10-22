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
 * JPA entity mapped to the existing {@code appliance_service_addresses} table.
 *
 * <p>The database schema is managed by Flyway. Hibernate must not be used
 * to create or update this table.</p>
 */
@Entity
@Table(name = "appliance_service_addresses")
public class ApplianceServiceAddress {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "appliance_service_address_id", nullable = false, updatable = false)
  private UUID applianceServiceAddressId;

  @Column(name = "appliance_id", nullable = false)
  private UUID applianceId;

  @Column(name = "service_address_id", nullable = false)
  private UUID serviceAddressId;

  @Column(name = "note")
  private String note;

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

  public ApplianceServiceAddress() {
    // Required by JPA.
  }

  public UUID getApplianceServiceAddressId() {
    return applianceServiceAddressId;
  }

  public void setApplianceServiceAddressId(UUID applianceServiceAddressId) {
    this.applianceServiceAddressId = applianceServiceAddressId;
  }

  public UUID getApplianceId() {
    return applianceId;
  }

  public void setApplianceId(UUID applianceId) {
    this.applianceId = applianceId;
  }

  public UUID getServiceAddressId() {
    return serviceAddressId;
  }

  public void setServiceAddressId(UUID serviceAddressId) {
    this.serviceAddressId = serviceAddressId;
  }

  public String getNote() {
    return note;
  }

  public void setNote(String note) {
    this.note = note;
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
