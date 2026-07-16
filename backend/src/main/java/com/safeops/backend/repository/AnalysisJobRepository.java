package com.safeops.backend.repository;

import com.safeops.backend.entity.AnalysisJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, String> {
    Page<AnalysisJob> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
