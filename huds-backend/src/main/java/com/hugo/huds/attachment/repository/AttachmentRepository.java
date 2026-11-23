package com.hugo.huds.attachment.repository;

import com.hugo.huds.attachment.dto.AttachmentEntityType;
import com.hugo.huds.attachment.entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.awt.print.Pageable;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {
    /**
     * Rows changed after the cursor (updated_at, id), oldest first. The id breaks ties
     * between rows that were updated at the same instant, so no row is skipped or repeated.
     */
    @Query("""
            select a from Attachment a
            where a.updatedAt > :since
               or (a.updatedAt = :since and a.attachmentId > :afterId)
            order by a.updatedAt asc, a.attachmentId asc
            limit:limit
            """)
    List<Attachment> findChanges(@Param("since") Instant since,
                                 @Param("afterId") UUID afterId,
                                 @Param("limit") int limit);

    Optional<Attachment> findByEntityTypeAndEntityIdAndFileName(AttachmentEntityType entityType, UUID entityId, String fileName);
}
