package com.safeops.backend.repository;

import com.safeops.backend.entity.SafetyEvaluationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SafetyEvaluationLogRepository extends JpaRepository<SafetyEvaluationLog, Long> {
}
