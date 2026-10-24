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
 * JPA entity mapped to the existing {@code appliance_service_addresses} table.
 *
 * <p>Links an appliance to a service address where it has been seen. Repeat repairs at the
 * same address reuse the existing row. The database schema is managed by Flyway. The audit
 * columns are inherited from {@link AuthoredAuditableEntity} and filled automatically.</p>
 */
@Entity
@Table(name = "appliance_service_addresses")
@SQLRestriction("archived_at IS NULL")
@Setter
@Getter
public class ApplianceServiceAddress extends AuthoredAuditableEntity {

    // The id is supplied by the caller (the client generates it offline), so there is no @GeneratedValue.
    @Id
    @Column(name = "appliance_service_address_id", nullable = false, updatable = false)
    private UUID applianceServiceAddressId;

    // The (appliance, address) pair never changes after creation: archive and create a new link instead.
    @Column(name = "appliance_id", nullable = false, updatable = false)
    private UUID applianceId;

    @Column(name = "service_address_id", nullable = false, updatable = false)
    private UUID serviceAddressId;

    @Column(name = "note")
    private String note;

    @Column(name = "archived_at")
    @Setter(AccessLevel.NONE)
    private Instant archivedAt;

    @Version
    @Column(name = "row_version", nullable = false)
    @Setter(AccessLevel.NONE)
    private Long rowVersion;

    public ApplianceServiceAddress() {
        // Required by JPA.
    }

    /**
     * Soft-deletes this link. The update is issued by dirty checking when the transaction commits.
     *
     * @param now the archive time
     */
    public void archive(Instant now) {
        this.archivedAt = now;
    }
}