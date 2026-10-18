package com.hugo.huds.appliance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapped to the existing {@code appliance_location_history} table.
 *
 * <p>The database schema is managed by Flyway. Hibernate must not be used
 * to create or update this table.</p>
 */
@Entity
@Table(name = "appliance_location_history")
public class ApplianceLocationHistory {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "location_history_id", nullable = false, updatable = false)
  private UUID locationHistoryId;

  @Column(name = "appliance_id", nullable = false)
  private UUID applianceId;

  @Column(name = "service_address_id")
  private UUID serviceAddressId;

  @Column(name = "customer_id")
  private UUID customerId;

  @Column(name = "valid_from", nullable = false)
  private Instant validFrom;

  @Column(name = "valid_to")
  private Instant validTo;

  @Column(name = "note")
  private String note;

  public ApplianceLocationHistory() {
    // Required by JPA.
  }

  public UUID getLocationHistoryId() {
    return locationHistoryId;
  }

  public void setLocationHistoryId(UUID locationHistoryId) {
    this.locationHistoryId = locationHistoryId;
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

  public UUID getCustomerId() {
    return customerId;
  }

  public void setCustomerId(UUID customerId) {
    this.customerId = customerId;
  }

  public Instant getValidFrom() {
    return validFrom;
  }

  public void setValidFrom(Instant validFrom) {
    this.validFrom = validFrom;
  }

  public Instant getValidTo() {
    return validTo;
  }

  public void setValidTo(Instant validTo) {
    this.validTo = validTo;
  }

  public String getNote() {
    return note;
  }

  public void setNote(String note) {
    this.note = note;
  }

}
