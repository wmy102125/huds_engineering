package com.hugo.huds.appliance.service;

import com.hugo.huds.appliance.dto.*;
import com.hugo.huds.appliance.entity.ApplianceDocument;
import com.hugo.huds.appliance.repository.ApplianceDocumentRepository;
import com.hugo.huds.audit.dto.AuditAction;
import com.hugo.huds.audit.service.AuditService;
import com.hugo.huds.audit.service.ChangedFields;
import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.exception.ApplicationException;
import com.hugo.huds.storage.ObjectStorageService;
import com.hugo.huds.storage.PresignedDownload;
import com.hugo.huds.storage.PresignedUpload;
import com.hugo.huds.storage.StoredObject;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.hugo.huds.common.util.ParamNormalize.normalize;

/**
 * @author Meiyan Wang
 * @date 2026-11-12 1:39 p.m.
 */
@Validated
@Slf4j
@Service
@Transactional(readOnly = true)
public class ApplianceDocumentService {
    private static final String AUDIT_ENTITY_TYPE = "APPLIANCE_DOCUMENT";
    private static final String OBJECT_KEY_PREFIX = "appliance-documents";
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of("application/pdf", "image/png", "image/jpeg");
    private static final long MAX_BYTE_SIZE = 25L * 1024 * 1024;

    private final ApplianceDocumentRepository applianceDocumentRepository;
    private final AuditService auditService;
    private final ObjectStorageService objectStorageService;

    public ApplianceDocumentService(ApplianceDocumentRepository applianceDocumentRepository, AuditService auditService, ObjectStorageService objectStorageService) {
        this.applianceDocumentRepository = applianceDocumentRepository;
        this.auditService = auditService;
        this.objectStorageService = objectStorageService;
    }

    @Transactional
    public ApplianceDocumentResponse register(ApplianceDocumentRequest request) {
        String documentRef = normalize(request.documentRef());
        // The same document is registered once; tell the client which one to use instead.
        Optional<UUID> duplicateId = applianceDocumentRepository.findDuplicateId(request.documentType(), documentRef);
        if (duplicateId.isPresent()) {
            throw ApplicationException.duplicate(
                    "A document with the same type and reference already exists.", duplicateId.get());
        }
        ApplianceDocument document = new ApplianceDocument();
        document.setApplianceDocumentId(UUID.randomUUID());
        document.setDocumentType(request.documentType());
        document.setDocumentRef(documentRef);
        // saveAndFlush so that row_version and the timestamps are filled before the response is built.
        applianceDocumentRepository.saveAndFlush(document);
        auditService.recordInTransaction(
                AuditAction.CREATE, AUDIT_ENTITY_TYPE, document.getApplianceDocumentId(), Map.of());
        return toResponse(document);
    }


    @Transactional
    public ApplianceDocumentUploadResponse requestUpload(@NotNull UUID applianceDocumentId,
                                                         @NotNull ApplianceDocumentUploadRequest request) {
        ApplianceDocument document = findDocument(applianceDocumentId);
        if (document.getUploadStatus() == ApplianceDocumentUploadStatus.UPLOADED) {
            throw new ApplicationException(ErrorCode.DOMAIN_RULE_VIOLATION, "The document already has a file.");
        }

        if (!ALLOWED_MIME_TYPES.contains(request.mimeType())) {
            throw new ApplicationException(ErrorCode.INVALID_PAYLOAD, "Only PDF, PNG and JPEG files are accepted.");
        }

        if (request.byteSize() > MAX_BYTE_SIZE) {
            throw new ApplicationException(ErrorCode.INVALID_PAYLOAD, "The file is larger than " + MAX_BYTE_SIZE / 1024 / 1024 + " MB.");
        }
        // Keep only the last part of the name: a client may send a full path such as "C:\files\manual.pdf".
        String fileName = request.fileName().trim().replaceAll(".*[\\\\/]", "");
        if (fileName.isEmpty()) {
            throw new ApplicationException(ErrorCode.INVALID_PAYLOAD, "File name is required.");
        }
        // A retry gets a new key, so a half-finished earlier upload can never be mixed with this one.
        String objectKey = objectStorageService.newObjectKey(OBJECT_KEY_PREFIX);

        ChangedFields changes = new ChangedFields()
                .track("uploadStatus", document.getUploadStatus(), ApplianceDocumentUploadStatus.PENDING)
                .track("fileName", document.getFileName(), fileName)
                .track("mimeType", document.getMimeType(), request.mimeType())
                .track("byteSize", document.getByteSize(), request.byteSize())
                .track("sha256Hex", document.getSha256Hex(), request.sha256Hex());

        document.setObjectKey(objectKey);
        document.setFileName(fileName);
        document.setMimeType(request.mimeType());
        document.setByteSize(request.byteSize());
        document.setSha256Hex(request.sha256Hex());
        document.setUploadStatus(ApplianceDocumentUploadStatus.PENDING);
        // Flush so that row_version and updated_at are new when the response is built.
        applianceDocumentRepository.flush();
        auditService.recordInTransaction(
                AuditAction.UPDATE, AUDIT_ENTITY_TYPE, applianceDocumentId, Map.of("changedFields", changes.names()));

        PresignedUpload upload = objectStorageService.presignUpload(objectKey, request.mimeType(), request.byteSize());
        return new ApplianceDocumentUploadResponse(toResponse(document), upload);
    }

