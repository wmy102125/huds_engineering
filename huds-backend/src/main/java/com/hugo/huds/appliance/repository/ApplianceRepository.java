package com.hugo.huds.appliance.repository;

import com.hugo.huds.appliance.entity.Appliance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplianceRepository extends JpaRepository<Appliance, UUID> {

   // Optional<Appliance> findByApplianceTypeAndBrandAndModelNoAndSerialNo(String applianceType, String brand, String modelNo, String serialNo);
}
