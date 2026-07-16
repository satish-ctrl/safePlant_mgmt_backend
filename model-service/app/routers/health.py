import os
from fastapi import APIRouter, status
from app.schemas.responses import HealthResponse

router = APIRouter(tags=["Health"])


@router.get(
    "/health",
    response_model=HealthResponse,
    status_code=status.HTTP_200_OK,
    summary="Health check for model service"
)
def health_check():
    """Returns health status of the model service including model and ChromaDB readiness."""
    model_loaded = os.path.exists(
        os.getenv("LSTM_MODEL_PATH", os.path.join(os.getenv("DATA_DIR", "data"), "lstm_weights.pth"))
    )
    
    chromadb_ready = False
    try:
        from app.core.database.vector_db import VectorDB
        db = VectorDB()
        db.client.heartbeat()
        chromadb_ready = True
    except Exception:
        pass
    
    return HealthResponse(
        status="healthy",
        model_loaded=model_loaded,
        chromadb_ready=chromadb_ready
    )
