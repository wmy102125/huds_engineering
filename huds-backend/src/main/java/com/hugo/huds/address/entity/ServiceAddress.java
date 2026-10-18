package com.hugo.huds.address.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapped to the existing {@code service_addresses} table.
 *
 * <p>The database schema is managed by Flyway. Hibernate must not be used
 * to create or update this table.</p>
 */
@Entity
@Table(name = "service_addresses")
public class ServiceAddress {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "service_address_id", nullable = false, updatable = false)
  private UUID serviceAddressId;

  @Column(name = "customer_id")
  private UUID customerId;

  @Column(name = "address_line1", nullable = false)
  private String addressLine1;

  @Column(name = "address_line2")
  private String addressLine2;

  @Column(name = "city", nullable = false)
  private String city;

  @Column(name = "province", nullable = false)
  private String province;

  @Column(name = "postal_code")
  private String postalCode;

  @Column(name = "country_code", nullable = false)
  private String countryCode;

  @Column(name = "is_default", nullable = false)
  private Boolean isDefault = false;

  @Version
  @Column(name = "row_version", nullable = false)
  private Long rowVersion;

  @Column(name = "archived_at")
  private Instant archivedAt;

  public ServiceAddress() {
    // Required by JPA.
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

  public String getAddressLine1() {
    return addressLine1;
  }

  public void setAddressLine1(String addressLine1) {
    this.addressLine1 = addressLine1;
  }

  public String getAddressLine2() {
    return addressLine2;
  }

  public void setAddressLine2(String addressLine2) {
    this.addressLine2 = addressLine2;
  }

  public String getCity() {
    return city;
  }

  public void setCity(String city) {
    this.city = city;
  }

  public String getProvince() {
    return province;
  }

  public void setProvince(String province) {
    this.province = province;
  }

  public String getPostalCode() {
    return postalCode;
  }

  public void setPostalCode(String postalCode) {
    this.postalCode = postalCode;
  }

  public String getCountryCode() {
    return countryCode;
  }

  public void setCountryCode(String countryCode) {
    this.countryCode = countryCode;
  }

  public Boolean getIsDefault() {
    return isDefault;
  }

  public void setIsDefault(Boolean isDefault) {
    this.isDefault = isDefault;
  }

  public Long getRowVersion() {
    return rowVersion;
  }

  public Instant getArchivedAt() {
    return archivedAt;
  }

  public void setArchivedAt(Instant archivedAt) {
    this.archivedAt = archivedAt;
  }

}
