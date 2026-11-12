package com.hugo.huds.diagnosticcase.repository;

import com.hugo.huds.diagnosticcase.entity.CaseNumberCounter;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseNumberCounterRepository extends JpaRepository<CaseNumberCounter, Integer> {
}
