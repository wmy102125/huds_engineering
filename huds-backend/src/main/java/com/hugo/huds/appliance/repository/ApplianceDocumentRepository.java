package com.hugo.huds.appliance.repository;

import com.hugo.huds.appliance.dto.ApplianceDocumentType;
import com.hugo.huds.appliance.entity.ApplianceDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ApplianceDocumentRepository extends JpaRepository<ApplianceDocument, UUID> {

    /**
     * Finds the id of the document with the same type and reference number (case-insensitive).
     *
     * @return the id of the existing document, or empty if there is none
     */
    @Query("""
        select d.applianceDocumentId from ApplianceDocument d
        where d.documentType = :documentType
          and lower(d.documentRef) = lower(:documentRef)
        """)
    Optional<UUID> findDuplicateId(@Param("documentType") ApplianceDocumentType documentType,
                                   @Param("documentRef") String documentRef);

    /**
     * Same as {@link #findDuplicateId}, but ignores the document with the given id (used when updating).
     *
     * @return the id of another document with the same type and reference, or empty if there is none
     */
    @Query("""
        select d.applianceDocumentId from ApplianceDocument d
        where d.documentType = :documentType
          and lower(d.documentRef) = lower(:documentRef)
          and d.applianceDocumentId <> :excludeId
        """)
    Optional<UUID> findDuplicateIdExcluding(@Param("documentType") ApplianceDocumentType documentType,
                                            @Param("documentRef") String documentRef,
                                            @Param("excludeId") UUID excludeId);

    /**
     * Finds a document by the key of its file in object storage.
     *
     * @return the matching document, or empty if no document uses this key
     */
    Optional<ApplianceDocument> findByObjectKey(String objectKey);
}