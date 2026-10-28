package com.hugo.huds.appliance.repository;

import com.hugo.huds.appliance.entity.Appliance;
import com.hugo.huds.user.reponse.UserBriefResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplianceRepository extends JpaRepository<Appliance, UUID> {
    /**
     * One row of the change feed. Archived appliances are included on purpose.
     */
    interface ChangeRow {
        UUID getApplianceId();

        UUID getApplianceModelId();

        String getSerialNo();

        Boolean getSerialVerified();

        String getIdentityNotes();

        Long getRowVersion();

        Instant getCreatedAt();

        UUID getCreatedBy();

        String getCreatedByName();

        Instant getUpdatedAt();

        UUID getUpdatedBy();

        String getUpdatedByName();

        Boolean getArchived();

        /**
         * The creator as one object, or null when the row has no creator.
         */
        default UserBriefResponse getCreator() {
            return getCreatedBy() == null ? null : new UserBriefResponse(getCreatedBy(), getCreatedByName());
        }

        /**
         * The last editor as one object, or null when the row has no editor.
         */
        default UserBriefResponse getEditor() {
            return getUpdatedBy() == null ? null : new UserBriefResponse(getUpdatedBy(), getUpdatedByName());
        }
    }

    List<Appliance> findByApplianceModelId(UUID applianceModelId);

    List<Appliance> findByApplianceModelIdIn(List<UUID> applianceModelIds);

    Optional<Appliance> findByApplianceModelIdAndSerialNo(UUID applianceModelId, String serialNo);

    /**
     * Whether an active with the same applianceModelId, serialNo already exists.
     * Matches the unique index: case-insensitive, and a missing applianceModelId or serialNo number equals an empty one.
     *
     * @param applianceModelId applianceModelId
     * @param serialNo         serialNo
     * @return true if duplicate exists
     */
    @Query("""
             select count(m)>0 from Appliance m
                         where m.applianceModelId = :applianceModelId
                               and coalesce(lower(m.serialNo),'')= lower(:serialNo)
            """)
    boolean existsDuplicate(@Param("applianceModelId") UUID applianceModelId,
                            @Param("serialNo") String serialNo);

    /**
     * Same as {@link #existsDuplicate}, but ignores the appliance with the given id (used when updating).
     *
     * @param applianceModelId applianceModelId
     * @param serialNo         serialNo
     * @param excludeId        the id of the model to ignore
     * @return true if another model with the same identity exists
     */
    @Query("""
             select count(m)>0 from Appliance m
                         where m.applianceModelId = :applianceModelId
                               and coalesce(lower(m.serialNo),'')= lower(:serialNo)
                               and m.applianceId<>:excludeId
            """)
    boolean existsDuplicateExcluding(@Param("applianceModelId") UUID applianceModelId,
                                     @Param("serialNo") String serialNo,
                                     @Param("excludeId") UUID excludeId);

    @Query("""
            select m from Appliance m
            where m.applianceModelId = :applianceModelId
              and (:serialNo = '' or lower(m.serialNo) like lower(concat('%', :serialNo, '%')))
            """)
    Page<Appliance> findByFilters(@Param("applianceModelId") UUID applianceModelId,
                                  @Param("serialNo") String serialNo,
                                  Pageable pageable);

    @Query("""
            select m from Appliance m
            where m.applianceModelId = :applianceModelId
              and (:serialNo = '' or lower(m.serialNo) like lower(concat('%', :serialNo, '%')))
            """)
    List<Appliance> findAllByFilters(@Param("applianceModelId") UUID applianceModelId,
                                     @Param("serialNo") String serialNo,
                                     Sort sort);

    /**
     * Whether any active appliance uses the given catalog model.
     *
     * @param applianceModelId the id of the catalog model
     * @return true if at least one active appliance uses it
     */
    boolean existsByApplianceModelId(UUID applianceModelId);

    /**
     * Finds the active appliance of a model that has this serial number, ignoring case.
     *
     * @param applianceModelId the id of the appliance model
     * @param serialNo         the serial number, never empty
     * @return the existing appliance, or empty if there is none
     */
    @Query("""
            select a from Appliance a
            where a.applianceModelId = :applianceModelId
              and lower(a.serialNo) = lower(:serialNo)
            """)
    Optional<Appliance> findBySerialNoIgnoreCase(@Param("applianceModelId") UUID applianceModelId,
                                                 @Param("serialNo") String serialNo);

    /**
     * Appliances changed after the cursor (updated_at, appliance_id), oldest first.
     * Native SQL is needed because @SQLRestriction would hide archived rows from JPQL.
     */
    @Query(value = """
            select a.appliance_id as applianceId,
                   a.appliance_model_id as applianceModelId,
                   a.serial_no as serialNo,
                   a.serial_verified as serialVerified,
                   a.identity_notes as identityNotes,
                   a.row_version as rowVersion,
                   a.created_at as createdAt,
                   a.created_by as createdBy,
                   cu.user_name as createdByName,
                   a.updated_at as updatedAt,
                   a.updated_by as updatedBy,
                   uu.user_name as updatedByName,
                   (a.archived_at is not null) as archived
            from appliances a
            left join users cu on cu.user_id = a.created_by
            left join users uu on uu.user_id = a.updated_by
            where (a.updated_at, a.appliance_id) > (:since, :sinceId)
            order by a.updated_at, a.appliance_id
            limit :limit
            """, nativeQuery = true)
    List<ChangeRow> findChanges(@Param("since") Instant since,
                                @Param("sinceId") UUID sinceId,
                                @Param("limit") int limit);


}
