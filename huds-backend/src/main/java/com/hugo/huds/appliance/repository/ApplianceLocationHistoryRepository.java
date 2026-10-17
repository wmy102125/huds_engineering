package com.hugo.huds.appliance.repository;

import com.hugo.huds.appliance.entity.ApplianceLocationHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ApplianceLocationHistoryRepository extends JpaRepository<ApplianceLocationHistory, UUID> {
}
