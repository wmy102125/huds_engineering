package com.hugo.huds.appliance.repository;

import com.hugo.huds.appliance.entity.Appliance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplianceRepository extends JpaRepository<Appliance, UUID> {

    List<Appliance> findByApplianceModelId(UUID applianceModelId);

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
                               and m.excludeId<>:excludeId
            """)
    boolean existsDuplicateExcluding(@Param("applianceModelId") UUID applianceModelId,
                                     @Param("serialNo") String serialNo,
                                     @Param("excludeId") UUID excludeId);

    /**
     * Finds appliance by applianceModelId ,with optional serialNo.
     * ApplianceModelId is required filter, serialNo is optional filter, comparisons are case-insensitive.
     *
     * @param applianceModelId the required applianceModelId filter
     * @param serialNo         the optional serialNo filter
     * @param pageable         the pagination information
     * @return a page of matching appliance
     */
    @Query("""
            select m from Appliance m
            where (:applianceModelId = '' or m.applianceModelId = :applianceModelId)
              and (:brand = '' or lower(m.serialNo) like lower(concat('%', :serialNo, '%')))
            """)
    Page<Appliance> findByFilters(@Param("applianceModelId") UUID applianceModelId,
                                  @Param("serialNo") String serialNo,
                                  Pageable pageable);

    /**
     * Finds appliance by applianceModelId ,with optional serialNo.
     * ApplianceModelId is required filter, serialNo is optional filter, comparisons are case-insensitive.
     *
     * @param applianceModelId the required applianceModelId filter
     * @param serialNo         the optional serialNo filter
     * @param sort             the sort of the results
     * @return matching appliances
     */
    @Query("""
            select m from Appliance m
            where (:applianceModelId = '' or m.applianceModelId = :applianceModelId)
              and (:brand = '' or lower(m.serialNo) like lower(concat('%', :serialNo, '%')))
            """)
    Page<Appliance> findAllByFilters(@Param("applianceModelId") UUID applianceModelId,
                                     @Param("serialNo") String serialNo,
                                     Sort sort);
}
