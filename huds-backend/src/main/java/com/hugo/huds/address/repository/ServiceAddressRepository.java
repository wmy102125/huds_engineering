package com.hugo.huds.address.repository;

import com.hugo.huds.address.entity.ServiceAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ServiceAddressRepository extends JpaRepository<ServiceAddress, UUID> {
}
