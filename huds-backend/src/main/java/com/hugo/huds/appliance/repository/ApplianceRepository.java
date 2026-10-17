package com.hugo.huds.appliance.repository;

import com.hugo.huds.appliance.entity.Appliance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ApplianceRepository extends JpaRepository<Appliance, UUID> {
}
