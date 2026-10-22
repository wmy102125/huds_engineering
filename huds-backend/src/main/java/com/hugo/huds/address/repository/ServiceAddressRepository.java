package com.hugo.huds.address.repository;

import com.hugo.huds.address.entity.ServiceAddress;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
