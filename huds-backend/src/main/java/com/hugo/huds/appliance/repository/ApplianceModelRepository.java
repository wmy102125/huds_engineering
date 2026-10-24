package com.hugo.huds.appliance.repository;

import com.hugo.huds.appliance.entity.ApplianceModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplianceModelRepository extends JpaRepository<ApplianceModel, UUID> {

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
}
