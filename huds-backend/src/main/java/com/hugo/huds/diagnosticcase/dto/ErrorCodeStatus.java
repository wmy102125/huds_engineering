package com.hugo.huds.diagnosticcase.dto;

/**
 * Status of an error code on the diagnostic form.
 *
 * <p>The values must match the CHECK constraint on {@code case_error_codes.code_status}.</p>
 */
public enum ErrorCodeStatus {

    /** The current fault code shown by the appliance. */
    ACTIVE,

    /** An earlier fault stored in the appliance's history. */
    STORED_HISTORY
}
