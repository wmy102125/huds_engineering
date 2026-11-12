package com.hugo.huds.visit.diagnosticstep.dto;

/**
 * The power state of the appliance during a test.
 */
public enum PowerState {
    /** Powered off: resistance, continuity and visual checks. */
    STATIC,
    /** Powered on: voltage and current measurements. */
    LIVE
}
