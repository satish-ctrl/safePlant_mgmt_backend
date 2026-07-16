from pydantic import BaseModel
from typing import List, Dict, Any, Optional


class HealthResponse(BaseModel):
    status: str
    model_loaded: bool
    chromadb_ready: bool


class SensorAnomalyResponse(BaseModel):
    agent: str
    sensor_id: str
    zone_id: str
    sensor_type: str
    current_value: float
    normal_baseline: float
    anomaly_score: float
    severity: str
    trend: str
    predicted_threshold_breach_minutes: int
    timestamp: str


class ViolationResponse(BaseModel):
    worker_id: str
    violation_type: str
    confidence: float
    bbox: List[int]
    frame_snapshot_path: str


class CVSafetyResponse(BaseModel):
    agent: str
    zone_id: str
    frame_timestamp: str
    workers_detected: int
    violations: List[ViolationResponse]
    headcount_in_hazard_zone: int
    timestamp: str


class ConflictResponse(BaseModel):
    conflict_type: str
    permit_ids: List[str]
    zones_affected: List[str]
    risk_description: str
    severity: str


class PermitIntelResponse(BaseModel):
    agent: str
    active_permits: List[Dict[str, Any]]
    conflicts: List[ConflictResponse]
    timestamp: str


class RiskBreakdownResponse(BaseModel):
    sensor_score: float
    cv_score: float
    permit_score: float
    shift_score: float


class RiskFusionResponse(BaseModel):
    score: float
    severity: str
    kg_amplified: bool
    breakdown: RiskBreakdownResponse


class SimilarIncidentResponse(BaseModel):
    incident_id: str
    date: str
    plant: str
    description: str
    outcome: str
    similarity_score: float


class RegulationResponse(BaseModel):
    regulation_id: str
    title: str
    clause: str
    requirement: str
    violation_detected: bool
    source: str


class RAGComplianceResponse(BaseModel):
    agent: str
    triggered_by_alert_id: str
    similar_incidents: List[SimilarIncidentResponse]
    applicable_regulations: List[RegulationResponse]
    recommended_actions: List[str]
    rag_sources_cited: List[str]


class AnalyzeResponse(BaseModel):
    zone_id: str
    sensor_anomaly: Dict[str, Any]
    cv_safety: Dict[str, Any]
    permit_intel: Dict[str, Any]
    risk_fusion: Dict[str, Any]
    rag_compliance: Optional[Dict[str, Any]] = None
    action_taken: str
    notifications_sent: List[str]
    report_generated: bool


class ErrorResponse(BaseModel):
    error: str
    detail: Optional[str] = None
