package com.hugo.huds.appliance.repository;

import com.hugo.huds.appliance.entity.ApplianceServiceAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ApplianceServiceAddressRepository extends JpaRepository<ApplianceServiceAddress, UUID> {
}
