from pydantic import BaseModel, Field
from typing import List, Dict, Any, Optional


# ─── Shared sub-models ───────────────────────────────────────────────

class ViolationInput(BaseModel):
    worker_id: str = "WORKER_UNKNOWN"
    violation_type: str = "NO_HELMET"
    confidence: float = 0.85
    bbox: Optional[List[int]] = None
    frame_snapshot_path: Optional[str] = None


class CVFrameInput(BaseModel):
    zone_id: str = "ZONE_3"
    workers_detected: int = 0
    violations: List[ViolationInput] = []


class PermitInput(BaseModel):
    permit_id: str
    type: str
    zone_id: str
    start_time: Optional[str] = None
    expiry: Optional[str] = None
    workers_assigned: Optional[List[str]] = None
    hazard_class: Optional[str] = None


# ─── /analyze endpoint ───────────────────────────────────────────────

class AnalyzeRequest(BaseModel):
    zone_id: str = Field(..., description="Target zone ID (e.g. ZONE_3)")
    current_time: Optional[str] = Field(None, description="ISO8601 timestamp override")
    shift_risk_factor: float = Field(0.1, ge=0.0, le=1.0, description="Time-based human error factor")
    sensor_raw_history: List[List[float]] = Field(..., description="List of [gas, temp, pressure] readings")
    cv_raw_frame: CVFrameInput = Field(..., description="CCTV frame data with violations")
    active_permits_raw: List[PermitInput] = Field(default_factory=list, description="Active work permits")


# ─── /sensor/anomaly endpoint ────────────────────────────────────────

class SensorAnomalyRequest(BaseModel):
    readings: List[Any] = Field(..., description="List of [gas, temp, pressure] or dicts with gas/temp/pressure keys")


# ─── /permits/check endpoint ─────────────────────────────────────────

class PermitCheckRequest(BaseModel):
    permits: List[PermitInput] = Field(..., description="Active permits to check for conflicts")
    current_time: Optional[str] = Field(None, description="ISO8601 timestamp override")
    current_gas: float = Field(0.0, ge=0.0, description="Current gas reading in LEL%")


# ─── /rag/query endpoint ─────────────────────────────────────────────

class RAGQueryRequest(BaseModel):
    zone_id: str = Field(..., description="Target zone ID")
    sensor_reading: float = Field(..., description="Current gas sensor reading in LEL%")
    cv_violations: List[Dict[str, Any]] = Field(default_factory=list, description="Violation dicts from CV agent")
    active_permits: List[Dict[str, Any]] = Field(default_factory=list, description="Active permit dicts")
