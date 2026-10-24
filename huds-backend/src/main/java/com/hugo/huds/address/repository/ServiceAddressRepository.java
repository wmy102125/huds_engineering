package com.hugo.huds.address.repository;

import com.hugo.huds.address.entity.ServiceAddress;
import jakarta.persistence.Column;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceAddressRepository extends JpaRepository<ServiceAddress, UUID> {
    /**
     * Find current customer's default address
     * @param customerId customer id
     * @param isDefault If current address is default(true)
     * @return Current customer's default address
     */
    Optional<ServiceAddress> findByCustomerIdAndIsDefault(UUID customerId, boolean isDefault);

    /**
     * Find current customer's service address list.
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
}
