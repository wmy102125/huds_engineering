package com.hugo.huds.diagnosticcase.casepart.dto;

/**
 * Where a part of a case is in its supply. The values must match the CHECK constraint on {@code case_parts.parts_supply_status}.
 */
public enum PartsSupplyStatus {

    /** The part is in the shop's own stock. */
    IN_STOCK,

    /** The part is ordered and has not come in yet. */
    ORDERED,

    /** The supplier cannot deliver the part for now. */
    BACKORDER,

    /** The customer brings the part. */
    CUSTOMER_SUPPLIED,

    /** The part was ordered and has come in. */
    ARRIVED;

    /** True while the part still has to be waited for. */
    public boolean isWaiting() {
        return this == ORDERED || this == BACKORDER;
    }
}
