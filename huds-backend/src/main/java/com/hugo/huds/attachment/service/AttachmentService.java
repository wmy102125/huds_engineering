package com.hugo.huds.attachment.service;

import com.hugo.huds.appliance.repository.ApplianceRepository;
import com.hugo.huds.attachment.dto.*;
import com.hugo.huds.attachment.entity.Attachment;
import com.hugo.huds.attachment.repository.AttachmentRepository;
import com.hugo.huds.audit.dto.AuditAction;
import com.hugo.huds.audit.service.AuditService;
import com.hugo.huds.audit.service.ChangedFields;
import com.hugo.huds.common.error.ErrorCode;
import com.hugo.huds.common.exception.ApplicationException;
import com.hugo.huds.diagnosticcase.repository.DiagnosticCaseRepository;
import com.hugo.huds.storage.ObjectStorageService;
import com.hugo.huds.storage.PresignedDownload;
import com.hugo.huds.storage.PresignedUpload;
import com.hugo.huds.storage.StoredObject;
import com.hugo.huds.sync.dto.SyncPullResponse;
import com.hugo.huds.visit.diagnosticstep.repository.DiagnosticStepRepository;
import com.hugo.huds.visit.repository.ServiceVisitRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.annotation.Validated;

import java.time.Instant;
import java.util.*;

import static com.hugo.huds.common.util.ParamNormalize.normalize;

/**
 * @author Meiyan Wang
 * @date 2026-11-13 9:46 a.m.
 */
@Validated
@Slf4j
@Service
@Transactional(readOnly = true)
public class AttachmentService {
    private static final String AUDIT_ENTITY_TYPE = "ATTACHMENT";
    private static final String OBJECT_KEY_PREFIX = "attachments";
    private static final long MAX_PHOTO_BYTES = 15L * 1024 * 1024;
    private static final long MAX_VIDEO_BYTES = 200L * 1024 * 1024;

    private static final Map<String, AttachmentKind> KIND_BY_MIME_TYPE = Map.of(
            "image/jpeg", AttachmentKind.PHOTO,
            "image/png", AttachmentKind.PHOTO,
            "image/heic", AttachmentKind.PHOTO,
            "video/mp4", AttachmentKind.VIDEO,
            "video/quicktime", AttachmentKind.VIDEO);

    private final AttachmentRepository attachmentRepository;
    private final AuditService auditService;
    private final ObjectStorageService objectStorageService;
    private final DiagnosticCaseRepository diagnosticCaseRepository;
    private final ApplianceRepository applianceRepository;
    private final ServiceVisitRepository serviceVisitRepository;
    private final DiagnosticStepRepository diagnosticStepRepository;


    public AttachmentService(AttachmentRepository attachmentRepository, AuditService auditService, ObjectStorageService objectStorageService, DiagnosticCaseRepository diagnosticCaseRepository, ApplianceRepository applianceRepository, ServiceVisitRepository serviceVisitRepository, DiagnosticStepRepository diagnosticStepRepository) {
        this.attachmentRepository = attachmentRepository;
        this.auditService = auditService;
        this.objectStorageService = objectStorageService;
        this.diagnosticCaseRepository = diagnosticCaseRepository;
        this.applianceRepository = applianceRepository;
        this.serviceVisitRepository = serviceVisitRepository;
        this.diagnosticStepRepository = diagnosticStepRepository;
    }

    @Transactional
    public AttachmentResponse register(@NotNull  UUID attachmentId, @Valid @NotNull AttachmentRequest request) {

        AttachmentEntityType entityType = request.entityType();
        UUID entityId = request.entityId();
        boolean exists = switch (entityType) {
            case CASE -> diagnosticCaseRepository.existsById(entityId);
            case APPLIANCE -> applianceRepository.existsById(entityId);
            case SERVICE_VISIT -> serviceVisitRepository.existsById(entityId);
            case DIAGNOSTIC_STEP -> diagnosticStepRepository.existsById(entityId);
            default -> {
                throw new ApplicationException(ErrorCode.INVALID_PAYLOAD, "The entity type is not supported, entityType: " + entityType);
            }
        };
        if (!exists) {
            throw ApplicationException.notFound("The record this file belongs to was not found.", entityId);
        }

        String fileName = normalize(request.fileName());
        if (fileName.isEmpty()) {
            throw new ApplicationException(ErrorCode.INVALID_PAYLOAD, "File name is required.");
        }
        Optional<Attachment> optional = attachmentRepository.findById(attachmentId);
        if (optional.isPresent()) {
            Attachment record = optional.get();
            if (!sameRequest(record, request, fileName)) {
                throw ApplicationException.duplicate("A different file already uses this id.", attachmentId);
            }
            return toResponse(optional.get());

        }
        AttachmentKind kind = KIND_BY_MIME_TYPE.get(request.mimeType());
        if (kind == null) {
            throw new ApplicationException(ErrorCode.INVALID_PAYLOAD, "Only JPEG, PNG, HEIC photos and MP4, MOV videos are accepted.");
        }
        long maxBytes = kind == AttachmentKind.PHOTO ? MAX_PHOTO_BYTES : MAX_VIDEO_BYTES;
        if (request.byteSize() > maxBytes) {
            throw new ApplicationException(ErrorCode.INVALID_PAYLOAD, "The file is larger than " + maxBytes / 1024 / 1024 + " MB.");
        }


        Attachment media = new Attachment();
        media.setAttachmentId(attachmentId);
        media.setEntityType(entityType);
        media.setEntityId(entityId);
        media.setObjectKey(objectStorageService.newObjectKey(OBJECT_KEY_PREFIX));
        media.setMediaKind(kind);
        media.setByteSize(request.byteSize());
        media.setFileName(fileName);
        media.setMimeType(request.mimeType());
        media.setCapturedAt(request.capturedAt());
        media.setSha256Hex(request.sha256Hex());
        // saveAndFlush so that row_version and the timestamps are filled before the response is built.
        attachmentRepository.saveAndFlush(media);
        auditService.recordInTransaction(
                AuditAction.CREATE, AUDIT_ENTITY_TYPE, attachmentId, Map.of());
        return toResponse(media);
    }

