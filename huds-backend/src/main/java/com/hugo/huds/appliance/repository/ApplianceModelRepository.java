package com.hugo.huds.appliance.repository;

import com.hugo.huds.appliance.entity.ApplianceModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ApplianceModelRepository extends JpaRepository<ApplianceModel, UUID> {
}
