package com.hugo.huds.address.entity;

import com.hugo.huds.auth.persistence.AuthoredAuditableEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

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
@SQLRestriction("archived_at IS NULL")
@Getter
@Setter
public class ServiceAddress extends AuthoredAuditableEntity {

  @Id
  @Column(name = "service_address_id", nullable = false, updatable = false)
  private UUID serviceAddressId;

  @Column(name = "customer_id", nullable = false)
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
  @Setter(AccessLevel.NONE)
  private Long rowVersion;

  @Column(name = "archived_at")
  @Setter(AccessLevel.NONE)
  private Instant archivedAt;

  public ServiceAddress() {
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
}