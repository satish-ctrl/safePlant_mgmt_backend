import traceback
from fastapi import APIRouter, HTTPException, status
from app.schemas.requests import SensorAnomalyRequest
from app.schemas.responses import ErrorResponse

router = APIRouter(prefix="/sensor", tags=["Sensor"])


@router.post(
    "/anomaly",
    status_code=status.HTTP_200_OK,
    responses={500: {"model": ErrorResponse}},
    summary="Run sensor anomaly detection"
)
def check_sensor_anomaly(request: SensorAnomalyRequest):
    """Evaluates a sequence of sensor readings through the LSTM Autoencoder
    and returns an anomaly score with severity classification."""
    try:
        from app.core.agents.sensor_agent import SensorAgent
        agent = SensorAgent()
        result = agent.run(request.readings)
        return result
    except Exception as e:
        traceback.print_exc()
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Sensor anomaly detection failed: {str(e)}"
        )
