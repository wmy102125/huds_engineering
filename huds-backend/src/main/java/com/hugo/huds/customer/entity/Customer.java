package com.hugo.huds.customer.entity;

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
 * JPA entity mapped to the existing {@code customers} table.
 *
 * <p>The database schema is managed by Flyway. Hibernate must not be used
 * to create or update this table. The audit columns are inherited from
 * {@link AuthoredAuditableEntity} and filled automatically.</p>
 */
@Entity
@Table(name = "customers")
@SQLRestriction("archived_at IS NULL")
@Setter
@Getter
public class Customer extends AuthoredAuditableEntity {

    @Id
    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "phone")
    private String phone;

    @Column(name = "email")
    private String email;

    @Column(name = "notes")
    private String notes;

    @Column(name = "archived_at")
    @Setter(AccessLevel.NONE)
    private Instant archivedAt;

    @Version
    @Column(name = "row_version", nullable = false)
    @Setter(AccessLevel.NONE)
    private Long rowVersion;

    public Customer() {
        // Required by JPA.
    }

    /**
     * Soft-deletes this customer. The update is issued by dirty checking when the transaction commits.
     *
     * @param now the archive time
     */
    public void archive(Instant now) {
        this.archivedAt = now;
    }
}