    public PresignedUpload requestUpload(UUID attachmentId) {
        Attachment media = attachmentRepository.findById(attachmentId).orElseThrow(() -> ApplicationException.notFound("The current attachment not found.", attachmentId));
        if (media.getUploadStatus() != AttachmentUploadStatus.PENDING) {
            throw new ApplicationException(ErrorCode.DOMAIN_RULE_VIOLATION, "Only a pending attachment can be uploaded.");
        }
        return objectStorageService.presignUpload(media.getObjectKey(), media.getMimeType(), media.getByteSize());
    }


    @Transactional
    public AttachmentResponse completeUpload(@NotNull UUID attachmentId) {
        Attachment attachment = findMedia(attachmentId);
        if (attachment.getUploadStatus() == AttachmentUploadStatus.UPLOADED) {
            return toResponse(attachment);
        }

        StoredObject stored = objectStorageService.head(attachment.getObjectKey())
                .orElseThrow(() -> new ApplicationException(ErrorCode.DOMAIN_RULE_VIOLATION,
                        "The file has not been uploaded yet."));
        if (stored.byteSize() != attachment.getByteSize() || !attachment.getMimeType().equals(stored.contentType())) {
            throw new ApplicationException(ErrorCode.DOMAIN_RULE_VIOLATION,
                    "The uploaded file does not match the announced size or type.");
        }

        ChangedFields changes = new ChangedFields()
                .track("uploadStatus", attachment.getUploadStatus(), AttachmentUploadStatus.UPLOADED);
        attachment.setUploadStatus(AttachmentUploadStatus.UPLOADED);
        // Flush so that row_version and updated_at are new when the response is built.
        attachmentRepository.flush();
        auditService.recordInTransaction(
                AuditAction.UPDATE, AUDIT_ENTITY_TYPE, attachmentId, Map.of("changedFields", changes.names()));
        return toResponse(attachment);
    }

    /**
     * Creates a short-lived link for downloading the file of a media.
     *
     * @param attachmentId the id of the attachment
     * @return the link and the time it stops working
     */
    public AttachmentDownloadedResponse downloadLink(@NotNull UUID attachmentId) {
        Attachment media = findMedia(attachmentId);
        if (media.getUploadStatus() != AttachmentUploadStatus.UPLOADED) {
            throw new ApplicationException(ErrorCode.DOMAIN_RULE_VIOLATION, "The attachment has no file yet.");
        }
        PresignedDownload link = objectStorageService.presignDownload(media.getObjectKey(), media.getFileName());
        return new AttachmentDownloadedResponse(link.url(), link.expiresAt());
    }


    /**
     * Searches the change rows by since, sinceId and limit.
     *
     * @param since   the change rows start time
     * @param sinceId the change rows start id
     * @param limit   this search batch max size number
     * @return matching rows
     */
    public SyncPullResponse fetchChanges(Instant since, UUID sinceId, int limit) {

        List<Attachment> rows = attachmentRepository.findChanges(since, sinceId, limit + 1);
        // There is no change row, return default response
        if (CollectionUtils.isEmpty(rows)) {
            return SyncPullResponse.nullListResponse(since, sinceId);
        }

        boolean hasMore = rows.size() > limit;
        List<Attachment> page = hasMore ? rows.subList(0, limit) : rows;

        List<AttachmentResponse> detailResponses = page.stream().map(this::toResponse).toList();
        Attachment last = page.get(page.size() - 1);
        Instant nextSince = last.getUpdatedAt();
        UUID nextSinceId = last.getAttachmentId();
        return new SyncPullResponse(detailResponses, nextSince, nextSinceId, hasMore);
    }


    private Attachment findMedia(UUID entityId) {
        return attachmentRepository.findById(entityId).orElseThrow(() -> ApplicationException.notFound("Attachment not found.", entityId));
    }

    private boolean sameRequest(Attachment existing, AttachmentRequest request, String fileName) {
        return existing.getEntityType() == request.entityType()
                && Objects.equals(existing.getEntityId(), request.entityId())
                && Objects.equals(existing.getFileName(), fileName)
                && Objects.equals(existing.getMimeType(), request.mimeType())
                && existing.getByteSize() == request.byteSize()
                && Objects.equals(existing.getSha256Hex(), request.sha256Hex());
    }

    private AttachmentResponse toResponse(Attachment media) {
        return new AttachmentResponse(
                media.getAttachmentId(),
                media.getEntityType(),
                media.getEntityId(),
                media.getMediaKind(),
                media.getUploadStatus(),
                media.getFileName(),
                media.getMimeType(),
                media.getByteSize(),
                media.getRowVersion(),
                media.getCapturedAt(),
                media.getCreatedAt(),
                media.getUpdatedAt());

    }

}
