package com.hugo.huds.invoice.dto;

import java.util.EnumSet;
import java.util.Set;

/**
 * The state of the bill of a case. DRAFT -> ISSUED -> VOID, or DRAFT -> VOID. VOID cannot be left.
 */
public enum InvoiceStatus {
    DRAFT,
    ISSUED,
    VOID;

    /** True when the status may change to the given one (staying the same is always fine). */
    public boolean canMoveTo(InvoiceStatus target) {
        if (this == target) {
            return true;
        }
        return switch (this) {
            case DRAFT -> EnumSet.of(ISSUED, VOID).contains(target);
            case ISSUED -> target == VOID;
            case VOID -> false;
        };
    }

    /** The fees can only be changed while the bill is a draft. */
    public boolean isFeeEditable() {
        return this == DRAFT;
    }

    public static Set<InvoiceStatus> creatableStatuses() {
        return EnumSet.of(DRAFT);
    }
}
