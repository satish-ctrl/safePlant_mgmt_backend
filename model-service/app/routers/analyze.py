import time
import traceback
from fastapi import APIRouter, HTTPException, status
from app.schemas.requests import AnalyzeRequest
from app.schemas.responses import AnalyzeResponse, ErrorResponse

router = APIRouter(prefix="/analyze", tags=["Analysis"])


@router.post(
    "",
    response_model=AnalyzeResponse,
    status_code=status.HTTP_200_OK,
    responses={500: {"model": ErrorResponse}},
    summary="Run full LangGraph analysis pipeline"
)
def run_full_analysis(request: AnalyzeRequest):
    """Executes the complete SafeOps AI analysis pipeline:
    Sensor → CV → Permit → Risk Fusion → (conditional) RAG Compliance.
    
    This is a synchronous call that may take 3-10 seconds when RAG is triggered.
    """
    try:
        from app.core.orchestrator.master_flow import flow_app
        
        # Build the LangGraph input state
        state = {
            "zone_id": request.zone_id,
            "current_time": request.current_time,
            "shift_risk_factor": request.shift_risk_factor,
            "sensor_raw_history": request.sensor_raw_history,
            "cv_raw_frame": request.cv_raw_frame.model_dump(),
            "active_permits_raw": [p.model_dump() for p in request.active_permits_raw],
        }
        
        start_time = time.time()
        result = flow_app.invoke(state)
        latency_ms = (time.time() - start_time) * 1000
        
        print(f"Analysis completed in {latency_ms:.2f}ms")
        
        return AnalyzeResponse(
            zone_id=request.zone_id,
            sensor_anomaly=result.get("sensor_anomaly_out", {}),
            cv_safety=result.get("cv_safety_out", {}),
            permit_intel=result.get("permit_intel_out", {}),
            risk_fusion=result.get("risk_fusion_out", {}),
            rag_compliance=result.get("rag_compliance_out"),
            action_taken=result.get("action_taken", "MONITOR"),
            notifications_sent=result.get("notifications_sent", []),
            report_generated=result.get("report_generated", False)
        )
        
    except Exception as e:
        traceback.print_exc()
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Analysis pipeline failed: {str(e)}"
        )
