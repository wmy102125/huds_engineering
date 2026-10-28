package com.hugo.huds.appliance.repository;

import com.hugo.huds.appliance.entity.ApplianceModel;
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

public interface ApplianceModelRepository extends JpaRepository<ApplianceModel, UUID> {
    /** One row of the change feed. Archived models are included on purpose. */
    interface ChangeRow {
        UUID getApplianceModelId();
        String getApplianceType();
        String getBrand();
        String getModelNo();
        Long getRowVersion();
        Instant getCreatedAt();
        UUID getCreatedBy();
        String getCreatedByName();
        Instant getUpdatedAt();
        UUID getUpdatedBy();
        String getUpdatedByName();
        Boolean getArchived();

        /** The creator as one object, or null when the row has no creator. */
        default UserBriefResponse getCreator() {
            return getCreatedBy() == null ? null : new UserBriefResponse(getCreatedBy(), getCreatedByName());
        }

        /** The last editor as one object, or null when the row has no editor. */
        default UserBriefResponse getEditor() {
            return getUpdatedBy() == null ? null : new UserBriefResponse(getUpdatedBy(), getUpdatedByName());
        }
    }
    /**
     * Finds an appliance model with its applianceType, brand and modelNo
     *
     * @param applianceType the type of the appliance model
     * @param brand         the brand of the appliance model
     * @param modelNo       the model no of the appliance model
     * @return the matching appliance model, or empty if none is found
     */
    Optional<ApplianceModel> findByApplianceTypeAndBrandAndModelNo(String applianceType,
                                                                   String brand,
                                                                   String modelNo);
    /**
     * Finds the id of the active model with the same type, brand and model number (case-insensitive).
     *
     * @return the id of the existing model, or empty if there is none
     */
    @Query("""
        select m.applianceModelId from ApplianceModel m
        where lower(m.applianceType) = lower(:applianceType)
          and coalesce(lower(m.brand), '') = lower(:brand)
          and coalesce(lower(m.modelNo), '') = lower(:modelNo)
        """)
    Optional<UUID> findDuplicateId(@Param("applianceType") String applianceType,
                                   @Param("brand") String brand,
                                   @Param("modelNo") String modelNo);

    /**
     * Same as {@link #findDuplicateId}, but ignores the model with the given id (used when updating).
     *
     * @return the id of another model with the same identity, or empty if there is none
     */
    @Query("""
        select m.applianceModelId from ApplianceModel m
        where lower(m.applianceType) = lower(:applianceType)
          and coalesce(lower(m.brand), '') = lower(:brand)
          and coalesce(lower(m.modelNo), '') = lower(:modelNo)
          and m.applianceModelId <> :excludeId
        """)
    Optional<UUID> findDuplicateIdExcluding(@Param("applianceType") String applianceType,
                                            @Param("brand") String brand,
                                            @Param("modelNo") String modelNo,
                                            @Param("excludeId") UUID excludeId);
    /**
     * Whether an active model with the same applianceType, brand and modelNo already exists.
     * Matches the unique index: case-insensitive, and a missing brand or model number equals an empty one.
     *
     * @param applianceType appliance type
     * @param brand         brand
     * @param modelNo       modelNo
     * @return true if duplicate exists
     */
    @Query("""
             select count(m)>0 from ApplianceModel m
                         where m.applianceType = :applianceType
                               and coalesce(lower(m.brand),'')= lower(:brand)
                               and coalesce(lower(m.modelNo),'')= lower(:modelNo)
            """)
    boolean existsDuplicate(@Param("applianceType") String applianceType,
                            @Param("brand") String brand,
                            @Param("modelNo") String modelNo);

    /**
     * Same as {@link #existsDuplicate}, but ignores the model with the given id (used when updating).
     *
     * @param applianceType appliance type
     * @param brand         brand, or an empty string
     * @param modelNo       model number, or an empty string
     * @param excludeId     the id of the model to ignore
     * @return true if another model with the same identity exists
     */
    @Query("""
             select count(m)>0 from ApplianceModel m
                         where m.applianceType = :applianceType
                               and coalesce(lower(m.brand),'')= lower(:brand)
                               and coalesce(lower(m.modelNo),'')= lower(:modelNo)
                               and m.applianceModelId <> :excludeId
            """)
    boolean existsDuplicateExcluding(@Param("applianceType") String applianceType,
                                     @Param("brand") String brand,
                                     @Param("modelNo") String modelNo,
                                     @Param("excludeId") UUID excludeId);

    /**
     * Finds appliance models matching the provided filters.
     * All filters are optional, and comparisons are case-insensitive.
     *
     * @param applianceType the optional appliance type filter
     * @param brand         the optional brand filter
     * @param modelNo       the optional model number filter
     * @param pageable      the pagination information
     * @return a page of matching appliance models
     */
    @Query("""
            select m from ApplianceModel m
            where (:applianceType = '' or m.applianceType = :applianceType)
              and (:brand = '' or lower(m.brand) like lower(concat('%', :brand, '%')))
              and (:modelNo = '' or lower(m.modelNo) like lower(concat('%', :modelNo, '%')))
            """)
    Page<ApplianceModel> findByFilters(@Param("applianceType") String applianceType,
                                       @Param("brand") String brand,
                                       @Param("modelNo") String modelNo,
                                       Pageable pageable);

    /**
     * Finds appliance models by applianceType ,with optional brand and model number.
     *
     * @param applianceType the optional appliance type filter
     * @param brand         the optional brand filter
     * @param modelNo       the optional model number filter
     * @return matching appliance models
     */
    @Query("""
            select m from ApplianceModel m
                        where m.applianceType = :applianceType
                          and (:brand = '' or lower(m.brand) like lower(concat('%', :brand, '%')))
                          and (:modelNo = '' or lower(m.modelNo) like lower(concat('%', :modelNo, '%')))
            """)
    List<ApplianceModel> findAllByFilters(@Param("applianceType") String applianceType,
                                          @Param("brand") String brand,
                                          @Param("modelNo") String modelNo,
                                          Sort sort);
    /**
     * Models changed after the cursor (updated_at, appliance_model_id), oldest first.
     * Native SQL is needed because @SQLRestriction would hide archived rows from JPQL.
     */
    @Query(value = """
            select m.appliance_model_id as applianceModelId,
                   m.appliance_type as applianceType,
                   m.brand as brand,
                   m.model_no as modelNo,
                   m.row_version as rowVersion,
                   m.created_at as createdAt,
                   m.created_by as createdBy,
                   cu.user_name as createdByName,
                   m.updated_at as updatedAt,
                   m.updated_by as updatedBy,
                   uu.user_name as updatedByName,
                   (m.archived_at is not null) as archived
            from appliance_models m
            left join users cu on cu.user_id = m.created_by
            left join users uu on uu.user_id = m.updated_by
            where (m.updated_at, m.appliance_model_id) > (:since, :sinceId)
            order by m.updated_at, m.appliance_model_id
            limit :limit
            """, nativeQuery = true)
    List<ChangeRow> findChanges(@Param("since") Instant since,
                                @Param("sinceId") UUID sinceId,
                                @Param("limit") int limit);

}
