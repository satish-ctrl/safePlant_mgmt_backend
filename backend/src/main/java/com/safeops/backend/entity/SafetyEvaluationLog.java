package com.safeops.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "safety_evaluation_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SafetyEvaluationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "zone_id", nullable = false, length = 50)
    private String zoneId;

    @Column(name = "risk_score", nullable = false)
    private double riskScore;

    @Column(name = "severity", nullable = false, length = 50)
    private String severity;

    @Column(name = "action_taken", length = 100)
    private String actionTaken;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_result", columnDefinition = "jsonb")
    private Map<String, Object> rawResult;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
