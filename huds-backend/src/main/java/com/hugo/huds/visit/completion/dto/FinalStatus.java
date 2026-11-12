package com.hugo.huds.visit.completion.dto;

/**
 * The state of the appliance when the technician leaves the site.
 */
public enum FinalStatus {
    OPERATING_NORMALLY,
    FURTHER_TESTING,
    PARTS_REQUIRED,
    NO_FAULT_FOUND,
    REPAIR_DECLINED,
    NOT_REPAIRABLE
}
