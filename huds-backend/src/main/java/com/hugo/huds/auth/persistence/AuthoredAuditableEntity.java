package com.hugo.huds.auth.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;
import lombok.Getter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;

/**
 * @author Meiyan Wang
 * @date 2026-10-19 11:48 p.m.
 * @description Extends {@link AuditableEntity} with the ids of the users who created and last changed the row.
 *              The values come from {@link com.hugo.huds.config.CurrentUserAuditorAware}.
 */
@Getter
@MappedSuperclass
public abstract class AuthoredAuditableEntity extends AuditableEntity {

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @LastModifiedBy
    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;
}