    /**
     * Confirms that the file of a document has been uploaded. The server does not take the client's word for it:
     * it asks object storage whether the file exists and matches what was announced.
     * Calling it again for a document that is already UPLOADED changes nothing, so a retry is safe.
     *
     * @param applianceDocumentId the id of the document
     * @return the document, UPLOADED when the file is in storage
     */
    @Transactional
    public ApplianceDocumentResponse completeUpload(@NotNull UUID applianceDocumentId) {
        ApplianceDocument document = findDocument(applianceDocumentId);
        if (document.getUploadStatus() == ApplianceDocumentUploadStatus.UPLOADED) {
            return toResponse(document);
        }
        if (document.getUploadStatus() == ApplianceDocumentUploadStatus.NO_FILE) {
            throw new ApplicationException(ErrorCode.DOMAIN_RULE_VIOLATION,
                    "No upload was requested for this document.");
        }
        StoredObject stored = objectStorageService.head(document.getObjectKey())
                .orElseThrow(() -> new ApplicationException(ErrorCode.DOMAIN_RULE_VIOLATION,
                        "The file has not been uploaded yet."));
        if (stored.byteSize() != document.getByteSize() || !document.getMimeType().equals(stored.contentType())) {
            throw new ApplicationException(ErrorCode.DOMAIN_RULE_VIOLATION,
                    "The uploaded file does not match the announced size or type.");
        }

        ChangedFields changes = new ChangedFields()
                .track("uploadStatus", document.getUploadStatus(), ApplianceDocumentUploadStatus.UPLOADED);
        document.setUploadStatus(ApplianceDocumentUploadStatus.UPLOADED);
        // Flush so that row_version and updated_at are new when the response is built.
        applianceDocumentRepository.flush();
        auditService.recordInTransaction(
                AuditAction.UPDATE, AUDIT_ENTITY_TYPE, applianceDocumentId, Map.of("changedFields", changes.names()));
        return toResponse(document);
    }

    /**
     * Creates a short-lived link for downloading the file of a document.
     *
     * @param applianceDocumentId the id of the document
     * @return the link and the time it stops working
     */
    public ApplianceDocumentDownloadResponse downloadLink(@NotNull UUID applianceDocumentId) {
        ApplianceDocument document = findDocument(applianceDocumentId);
        if (document.getUploadStatus() != ApplianceDocumentUploadStatus.UPLOADED) {
            throw new ApplicationException(ErrorCode.DOMAIN_RULE_VIOLATION, "The document has no file yet.");
        }
        PresignedDownload link = objectStorageService.presignDownload(document.getObjectKey(), document.getFileName());
        return new ApplianceDocumentDownloadResponse(link.url(), link.expiresAt());
    }

    private ApplianceDocument findDocument(UUID applianceDocumentId) {
        return applianceDocumentRepository.findById(applianceDocumentId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.NOT_FOUND, "Document not found."));
    }
        private ApplianceDocumentResponse toResponse(ApplianceDocument document) {
        return new ApplianceDocumentResponse(
                document.getApplianceDocumentId(),
                document.getDocumentType(),
                document.getDocumentRef(),
                document.getUploadStatus(),
                document.getFileName(),
                document.getMimeType(),
                document.getByteSize(),
                document.getRowVersion(),
                document.getCreatedAt(),
                document.getUpdatedAt());
    }

}
