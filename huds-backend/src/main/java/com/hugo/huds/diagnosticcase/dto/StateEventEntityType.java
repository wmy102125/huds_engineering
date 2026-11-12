package com.hugo.huds.diagnosticcase.dto;

/** The kinds of entity that write state events. The database column is plain text; this enum keeps the names in one place. */
public enum StateEventEntityType {
    CASE,
    VISIT
}
