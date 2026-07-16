package com.safeops.backend.repository;

import com.safeops.backend.entity.InvocationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvocationLogRepository extends JpaRepository<InvocationLog, Long> {
    Page<InvocationLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    Page<InvocationLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
