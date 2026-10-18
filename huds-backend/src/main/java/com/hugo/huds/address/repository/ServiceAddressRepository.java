package com.hugo.huds.address.repository;

import com.hugo.huds.address.entity.ServiceAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ServiceAddressRepository extends JpaRepository<ServiceAddress, UUID> {

    Optional<ServiceAddress> findServiceAddressByCustomerIdAndIsDefault(@Param("customerId") UUID customerId, @Param("isDefault") boolean isDefault);
}
