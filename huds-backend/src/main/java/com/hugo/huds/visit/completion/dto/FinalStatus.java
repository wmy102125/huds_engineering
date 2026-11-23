package com.hugo.huds.visit.completion.dto;

/**
 * The state of the appliance when the technician leaves the site.
 * The final status of a completed visit also decides the next state of the case.
 */
public enum FinalStatus {
    OPERATING_NORMALLY,
    FURTHER_TESTING,
    QUOTE_PENDING_APPROVAL,
    PARTS_REQUIRED,
    NO_FAULT_FOUND,
    REPAIR_DECLINED,
    NOT_REPAIRABLE
}