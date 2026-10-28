package com.hugo.huds.address.repository;

import com.hugo.huds.address.entity.ServiceAddress;
import com.hugo.huds.customer.repository.CustomerRepository;
import com.hugo.huds.user.reponse.UserBriefResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceAddressRepository extends JpaRepository<ServiceAddress, UUID> {
    /**
     * One row of the change feed. Archived service addresses are included on purpose.
     */
    interface ChangeRow {
        UUID getServiceAddressId();

        UUID getCustomerId();

        String getAddressLine1();

        String getAddressLine2();

        String getCity();

        String getProvince();

        String getPostalCode();

        String getCountryCode();

        Boolean getIsDefault();

        Instant getCreatedAt();

        UUID getCreatedBy();

        String getCreatedByName();

        Instant getUpdatedAt();

        UUID getUpdatedBy();

        String getUpdatedByName();

        Long getRowVersion();

        Boolean getArchived();

        default UserBriefResponse getCreator() {
            return getCreatedBy() == null ? null : new UserBriefResponse(getCreatedBy(), getCreatedByName());
        }

        default UserBriefResponse getEditor() {
            return getUpdatedBy() == null ? null : new UserBriefResponse(getUpdatedBy(), getUpdatedByName());
        }
    }

    /**
     * Find current customer's default address
     *
     * @param customerId customer id
     * @param isDefault  If current address is default(true)
     * @return Current customer's default address
     */
    Optional<ServiceAddress> findByCustomerIdAndIsDefault(UUID customerId, boolean isDefault);

    /**
     * Find current customer's service address list.
     *
     * @param customerId customer id
     * @return Current customer's service address list.
     */
    List<ServiceAddress> findByCustomerId(UUID customerId);

    /**
     * Whether the customer already has an active address with the same lines, city, province,
     * postal code and country. The comparison ignores case, and a missing optional value equals
     * an empty one. Pass an empty string, never null, for a missing optional value.
     *
     * @param customerId   the id of the customer
     * @param addressLine1 the first address line
     * @param addressLine2 the second address line, or an empty string
     * @param city         the city
     * @param province     the province
     * @param postalCode   the postal code, or an empty string
     * @param countryCode  the country code
     * @return true if a duplicate exists
     */
    @Query("""
            select count(a) > 0 from ServiceAddress a
            where a.customerId = :customerId
              and lower(a.addressLine1) = lower(:addressLine1)
              and coalesce(lower(a.addressLine2), '') = lower(:addressLine2)
              and lower(a.city) = lower(:city)
              and lower(a.province) = lower(:province)
              and coalesce(lower(a.postalCode), '') = lower(:postalCode)
              and lower(a.countryCode) = lower(:countryCode)
            """)
    boolean existsDuplicate(@Param("customerId") UUID customerId,
                            @Param("addressLine1") String addressLine1,
                            @Param("addressLine2") String addressLine2,
                            @Param("city") String city,
                            @Param("province") String province,
                            @Param("postalCode") String postalCode,
                            @Param("countryCode") String countryCode);

    /**
     * Same as {@link #existsDuplicate}, but ignores the address with the given id (used when updating).
     *
     * @param customerId       the id of the customer
     * @param addressLine1     the first address line
     * @param addressLine2     the second address line, or an empty string
     * @param city             the city
     * @param province         the province
     * @param postalCode       the postal code, or an empty string
     * @param countryCode      the country code
     * @param serviceAddressId the id of the address to ignore
     * @return true if another address with the same content exists
     */
    @Query("""
            select count(a) > 0 from ServiceAddress a
            where a.customerId = :customerId
              and lower(a.addressLine1) = lower(:addressLine1)
              and coalesce(lower(a.addressLine2), '') = lower(:addressLine2)
              and lower(a.city) = lower(:city)
              and lower(a.province) = lower(:province)
              and coalesce(lower(a.postalCode), '') = lower(:postalCode)
              and lower(a.countryCode) = lower(:countryCode)
              and a.serviceAddressId <> :serviceAddressId
            """)
    boolean existsDuplicateExcluding(@Param("customerId") UUID customerId,
                                     @Param("addressLine1") String addressLine1,
                                     @Param("addressLine2") String addressLine2,
                                     @Param("city") String city,
                                     @Param("province") String province,
                                     @Param("postalCode") String postalCode,
                                     @Param("countryCode") String countryCode,
                                     @Param("serviceAddressId") UUID serviceAddressId);

    /**
     * Finds the id of the customer's active address that has the same content, ignoring case.
     * A missing optional value equals an empty one, so pass "" instead of null for it.
     *
     * @return the id of the existing address, or empty if there is none
     */
    @Query("""
            select a.serviceAddressId from ServiceAddress a
            where a.customerId = :customerId
              and lower(a.addressLine1) = lower(:addressLine1)
              and coalesce(lower(a.addressLine2), '') = lower(:addressLine2)
              and lower(a.city) = lower(:city)
              and lower(a.province) = lower(:province)
              and coalesce(lower(a.postalCode), '') = lower(:postalCode)
              and lower(a.countryCode) = lower(:countryCode)
            """)
    Optional<UUID> findDuplicateId(@Param("customerId") UUID customerId,
                                   @Param("addressLine1") String addressLine1,
                                   @Param("addressLine2") String addressLine2,
                                   @Param("city") String city,
                                   @Param("province") String province,
                                   @Param("postalCode") String postalCode,
                                   @Param("countryCode") String countryCode);

    /**
     * Same as {@link #findDuplicateId}, but ignores the address with the given id (used when updating).
     */
    @Query("""
            select a.serviceAddressId from ServiceAddress a
            where a.customerId = :customerId
              and lower(a.addressLine1) = lower(:addressLine1)
              and coalesce(lower(a.addressLine2), '') = lower(:addressLine2)
              and lower(a.city) = lower(:city)
              and lower(a.province) = lower(:province)
              and coalesce(lower(a.postalCode), '') = lower(:postalCode)
              and lower(a.countryCode) = lower(:countryCode)
              and a.serviceAddressId <> :serviceAddressId
            """)
    Optional<UUID> findDuplicateIdExcluding(@Param("customerId") UUID customerId,
                                            @Param("addressLine1") String addressLine1,
                                            @Param("addressLine2") String addressLine2,
                                            @Param("city") String city,
                                            @Param("province") String province,
                                            @Param("postalCode") String postalCode,
                                            @Param("countryCode") String countryCode,
                                            @Param("serviceAddressId") UUID serviceAddressId);

    /**
     * ServiceAddresses changed after the cursor (updated_at, service_address_id), oldest first.
     * Native SQL is needed because @SQLRestriction on ServiceAddress would hide archived rows from JPQL.
     */
    @Query(value = """
            select c.service_address_id as serviceAddressId,
                               c.customer_id as customerId,
                               c.address_line1 as addressLine1,
                               c.address_line2 as addressLine2,
                               c.city as city,
                               c.province as province,
                               c.postal_code as postalCode,
                               c.country_code as countryCode,
                               c.is_default as isDefault,
                               c.created_at as createdAt,
                               c.created_by as createdBy,
                               cu.user_name as createdByName,
                               c.updated_at as updatedAt,
                               c.updated_by as updatedBy,
                               uu.user_name as updatedByName,
                               c.row_version as rowVersion,
                               (c.archived_at is not null) as archived
                        from service_addresses c
                        left join users cu on cu.user_id = c.created_by
                        left join users uu on uu.user_id = c.updated_by
                        where (c.updated_at, c.service_address_id) > (:since, :sinceId)
                        order by c.updated_at, c.service_address_id
                        limit :limit
            """, nativeQuery = true)
    List<ChangeRow> findChanges(@Param("since") Instant since,
                                @Param("sinceId") UUID sinceId,
                                @Param("limit") int limit);
    @Query(value = """
            select c.service_address_id as serviceAddressId,
                               c.customer_id as customerId,
                               c.address_line1 as addressLine1,
                               c.address_line2 as addressLine2,
                               c.city as city,
                               c.province as province,
                               c.postal_code as postalCode,
                               c.country_code as countryCode,
                               c.is_default as isDefault,
                               c.created_at as createdAt,
                               c.created_by as createdBy,
                               cu.user_name as createdByName,
                               c.updated_at as updatedAt,
                               c.updated_by as updatedBy,
                               uu.user_name as updatedByName,
                               c.row_version as rowVersion,
                               (c.archived_at is not null) as archived
                        from service_addresses c
                        left join users cu on cu.user_id = c.created_by
                        left join users uu on uu.user_id = c.updated_by
                        where  c.service_address_id in (:serviceAddressIds)
            """, nativeQuery = true)
    List<ChangeRow> findByIdIn(@Param("serviceAddressIds")List<UUID> serviceAddressIds);

}
