package com.hugo.huds.sync.service;

import com.hugo.huds.auth.support.RolePermissions;
import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.exception.ApplicationException;
import com.hugo.huds.sync.dispatcher.SyncEntityType;
import com.hugo.huds.sync.dto.SyncCaller;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * @author Meiyan Wang
 * @date 2026-10-26 2:25 a.m.
 *
 * Decides whether the caller may change an entity type through the sync endpoint.
 * The permission of every entity type is declared here, in one place.
 */
@Component
public class SyncPermissionChecker {
    private final Map<SyncEntityType, String> requiredAuthority = new EnumMap<>(SyncEntityType.class);

    public SyncPermissionChecker() {
        requiredAuthority.put(SyncEntityType.CUSTOMER, RolePermissions.CUSTOMER_WRITE);
        requiredAuthority.put(SyncEntityType.SERVICE_ADDRESS, RolePermissions.CUSTOMER_WRITE);
        requiredAuthority.put(SyncEntityType.APPLIANCE_MODEL, RolePermissions.APPLIANCE_WRITE);
        requiredAuthority.put(SyncEntityType.APPLIANCE, RolePermissions.APPLIANCE_WRITE);

        // Fail at startup, not at request time, if a new entity type has no permission yet.
        for (SyncEntityType type : SyncEntityType.values()) {
            if (!requiredAuthority.containsKey(type)) {
                throw new IllegalStateException("No write permission is declared for " + type);
            }
        }
    }

    /**
     * Checks that the caller may change the given entity type.
     *
     * @param caller     the authenticated caller
     * @param entityType the entity type of the mutation
     * @throws ApplicationException with FORBIDDEN if the caller lacks the permission
     */
    public void check(SyncCaller caller, SyncEntityType entityType) {
        if (!caller.authorities().contains(requiredAuthority.get(entityType))) {
            throw new ApplicationException(ErrorCode.FORBIDDEN,
                    "You do not have permission to change " + entityType + ".");
        }
    }
}
