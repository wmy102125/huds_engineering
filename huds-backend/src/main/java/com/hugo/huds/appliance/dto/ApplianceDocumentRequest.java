package com.hugo.huds.appliance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Register a document by its type and reference number printed ont it. the file is uploaded later.
 * @author Meiyan Wang
 * @date 2026-11-12 1:25 p.m.
 */
public record ApplianceDocumentRequest(@NotNull(message = "Document type is required")
                                       ApplianceDocumentType documentType,

                                       @NotBlank(message = "Document reference is required")
                                       @Size(max = 120, message = "Document reference is too long")
                                       String documentRef) {
}
