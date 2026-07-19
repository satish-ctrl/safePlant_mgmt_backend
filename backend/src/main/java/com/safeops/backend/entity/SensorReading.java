package com.safeops.backend.entity;

/**
 * SensorReading retained as a plain DTO-style placeholder after removing sensor
 * reading persistence and Flyway migrations.
 */
public class SensorReading {
    private Long id;
    private String sensorId;
    private Double value;
    private Double anomalyScore;
    private java.time.Instant ts;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSensorId() { return sensorId; }
    public void setSensorId(String sensorId) { this.sensorId = sensorId; }
    public Double getValue() { return value; }
    public void setValue(Double value) { this.value = value; }
    public Double getAnomalyScore() { return anomalyScore; }
    public void setAnomalyScore(Double anomalyScore) { this.anomalyScore = anomalyScore; }
    public java.time.Instant getTs() { return ts; }
    public void setTs(java.time.Instant ts) { this.ts = ts; }
}

