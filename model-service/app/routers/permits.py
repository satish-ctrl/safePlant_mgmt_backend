import traceback
from fastapi import APIRouter, HTTPException, status
from app.schemas.requests import PermitCheckRequest
from app.schemas.responses import ErrorResponse

router = APIRouter(prefix="/permits", tags=["Permits"])


@router.post(
    "/check",
    status_code=status.HTTP_200_OK,
    responses={500: {"model": ErrorResponse}},
    summary="Check permits for conflicts"
)
def check_permit_conflicts(request: PermitCheckRequest):
    """Analyzes active work permits for conflicts, expired permits, and SIMOPS violations."""
    try:
        from app.core.agents.permit_agent import PermitAgent
        agent = PermitAgent()
        permits = [p.model_dump() for p in request.permits]
        result = agent.run(permits, current_time=request.current_time, current_gas=request.current_gas)
        return result
    except Exception as e:
        traceback.print_exc()
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Permit conflict check failed: {str(e)}"
        )
