package com.hugo.huds.appliance.dto;

/**
 * Kind of a registered appliance document.
 *
 * <p>The values must match the CHECK constraint on {@code appliance_documents.document_type}.</p>
 */
public enum ApplianceDocumentType {

    /** Schematic or tech sheet. */
    SCHEMATIC,

    /** Service manual. */
    SERVICE_MANUAL,

    /** Any other document. */
    OTHER
}
